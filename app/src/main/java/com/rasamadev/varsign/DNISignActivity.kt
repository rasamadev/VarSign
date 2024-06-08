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
import com.rasamadev.varsign.utils.pki.Tool
import de.tsenger.androsmex.data.CANSpecDO
import es.gob.fnmt.dniedroid.gui.PasswordUI
import es.gob.fnmt.dniedroid.help.Loader
import es.gob.jmulticard.jse.provider.DnieLoadParameter
import es.gob.jmulticard.jse.provider.DnieProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.spongycastle.asn1.esf.SignaturePolicyIdentifier
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
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

class DNISignActivity : AppCompatActivity(), ReaderCallback {

    // ELEMENTOS PANTALLA

    private lateinit var tv: TextView
    private lateinit var tvInfo: TextView
    private lateinit var ivDni: ImageView
    private lateinit var btnCancelar: Button

    // ------------------------------------------------------

    private lateinit var _executor: ExecutorService
    private lateinit var _handler: Handler
    private lateinit var _signature: ByteArray
    private lateinit var privateKey: PrivateKey
    private lateinit var chain: Array<X509Certificate>
    private lateinit var rec: Rectangle

    private lateinit var can: String
//    private lateinit var docName: String
    private lateinit var docPath: String
    private lateinit var docsSelected: Array<String>
    private lateinit var signPosition: String
    private var numPageSign: Int = 0

    private lateinit var pdfReader: PdfReader

    companion object {
        private val dnieProv = DnieProvider()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dni_sign)

        tv = findViewById(R.id.tv)
        tvInfo = findViewById(R.id.tvInfo)
        ivDni = findViewById(R.id.ivDni)
        btnCancelar = findViewById(R.id.btnCancelDni)

        can = intent.getStringExtra("CAN") as String
        signPosition = intent.getStringExtra("signPosition") as String
        numPageSign = intent.getIntExtra("numPageSign", 1)
        docPath = intent.getStringExtra("docPath") as String
        docsSelected = intent.getStringArrayExtra("docsSelected") as Array<String>
//        if(!intent.getStringExtra("docName").isNullOrEmpty()){
//            docName = intent.getStringExtra("docName") as String
//        }
//        if(!intent.getStringArrayExtra("docsSelected").isNullOrEmpty()){
//
//        }

        _executor = Executors.newSingleThreadExecutor()
        _handler = Handler(Looper.getMainLooper())

        PasswordUI.setAppContext(this)
        PasswordUI.setPasswordDialog(null) //Diálogo de petición de contraseña por defecto

        btnCancelar.setOnClickListener { v: View? -> onBackPressed() }

        ivDni.setImageResource(R.drawable.dni30_grey_peq)

        Common.EnableReaderMode(this)
    }

    fun updateInfo(info: String?, extra: String?) {
        runOnUiThread {
            if (info != null) {
                tv.text = info
            }
            if (extra != null) {
                tvInfo.text = extra
            }
            ivDni.setImageResource(R.drawable.dni30_grey_peq)
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        runOnUiThread {
//            _resultInfo!!.visibility = View.GONE
            tv.text = "Leyendo datos..."
            tvInfo.text = ""
            ivDni.setImageResource(R.drawable.dni30_peq)
        }
        try{
            //Clase 'Initializer' que nos devuelve directamente el keystore
            //KeyStore keyStore = Loader.init(new String[]{_can}, tag).getKeyStore();

            // Versión DNIeDroid v2.03.109++
            Security.insertProviderAt(dnieProv, 1)
            val initInfo = DnieLoadParameter.getBuilder(arrayOf(can), tag).build()
            val keyStore = KeyStore.getInstance(DnieProvider.KEYSTORE_PROVIDER_NAME)
            keyStore.load(initInfo)
            val certificateBean: Tool.SignatureCertificateBean = Tool.selectCertificate(this, keyStore)

            // Actualizamos la BBDD de CAN de la App
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

            privateKey = keyStore.getKey(certificateBean.getAlias(), null) as PrivateKey
            chain = keyStore.getCertificateChain(certificateBean.getAlias()) as Array<X509Certificate>
            _executor!!.execute {
//                val result = doInBackground(privateKey)
                signDocuments()
                _handler!!.post {
                    //UI Thread work here
//                    updateInfo(
//                        if (result == null) "Firma realizada." else "Error en proceso de firma.",
//                        result ?: Base64.encodeToString(_signature, Base64.DEFAULT)
//                    )

                    val i = Intent(this, MainActivity::class.java)
                    i.putExtra("docsFirmados", "trueDNI")
                    startActivity(i)
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

    private fun doInBackground(privateKey: PrivateKey): String? {
        _signature = try {
            Common.getSignature(privateKey)
        } catch (e: Exception) {
            return e.message
        }
        return null
    }

    /**
     * METODO QUE APLICA LA FIRMA AL DOCUMENTO SELECCIONADO
     */
    private fun signDocuments() {
        try {
            /** CREAMOS UNA 'ExternalSignature' CON LA CLAVE PRIVADA Y LA FUNCION HASH SHA-256 */
            val pks: ExternalSignature = PrivateKeySignature(
                privateKey,
                DigestAlgorithms.SHA256,
                null
            )

            for(docName: String in docsSelected){
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
                    guardarPath("firmado_$docName?${dateFormat.format(calendar.time)}")
                }
            }
        } catch (e: KeyChainException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        } catch (e: DocumentException) {
            e.printStackTrace()
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
                preferences[stringPreferencesKey("paths")] += ",$name"
            }
        }
    }
}