package com.rasamadev.varsign

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.security.KeyChainException
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.security.BouncyCastleDigest
import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalDigest
import com.itextpdf.text.pdf.security.ExternalSignature
import com.itextpdf.text.pdf.security.MakeSignature
import com.itextpdf.text.pdf.security.PrivateKeySignature
import com.rasamadev.varsign.utils.Common
import com.rasamadev.varsign.utils.Utils
import com.rasamadev.varsign.utils.pki.Tool
import de.tsenger.androsmex.data.CANSpecDO
import es.gob.fnmt.dniedroid.gui.PasswordUI
import es.gob.fnmt.dniedroid.help.Loader
import es.gob.jmulticard.jse.provider.DnieLoadParameter
import es.gob.jmulticard.jse.provider.DnieProvider
import es.gob.jmulticard.ui.passwordcallback.CancelledOperationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.spongycastle.asn1.esf.SignaturePolicyIdentifier
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.jvm.Throws

/**
 * CLASE QUE MUESTRA LA INTERFAZ DE FIRMA MEDIANTE DNI ELETRONICO
 * POR NFC
 *
 * UTILIZA EL MIDDLEWARE 'DNIeDroid' JUNTO A ALGUNAS CLASES DE JAVA
 * BRINDADAS POR EL CUERPO NACIONAL DE POLICIA EN UN KIT DE
 * DESARROLLO.
 *
 * ESTA UTILIDAD NOS BRINDA LA CLAVE PRIVADA DEL CERTIFICADO DE
 * FIRMA DE NUESTRO DNI Y REALIZA VARIAS COMPROBACIONES:
 *
 * - Solicitud de contraseña del DNI
 * - Si el CAN utilizado es incorrecto
 * - Si hemos apartado el DNI durante el proceso de firma
 * - Si el usuario cancela la operacion de firmado
 * - Etc
 */
class DNISignActivity : AppCompatActivity(), ReaderCallback {

    // ELEMENTOS PANTALLA

    /** TextView principal */
    private lateinit var tv: TextView

    /** Textview de informacion */
    private lateinit var tvInfo: TextView

    /** Imagen del DNI (en blanco y negro o a color) */
    private lateinit var ivDni: ImageView

    /** Boton cancelar */
    private lateinit var btnCancelar: Button

    // ------------------------------------------------------


    private lateinit var _executor: ExecutorService
    private lateinit var _handler: Handler

    /** Clave privada del certificado incluido en el DNI */
    private lateinit var privateKey: PrivateKey

    /** Chain del certificado incluido en el DNI */
    private lateinit var chain: Array<X509Certificate>

    /** Coordenadas del rectangulo de la firma */
    private lateinit var rec: Rectangle

    /** Alias del certificado incluido en el DNI */
    private lateinit var aliasCert: String

    /** Codigo CAN utilizado para la firma */
    private lateinit var can: String

    /** Ruta del documento o carpeta */
    private lateinit var docPath: String

    /** Lista de documentos seleccionados para firmar */
    private lateinit var docsSelected: Array<String>

    /** String que guarda la posicion de la firma */
    private lateinit var signPosition: String

    /** Numero de la pagina donde se insertará la firma */
    private var numPageSign: Int = 0

    /** Lector del documento seleccionado */
    private lateinit var pdfReader: PdfReader

    /**
     * Variable que guardara el nombre del documento 'erroneo'
     * que borraremos si ha habido un problema en el proceso de firmado
     */
    private var del: String = ""

    companion object {
        private val dnieProv = DnieProvider()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dni_sign)
        initView()

        /** RECOGEMOS LOS EXTRAS DE LA ACTIVITY */
        can = intent.getStringExtra("CAN") as String
        signPosition = intent.getStringExtra("signPosition") as String
        numPageSign = intent.getIntExtra("numPageSign", 1)
        docPath = intent.getStringExtra("docPath") as String
        docsSelected = intent.getStringArrayExtra("docsSelected") as Array<String>

        _executor = Executors.newSingleThreadExecutor()
        _handler = Handler(Looper.getMainLooper())

        /** INICIALIZAMOS EL DIALOGO DE SOLICITUD DE CONTRASEÑA DEL DNI */
        PasswordUI.setAppContext(this)
        PasswordUI.setPasswordDialog(null) //Diálogo de petición de contraseña por defecto

        /** ESTABLECEMOS LA IMAGEN DEL DNI EN BLANCO Y NEGRO */
        ivDni.setImageResource(R.drawable.dni30_grey_peq)

        /** INICIALIZAMOS LA LECTURA POR NFC */
        Common.EnableReaderMode(this)
    }

    /**
     * METODO QUE INICIALIZA LOS ELEMENTOS DE LA PANTALLA
     * Y LOS LISTENER DE LOS BOTONES
     */
    private fun initView() {
        tv = findViewById(R.id.tv)
        tvInfo = findViewById(R.id.tvInfo)
        ivDni = findViewById(R.id.ivDni)
        btnCancelar = findViewById(R.id.btnCancelDni)

        btnCancelar.setOnClickListener { v: View? -> onBackPressed() }
    }

    /**
     * METODO QUE ACTUALIZA LOS TEXTVIEW DE INFORMACION
     * Y MODIFICA LA IMAGEN DEL DNI
     */
    fun updateInfo(info: String?, extra: String?) {
        runOnUiThread {
            if (info != null) {
                tv.text = info
            }
            if (extra != null) {
                tvInfo.text = extra
            }
            if(info != "Leyendo datos, no retire el DNI..."){
                ivDni.setImageResource(R.drawable.dni30_grey_peq)
            }
        }
    }

    /**
     * METODO QUE SE EJECUTA CUANDO EL LECTOR DE NFC DETECTA
     * NUESTRO DNI
     */
    override fun onTagDiscovered(tag: Tag) {
        runOnUiThread {
            tv.text = "Leyendo datos, no retire el DNI..."
            tvInfo.text = ""
            ivDni.setImageResource(R.drawable.dni30_peq)
        }

        try{
            /** Versión DNIeDroid v2.03.109++ */
            Security.insertProviderAt(dnieProv, 1)
            val initInfo = DnieLoadParameter.getBuilder(arrayOf(can), tag).build()
            val keyStore = KeyStore.getInstance(DnieProvider.KEYSTORE_PROVIDER_NAME)
            keyStore.load(initInfo)
            val certificateBean: Tool.SignatureCertificateBean = Tool.selectCertificate(this, keyStore)

            /** Actualizamos la BBDD de CAN de la App */
            val canSpecDO: CANSpecDO
            canSpecDO =
                if (initInfo.keyStoreType.equals(
                        DnieProvider.KEYSTORE_TYPE_AVAILABLE[1],
                        ignoreCase = true
                    )
                ) {
                    CANSpecDO(
                        can,
                        Tool.getCN(certificateBean.certificate),
                        Tool.getNIF(certificateBean.certificate)
                    )
                } else {
                    CANSpecDO(can, Tool.getCN(certificateBean.certificate), "")
                }
            Loader.saveCan2DB(canSpecDO, this)

            /** RECOGEMOS EL ALIAS, CLAVE PRIVADA Y EL CHAIN DEL CERTIFICADO DE FIRMA */
            aliasCert = canSpecDO.userName
            privateKey = keyStore.getKey(certificateBean.getAlias(), null) as PrivateKey
            chain = keyStore.getCertificateChain(certificateBean.getAlias()) as Array<X509Certificate>

            _executor!!.execute {
                val result = doInBackground()
                _handler!!.post {
                    if(result == null){
                        val i = Intent(this, MainActivity::class.java)
                        i.putExtra("docsFirmados", "trueDNI")
                        startActivity(i)
                    }
                    else{
                        updateInfo("Error en proceso de firma.", result)
                    }
                }
            }
        }
        catch (gsioe: GeneralSecurityException) {
            gsioe.printStackTrace()
            updateInfo("Error leyendo el DNIEe.", gsioe.message)
        }
        catch (gsioe: IOException) {
            gsioe.printStackTrace()
            updateInfo("Error leyendo el DNIEe.", gsioe.message)
        }
    }

    /**
     * METODO DE FIRMA DE UNO O VARIOS DOCUMENTOS
     */
    @Throws(Exception::class)
    private fun signDocuments() {
        /** CREAMOS UNA 'ExternalSignature' CON LA CLAVE PRIVADA Y LA FUNCION HASH SHA-256 */
        val pks: ExternalSignature = PrivateKeySignature(
            privateKey,
            DigestAlgorithms.SHA256,
            null
        )

        for((index, docName: String) in docsSelected.withIndex()){
            del = docName
            /** ESPECIFICAMOS DONDE IRA GUARDADO EL DOCUMENTO FIRMADO (Carpeta 'VarSign') */
            val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName")
            val fos = FileOutputStream(file)

            /** APLICAMOS LA FIRMA EN LA POSICION Y PAGINA INDICADAS ANTERIORMENTE */
            val uri = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(docPath), docName))
            if(intent.getByteArrayExtra("passwordDoc") != null){
                pdfReader = PdfReader(contentResolver.openInputStream(uri), intent.getByteArrayExtra("passwordDoc"))
            }
            else{
                pdfReader = PdfReader(contentResolver.openInputStream(uri))
            }
            val stamper = PdfStamper.createSignature(pdfReader, fos, '\u0000')

            val rectangle: Rectangle = pdfReader.getPageSizeWithRotation(numPageSign)
            if (rectangle.height >= rectangle.width){
                when (signPosition) {
                    "arrIzq" -> rec = Rectangle(20f, 800f, 130f, 830f)
                    "arrCen" -> rec = Rectangle(243f, 800f, 353f, 830f)
                    "arrDer" -> rec = Rectangle(466f, 800f, 576f, 830f)
                    "abaIzq" -> rec = Rectangle(20f, 20f, 130f, 50f)
                    "abaCen" -> rec = Rectangle(243f, 20f, 353f, 50f)
                    "abaDer" -> rec = Rectangle(466f, 20f, 576f, 50f)
                }
            }
            else{
                when (signPosition) {
                    "arrIzq" -> rec = Rectangle(20f, 570f, 130f, 600f)
                    "arrCen" -> rec = Rectangle(360f, 570f, 470f, 600f)
                    "arrDer" -> rec = Rectangle(690f, 570f, 800f, 600f)
                    "abaIzq" -> rec = Rectangle(20f, 20f, 130f, 50f)
                    "abaCen" -> rec = Rectangle(360f, 20f, 470f, 50f)
                    "abaDer" -> rec = Rectangle(690f, 20f, 800f, 50f)
                }
            }

            val appearance = stamper.signatureAppearance
            appearance.setVisibleSignature(rec, numPageSign, null)
            appearance.imageScale = -1f

            /**
             * UTILIZAMOS UN DIGEST PROPORCIONADO POR BouncyCastle QUE UTILIZA SUS METODOS
             * PARA CALCULAR EL HASH DEL DOCUMENTO
             */
            val digest: ExternalDigest = BouncyCastleDigest()

            /** FIRMAMOS EL DOCUMENTO EN FORMATO 'CAdES' */
            MakeSignature.signDetached(
                appearance,
                digest,
                pks,
                chain as Array<X509Certificate>,
                null,
                null,
                null,
                0,
                MakeSignature.CryptoStandard.CADES,
                null as SignaturePolicyIdentifier?
            )

            /**
             * GUARDAMOS EN EL DATASTORE DE LA APLICACION EL NOMBRE DEL DOCUMENTO
             * FIRMADO JUNTO CON LA FECHA Y HORA DE LA FIRMA, AMBOS DATOS SEPARADOS
             * POR UN '?' (PARA DESPUES SPLITEARLO MEDIANTE ESE SEPARADOR)
             */
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            lifecycleScope.launch(Dispatchers.IO){
                guardarPath("firmado_$docName?${dateFormat.format(calendar.time)}<DNI electronico>$aliasCert")
            }

            if(docsSelected.size > 1){
                updateInfo("Leyendo datos, no retire el DNI...", "Firmado documento ${index+1} de ${docsSelected.size}...")
            }
        }
    }

    /**
     * METODO QUE GUARDA EN DATASTORE LA INFORMACION DEL DOCUMENTO FIRMADO
     *
     * SI NO ES EL PRIMER DOCUMENTO FIRMADO, GUARDA LA INFORMACION CON UNA
     * COMA DELANTE (PARA QUE SEA SPLITEADA DESPUES), EN CASO CONTRARIO, SIN ELLA
     */
    private suspend fun guardarPath(name: String) {
        dataStore.edit{ preferences ->
            if(preferences[stringPreferencesKey("paths")].isNullOrEmpty()){
                preferences[stringPreferencesKey("paths")] = name
            }
            else{
                preferences[stringPreferencesKey("paths")] += "|$name"
            }
        }
    }

    /**
     * METODO QUE REALIZA EN SEGUNDO PLANO LA FIRMA DEL DOCUMENTO
     */
    private fun doInBackground(): String? {
        try {
            signDocuments()
        }
        catch (e: Exception) {
            /**
             * Si ha ocurrido un problema al intentar realizar la firma
             * se borrara el archivo generado. SI YA EXISTIA UN DOCUMENTO
             * FIRMADO, LO BORRARA IGUALMENTE
             */
            val f = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$del")
            if(f.length() <= 0){
                f.delete()
            }
            return e.message
        }
        return null
    }
}