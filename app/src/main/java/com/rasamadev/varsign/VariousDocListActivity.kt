package com.rasamadev.varsign

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.security.KeyChainException
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.spongycastle.asn1.esf.SignaturePolicyIdentifier
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * CLASE QUE CONTROLA EL PROCESO DE FIRMADO DE VARIOS DOCUMENTOS
 * MOSTRANDO UNA PANTALLA DONDE SELECCIONAR LOS DOCUMENTOS QUE
 * CONTIENE LA CARPETA QUE HAYAMOS SELECCIONADO Y ELIGIENDO CUALES
 * QUEREMOS FIRMAR
 */
class VariousDocListActivity : AppCompatActivity(), View.OnClickListener {

    // ELEMENTOS PANTALLA

    /** toolbar "Top Bar" */
    private lateinit var toolBarVariousDocs: MaterialToolbar

    /** Lista de documentos a seleccionar para su posterior firma */
    private lateinit var cbDocsContainer: LinearLayout

    /** Boton "Cancelar" */
    private lateinit var btnCancelar: Button

    /** Boton "Aceptar" */
    private lateinit var btnAceptar: Button

    // ------------------------------------------------------

    /** Coordenadas del rectangulo de la firma */
    private lateinit var rec: Rectangle

    /** Lista de documentos seleccionados para firmar */
    private lateinit var docsSelected: MutableList<String>

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    /** Nombre del directorio seleccionado */
    private lateinit var directorySelected: String

    /** String que guardara la posicion de la firma */
    private lateinit var signPosition: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_variousdoclist)
        initView()

        /** RECOGEMOS LOS EXTRAS DE LA ACTIVITY */
        val pdfFileNames = intent.getStringArrayListExtra("pdfFileNames")
        directorySelected = intent.getStringExtra("directorySelected") as String
        val edf = intent.getBooleanExtra("encryptedDocsFounded", true)

        /**
         * POR CADA DOCUMENTO QUE CONTENGA EL DIRECTORIO SELECCIONADO
         * CARGAMOS UN CHECKBOX CON EL NOMBRE DEL DOCUMENTO
         */
        pdfFileNames?.forEach { fileName ->
            val checkBox = CheckBox(this).apply {
                text = fileName
                textSize = 26f
                isChecked = true
            }
            cbDocsContainer.addView(checkBox)
        }

        /** SI EL DIRECTORIO SELECCIONADO CONTIENE ARCHIVOS PROTEGIDOS CON CONTRASEÑA */
        if(edf){
            Utils.mostrarError(this, "Se han eliminado de la lista uno o varios documentos protegidos con contraseña.\n\nSi desea firmar esos documentos, por favor, hagalo mediante la opcion de firma de 'Un documento'.")
        }
    }

    /**
     * METODO QUE INICIALIZA LOS ELEMENTOS DE LA PANTALLA, LOS
     * LISTENER DE LOS BOTONES Y ESTABLECE LA TOOLBAR
     */
    private fun initView() {
        toolBarVariousDocs = findViewById(R.id.toolBarVariousDocs)
        cbDocsContainer = findViewById<LinearLayout>(R.id.cbDocsContainer)
        btnCancelar = findViewById<Button>(R.id.btnCancelar)
        btnAceptar = findViewById<Button>(R.id.btnAceptar)

        btnCancelar.setOnClickListener(this)
        btnAceptar.setOnClickListener(this)

        setSupportActionBar(toolBarVariousDocs)
    }

    /**
     * METODO DE CONFIGURACION DE LOS BOTONES DE LA PANTALLA Y SUS ACCIONES
     */
    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btnCancelar -> {
                /** VOLVEMOS AL MENU DE INICIO */
                onBackPressed()
            }
            R.id.btnAceptar -> {
                /** RECOGEMOS EN UNA mutableList LOS DOCUMENTOS SELECCIONADOS */
                docsSelected = mutableListOf<String>()
                for (i in 0 until cbDocsContainer.childCount) {
                    val child = cbDocsContainer.getChildAt(i)
                    if (child is CheckBox && child.isChecked) {
                        docsSelected.add(child.text.toString())
                    }
                }

                /**
                 * COMPROBAMOS LOS ARCHIVOS 'SIMILARES' EXISTENTES EN LA
                 * CARPETA 'VarSign'
                 */
                var docsFounded: String = ""
                for (i in 0 until docsSelected.size) {
                    val f = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_${docsSelected[i]}")
                    if(f.exists()){
                        docsFounded += "-${docsSelected[i]}\n"
                    }
                }

                /** SI NO SE HA SELECCIONADO NINGUN DOCUMENTO */
                if (docsSelected.isEmpty()) {
                    Utils.mostrarError(this, "¡Selecciona al menos un documento!")
                }
                /**
                 * SI SE HAN ENCONTRADO ARCHIVOS SIMILARES
                 * (SE MUESTRA MENSAJE DE ADVERTENCIA)
                 */
                else if(docsFounded != ""){
                    dialogDocumentsAlreadyExists(docsFounded)
                }
                else{
                    dialogSignPosition()
                }

            }
        }
    }

    /**
     * METODO QUE APLICA LA FIRMA A LOS DOCUMENTOS SELECCIONADOS
     */
    private fun signVariousDocuments() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg params: Void?): Void? {
                var privateKey: PrivateKey? = null
                try {
                    /** RECOGEMOS LA CLAVE PRIVADA Y EL CHAIN DEL CERTIFICADO SELECCIONADO */
                    privateKey = KeyChain.getPrivateKey(applicationContext, aliasCert)
                    val keyFactory = KeyFactory.getInstance(privateKey!!.algorithm, "AndroidKeyStore")
                    val chain: Array<X509Certificate>? = KeyChain.getCertificateChain(applicationContext, aliasCert)

                    /** CREAMOS UNA 'ExternalSignature' CON LA CLAVE PRIVADA Y LA FUNCION HASH SHA-256 */
                    val pks: ExternalSignature = PrivateKeySignature(
                        privateKey,
                        DigestAlgorithms.SHA256,
                        null
                    )

                    /** APLICAMOS LA FIRMA A CADA DOCUMENTO SELECCIONADO */
                    for(doc: String in docsSelected){
                        /**
                         * CARGAMOS EL ARCHIVO Y ESPECIFICAMOS DONDE IRA
                         * GUARDADO EL DOCUMENTO FIRMADO (Carpeta 'VarSign')
                         */
                        val file = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(directorySelected), doc))
                        val filesigned = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$doc")
                        val fos = FileOutputStream(filesigned)

                        /**
                         * CARGAMOS EL DOCUMENTO A PARTIR DEL ARCHIVO Y
                         * LE APLICAMOS LA FIRMA VACIA
                         */
                        val reader = PdfReader(contentResolver.openInputStream(file!!))
                        val stamper = PdfStamper.createSignature(reader, fos, '\u0000')

                        /**
                         * CALCULAMOS LA ORIENTACION DE LA PAGINA A FIRMAR
                         * PARA ESTABLECER LAS COORDENADAS DE LA POSICION
                         * DE LA FIRMA SELECCIONADA
                         */
                        val rectangle: Rectangle = reader.getPageSizeWithRotation(1)
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

                        /** APLICAMOS LA FIRMA EN LA POSICION Y PAGINA INDICADAS ANTERIORMENTE */
                        val appearance = stamper.signatureAppearance
                        appearance.setVisibleSignature(rec, 1, "sig")
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
                            guardarPath("firmado_$doc?${dateFormat.format(calendar.time)}")
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
                return null
            }
        }.execute()
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

    // ---------------------------------------- DIALOG´S ---------------------------------------- //

    /**
     * ALERTDIALOG DE ADVERTENCIA QUE SE MUESTRA CUANDO SE ENCUENTRAN
     * UNO O MAS ARCHIVOS YA FIRMADOS CON EL MISMO NOMBRE EN LA CARPETA 'VarSign'
     */
    private fun dialogDocumentsAlreadyExists(docsFounded: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Se ha encontrado en la carpeta 'VarSign' una copia con el mismo nombre de los siguientes documentos seleccionados:\n\n$docsFounded\nSi continua, seran sobreescritos por los documentos actuales, ¿Esta seguro?")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            dialogSignPosition()
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    /**
     * ALERTDIALOG QUE MUESTRA UN MENU DE SEIS OPCIONES PARA ESTABLECER
     * LA POSICION DE LA FIRMA EN LOS DOCUMENTOS
     */
    private fun dialogSignPosition() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_variousdoc_signposition, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val arrIzq = dialogView.findViewById<RadioButton>(R.id.rbDiaArrIzq)
        val arrCen = dialogView.findViewById<RadioButton>(R.id.rbDiaArrCen)
        val arrDer = dialogView.findViewById<RadioButton>(R.id.rbDiaArrDer)
        val abaIzq = dialogView.findViewById<RadioButton>(R.id.rbDiaAbaIzq)
        val abaCen = dialogView.findViewById<RadioButton>(R.id.rbDiaAbaCen)
        val abaDer = dialogView.findViewById<RadioButton>(R.id.rbDiaAbaDer)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Indique la posicion de la firma")
        builder.setView(dialogView)

        builder.setPositiveButton("Aceptar") { dialog, which ->
            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadioButton = dialogView.findViewById<RadioButton>(selectedId)
            when (selectedRadioButton.id) {
                arrIzq.id -> signPosition = "arrIzq"
                arrCen.id -> signPosition = "arrCen"
                arrDer.id -> signPosition = "arrDer"
                abaIzq.id -> signPosition = "abaIzq"
                abaCen.id -> signPosition = "abaCen"
                abaDer.id -> signPosition = "abaDer"
            }
            dialogSignMethods()
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    // TODO EDITAR Y RELLENAR DESCRIPCION SEGUN SE IMPLEMENTE O NO FIRMA POR DNI (varios)
    private fun dialogSignMethods() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccione un metodo de firmado")

        val options = arrayOf(
            "Certificado digital",
            "DNI electronico (NFC)"
        )

        builder.setItems(options) { dialog, which ->
            when (which) {
                // Certificado digital
                0 -> {
                    KeyChain.choosePrivateKeyAlias(
                        this,
                        object: KeyChainAliasCallback {
                            override fun alias(alias: String?) {
                                if(alias != null){
                                    aliasCert = alias
                                    /** MOSTRAMOS EL MENSAJE DE CONFIRMACION EN EL HILO DE UI */
                                    runOnUiThread{
                                        dialogConfirmSign()
                                    }
                                }
                            }
                        },
                        null,
                        null,
                        null,
                        -1,
                        null
                    )
                }
                // TODO DNI electronico (NFC)
                1 -> {

                }
            }
            dialog.dismiss()
        }

        builder.setPositiveButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    /**
     * ALERTDIALOG DE CONFIRMACION PARA FIRMAR LOS DOCUMENTOS SELECCIONADOS.
     * ESTOS DOCUMENTOS SE MUESTRAN EN UNA LISTA
     */
    private fun dialogConfirmSign() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("SE VAN A FIRMAR LOS SIGUIENTES DOCUMENTOS:")

        // TODO PERFECCIONAR STRING DE LISTA DE DOCUMENTOS??
        /** GENERAMOS LA LISTA DE LOS DOCUMENTOS SELECCIONADOS */
        var list: String = ""
        for (doc: String in docsSelected) {
            list += "\n\n- $doc"
        }
        list += "\n\n\n\nSi esta de acuerdo, pulse en el boton 'Aceptar'.\n\nNOTA: La firma se aplicará en la pagina 1 de cada documento."
        builder.setMessage(list)

        /** FIRMAMOS LOS DOCUMENTOS SELECCIONADOS Y VOLVEMOS AL MENU DE INICIO */
        builder.apply {
            setPositiveButton("Aceptar") { dialog, which ->
                signVariousDocuments()

                val i = Intent(applicationContext, MainActivity::class.java)
                i.putExtra("docsFirmados", "true")
                startActivity(i)
            }
            setNegativeButton("Cancelar") { dialog, which ->
                dialog.dismiss()
            }
        }

        val dialog = builder.create()
        dialog.show()
    }
}