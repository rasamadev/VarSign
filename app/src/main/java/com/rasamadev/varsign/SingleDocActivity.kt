package com.rasamadev.varsign

//import com.itextpdf.kernel.geom.Rectangle
//import com.itextpdf.kernel.pdf.PdfDocument
//import com.itextpdf.kernel.pdf.PdfReader
//import com.itextpdf.kernel.pdf.PdfWriter
//import com.itextpdf.kernel.pdf.StampingProperties
//import com.itextpdf.signatures.BouncyCastleDigest
//import com.itextpdf.signatures.DigestAlgorithms
//import com.itextpdf.signatures.IExternalDigest
//import com.itextpdf.signatures.IExternalSignature
//import com.itextpdf.signatures.PdfSigner
//import com.itextpdf.signatures.PrivateKeySignature

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.security.KeyChainException
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Rectangle
import com.itextpdf.text.exceptions.InvalidPdfException
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.security.BouncyCastleDigest
import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalDigest
import com.itextpdf.text.pdf.security.ExternalSignature
import com.itextpdf.text.pdf.security.MakeSignature
import com.itextpdf.text.pdf.security.MakeSignature.CryptoStandard
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

//import kotlin.coroutines.jvm.internal.CompletedContinuation.context

class SingleDocActivity : AppCompatActivity(), View.OnClickListener {

    // ELEMENTOS PANTALLA

    /** Boton "Instalar certificado" */
    private lateinit var btnSelDoc: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnFirmar: Button

    /** Campo de nombre del documento seleccionado */
    private lateinit var etNombreArchivo: EditText

    /** Campo de numero de pagina de la firma */
    private lateinit var etNumPagDoc: EditText

    /** Texto que indica el numero de paginas del documento */
    private lateinit var txtNumPagsDoc: TextView

    /** Texto que, en base a si se ha seleccionado o no un documento:
     * - No se ha seleccionado ningun documento.
     * - ¿En que pagina quieres aplicar la firma?
     */
    private lateinit var txtSignPage: TextView

    /** RadioGroup de opciones de lugar de la firma */
    private lateinit var rgLugarFirma: RadioGroup

    /** RadioButtons del RadioGroup "rbLugarFirma */
    private lateinit var rbArrIzq: RadioButton
    private lateinit var rbArrCen: RadioButton
    private lateinit var rbArrDer: RadioButton
    private lateinit var rbAbaIzq: RadioButton
    private lateinit var rbAbaCen: RadioButton
    private lateinit var rbAbaDer: RadioButton

    // ------------------------------------------------------

    /** Coordenadas del rectangulo de la firma */
    private lateinit var rec: Rectangle

    /** Lugar de firma seleccionado */
    private var idLugarFirma: Int = -1

    /** Documento seleccionado */
    private var docSeleccionado: Uri? = null

    /** Nombre del documento seleccionado */
    private lateinit var docName: String

    /** Ruta del documento seleccionado */
    private lateinit var docPath: String

    /** Numero de paginas del documento seleccionado */
    private var docPages: Int = 0

    /** Numero de la pagina donde se insertará la firma */
    private var numPageSign: Int = 0

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    /** Lector del documento seleccionado */
    private lateinit var pdfReader: PdfReader

    /** Posible contraseña del documento a firmar */
    private lateinit var passwordDoc: ByteArray

//    val sharedPreferences: SharedPreferences = getSharedPreferences("docs", Context.MODE_PRIVATE)

    companion object {
        const val PICK_PDF_REQUEST_CODE = 123
    }

    val getPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uri ->
            try{
                // COMPROBACION PDF CON CONTRASEÑA
                // TODO SI LO QUITO, DA EXCEPCION EN EL CATCH DE LA LINEA 195, SI LO DEJO, SELECCIONO UN DOCUMENTO FIRMADO, CANCELO E INTENTO FIRMAR
                docName = getFileName(uri) as String
                if(Utils.isPasswordProtected(contentResolver.openInputStream(uri))){
                    dialogRequestPassword(uri)
                }
                else{
                    continueDocSelected(uri)
                }

//                // GUARDAMOS EL NOMBRE Y RUTA DEL DOCUMENTO SELECCIONADO
//                docName = getFileName(uri) as String
//                docPath = getFilePath(uri.path) as String
//
//                // RECOGEMOS EL NUMERO DE PAGINAS DEL DOCUMENTO SELECCIONADO
//                val inputStream = applicationContext.contentResolver.openInputStream(uri)
//                val pass = "qwerty1234.".toByteArray()
//                val pdfReader = PdfReader(inputStream, pass)
//                docPages = pdfReader.numberOfPages
//
//                // SI EL DOCUMENTO SELECCIONADO ESTA CORRECTO, LO GUARDAMOS
//                docSeleccionado = uri
//
//                // ESTABLECEMOS EL NOMBRE EN EL EDITTEXT 'etNombreArchivo'
//                etNombreArchivo.setText(docName)
//
//                // MOSTRAMOS LOS TEXTOS Y EDITTEXT PARA INDICAR EN QUE PAGINA
//                // SE QUIERE APLICAR LA FIRMA
//                txtSignPage.text = "¿En que pagina quieres aplicar la firma?"
//                etNumPagDoc.visibility = View.VISIBLE
//                etNumPagDoc.setText("1")
//
//                // APLICARIA EL MAXLENGTH SEGUN LAS PAGS DEL DOC PERO NO SE PUEDE
//                txtNumPagsDoc.text = "Numero de paginas del documento: $docPages"
            }
            catch (e: InvalidPdfException) {
                // RESTABLECEMOS LOS ELEMENTOS DE LA PAGINA A SU ESTADO PRIMARIO
                // Y docSeleccionado = null PARA QUE, CUANDO PULSEMOS EN EL BOTON
                // "FIRMAR DOCUMENTO", NO HAYA DOCUMENTO SELECCIONADO
                docSeleccionado = null
                clearElements()
//                etNombreArchivo.setText("")
//                txtSignPage.text = "No se ha seleccionado un documento"
//                etNumPagDoc.visibility = View.INVISIBLE
//                txtNumPagsDoc.text = ""

                Utils.mostrarError(this, "No se pudo abrir '$docName' debido a que no es un tipo de archivo admitido o esta dañado.\n\nPor favor, intentelo de nuevo con otro documento.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_singledoc)
        initView()
    }

    private fun initView() {
        btnSelDoc = findViewById<Button>(R.id.btnSelDoc)
        btnFirmar = findViewById<Button>(R.id.btnFirmar)
        etNombreArchivo = findViewById<EditText>(R.id.etNombreArchivo)
        txtSignPage = findViewById<TextView>(R.id.txtSignPage)
        etNumPagDoc = findViewById<EditText>(R.id.etNumPagDoc)
        txtNumPagsDoc = findViewById<TextView>(R.id.txtNumPagsDoc)
        rgLugarFirma = findViewById<RadioGroup>(R.id.rgLugarFirma)
        rbArrIzq = findViewById<RadioButton>(R.id.rbArrIzq)
        rbArrCen= findViewById<RadioButton>(R.id.rbArrCen)
        rbArrDer = findViewById<RadioButton>(R.id.rbArrDer)
        rbAbaIzq = findViewById<RadioButton>(R.id.rbAbaIzq)
        rbAbaCen = findViewById<RadioButton>(R.id.rbAbaCen)
        rbAbaDer = findViewById<RadioButton>(R.id.rbAbaDer)

        btnSelDoc.setOnClickListener(this)
        btnFirmar.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btnSelDoc -> {
                // ABRIR EXPLORADOR DE ARCHIVOS PARA SELECCIONAR PDF
                getPdf.launch("application/pdf")

//                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//                intent.addCategory(Intent.CATEGORY_OPENABLE)
//                intent.type = "application/pdf"
//                startActivityForResult(intent, PICK_PDF_REQUEST_CODE)
            }
            R.id.btnFirmar -> {
                // SI NO SE HA SELECCIONADO NINGUN DOCUMENTO
                if(docSeleccionado == null){
                    Utils.mostrarError(this, "¡No has seleccionado un documento!")
                }
                // SI EL NUMERO DE PAGINA NO SE HA INDICADO
                // O ES MENOR QUE 1
                // O ES MAYOR QUE EL NUMERO DE PAGINAS DEL DOCUMENTO
                else if(etNumPagDoc.text.toString() == "" || Integer.parseInt(etNumPagDoc.text.toString()) < 1 || Integer.parseInt(etNumPagDoc.text.toString()) > docPages){
                    Utils.mostrarError(this, "Por favor, establece un numero de pagina correcto.")
                }
                else if(File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName").exists()){
                    dialogDocumentAlreadyExists()
                }
                else{
                    numPageSign = Integer.parseInt(etNumPagDoc.text.toString())
                    continueSign()
                }
            }
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == RESULT_OK) {
//            val uri: Uri? = data!!.data
//            docSeleccionado = uri
//            docName = getFileName(uri as Uri) as String
//            docPath = getFilePath(uri.path) as String
//            etNombreArchivo.setText(docName)
//        }
//    }

    private fun clearElements() {
        etNombreArchivo.setText("")
        txtSignPage.text = "No se ha seleccionado un documento"
        etNumPagDoc.visibility = View.INVISIBLE
        txtNumPagsDoc.text = ""
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != -1) {
                fileName = fileName?.substring(cut!! + 1)
            }
        }
        return fileName
    }

    private fun getFilePath(path: String?): String? {
        if (path != null) {
            val lastSlashIndex = path.lastIndexOf('/')
            if (lastSlashIndex != -1) {
                return path.substring(0, lastSlashIndex + 1)
            }
        }
        return null
    }

    private fun continueDocSelected(uri: Uri, passwordExists: Boolean = false) {
//        try{
            // GUARDAMOS EL NOMBRE Y RUTA DEL DOCUMENTO SELECCIONADO
            docName = getFileName(uri) as String
            docPath = getFilePath(uri.path) as String

            docSeleccionado = uri

            if(passwordExists){
//                val pass = passwordDoc.toByteArray()
                pdfReader = PdfReader(contentResolver.openInputStream(uri), passwordDoc)
            }
            else{
                pdfReader = PdfReader(contentResolver.openInputStream(uri))
            }

            // RECOGEMOS EL NUMERO DE PAGINAS DEL DOCUMENTO SELECCIONADO
            docPages = pdfReader.numberOfPages

            // ESTABLECEMOS EL NOMBRE EN EL EDITTEXT 'etNombreArchivo'
            etNombreArchivo.setText(docName)

            // MOSTRAMOS LOS TEXTOS Y EDITTEXT PARA INDICAR EN QUE PAGINA
            // SE QUIERE APLICAR LA FIRMA
            txtSignPage.text = "¿En que pagina quieres aplicar la firma?"
            etNumPagDoc.visibility = View.VISIBLE
            etNumPagDoc.setText("1")

            // APLICARIA EL MAXLENGTH SEGUN LAS PAGS DEL DOC PERO NO SE PUEDE
            txtNumPagsDoc.text = "Numero de paginas del documento: $docPages"
//        }
//        catch (e: InvalidPdfException) {
//            Utils.mostrarError(this, "No se pudo abrir '$docName' debido a que no es un tipo de archivo admitido o esta dañado.\n\nPor favor, intentelo de nuevo con otro documento.")
//        }
    }

    private fun continueSign() {
        idLugarFirma = rgLugarFirma.checkedRadioButtonId
        val rectangle: Rectangle = pdfReader.getPageSizeWithRotation(numPageSign)
        if (rectangle.height >= rectangle.width){
            when (idLugarFirma) {
                rbArrIzq.id -> rec = Rectangle(20f, 800f, 130f, 830f)
                rbArrCen.id -> rec = Rectangle(243f, 800f, 353f, 830f)
                rbArrDer.id -> rec = Rectangle(466f, 800f, 576f, 830f)
                rbAbaIzq.id -> rec = Rectangle(20f, 20f, 130f, 50f)
                rbAbaCen.id -> rec = Rectangle(243f, 20f, 353f, 50f)
                rbAbaDer.id -> rec = Rectangle(466f, 20f, 576f, 50f)
            }
        }
        else{
            when (idLugarFirma) {
                rbArrIzq.id -> rec = Rectangle(20f, 570f, 130f, 600f)
                rbArrCen.id -> rec = Rectangle(360f, 570f, 470f, 600f)
                rbArrDer.id -> rec = Rectangle(690f, 570f, 800f, 600f)
                rbAbaIzq.id -> rec = Rectangle(20f, 20f, 130f, 50f)
                rbAbaCen.id -> rec = Rectangle(360f, 20f, 470f, 50f)
                rbAbaDer.id -> rec = Rectangle(690f, 20f, 800f, 50f)
            }
        }

        dialogSignMethods()
    }

    private fun signOneDocument() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg params: Void?): Void? {
                var privateKey: PrivateKey? = null
                try {
                    privateKey = KeyChain.getPrivateKey(applicationContext, aliasCert)
                    val keyFactory = KeyFactory.getInstance(privateKey!!.algorithm, "AndroidKeyStore")
                    val chain: Array<X509Certificate>? = KeyChain.getCertificateChain(applicationContext, aliasCert)

                    val provider = BouncyCastleProvider()
                    Security.addProvider(provider)

                    val pks: ExternalSignature = CustomPrivateKeySignature(
                        privateKey,
                        DigestAlgorithms.SHA256,
                        provider.getName()
                    )

                    val tmp = File.createTempFile("eid", ".pdf", cacheDir)
                    // TODO EXCEPCION PARA MAS DE UNA FIRMA
                    // TODO (HECHO?) -- PENSAR: LO DEJO EN LA CARPETA DOCUMENTOS??
//                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "firmado_$docName")
                    val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName")
//                    val file = File("/sdcard/VarSign/", "firmado_$docName")
                    val fos = FileOutputStream(file)

                    // Creating the reader and the stamper
//                    val reader = PdfReader(contentResolver.openInputStream(docSeleccionado!!))
//                    val stamper = PdfStamper.createSignature(reader, fos, '\u0000')
                    val stamper = PdfStamper.createSignature(pdfReader, fos, '\u0000')

                    // Creating the appearance
                    val appearance = stamper.signatureAppearance
                    appearance.setVisibleSignature(rec, Integer.parseInt(etNumPagDoc.text.toString()), "sig")
                    appearance.imageScale = -1f

                    // Creating the signature
                    val digest: ExternalDigest = BouncyCastleDigest()

                    // Sign the document
                    MakeSignature.signDetached(
                        appearance,
                        digest,
                        pks,
                        chain as Array<X509Certificate>,
                        null,
                        null,
                        null,
                        0,
                        CryptoStandard.CADES,
                        null as SignaturePolicyIdentifier?
                    )

//                    sign(
//                        docSeleccionado,
//                        fos,
//                        chain,
//                        pks,
////                        DigestAlgorithms.SHA256,
////                        provider.getName(),
//                        CryptoStandard.CADES,
//                        Integer.parseInt(etNumPagDoc.text.toString()),
//                        rec
//                    )
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

//    @Throws(GeneralSecurityException::class, IOException::class, DocumentException::class)
//    fun sign(
//        uri: Uri?,
//        os: FileOutputStream?,
//        chain: Array<X509Certificate>?,
//        pk: ExternalSignature,
////        digestAlgorithm: String?,
////        provider: String?,
//        subfilter: CryptoStandard,
//        signPage: Int,
//        rec: Rectangle
//    ){
//        // Creating the reader and the stamper
//        val reader = PdfReader(contentResolver.openInputStream(uri!!))
//        val stamper = PdfStamper.createSignature(reader, os, '\u0000')
//        // Creating the appearance
//        val appearance = stamper.signatureAppearance
////         appearance.setReason(reason);
////         appearance.setLocation(location);
//
//        appearance.setVisibleSignature(rec, signPage, "sig")
//        // appearance.setImage(Image.getInstance(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/image.png"));
//        appearance.imageScale = -1f
//
//        // Creating the signature
//        val digest: ExternalDigest = BouncyCastleDigest()
//
//        MakeSignature.signDetached(
//            appearance,
//            digest,
//            pk,
//            chain as Array<X509Certificate>,
//            null,
//            null,
//            null,
//            0,
//            subfilter,
//            null as SignaturePolicyIdentifier?
//        )
//    }

    // ---------------------------------------- DIALOG´S ---------------------------------------- //

    private fun dialogRequestPassword(uri: Uri) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val dialog = AlertDialog.Builder(this)
            .setTitle("DOCUMENTO PROTEGIDO")
            .setMessage("Introduce la contraseña:")
            .setView(editText)

            .setPositiveButton("Aceptar") { dialogInterface, i ->
                passwordDoc = editText.text.toString().toByteArray()
                if(Utils.IsPasswordValid(contentResolver.openInputStream(uri), passwordDoc)){
                    continueDocSelected(uri, true)
                }
                else{
                    dialogRequestPassword(uri)
                }

            }
            .setNegativeButton("Cancelar") { dialogInterface, i ->
                docSeleccionado = null
                clearElements()
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun dialogDocumentAlreadyExists() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
//        builder.setMessage("Se ha encontrado un documento con el mismo nombre en la carpeta 'VarSign'.\n\nSi continua, ese documento se sobreescribirá por el documento actual, ¿esta seguro?")
        builder.setMessage("Se ha encontrado un documento con el nombre 'firmado_$docName' en la carpeta 'VarSign'.\n\nSi continua, ese documento se sobreescribirá por el documento actual, ¿esta seguro?")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            numPageSign = Integer.parseInt(etNumPagDoc.text.toString())
            continueSign()
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

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
//                                    alertDialogConfirmSign(docName, alias)

                                    val h = Handler(Looper.getMainLooper())
                                    h.post {
                                        aliasCert = alias
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

        val dialog = builder.create()
        dialog.show()
    }

    private fun dialogConfirmSign() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("CONFIRMACION")
        builder.setMessage("Se va a proceder a firmar el documento '$docName' con el certificado $aliasCert. ¿Esta seguro?")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            val h = Handler(Looper.getMainLooper())
            h.post {
                signOneDocument()

                // RESTABLECEMOS LOS ELEMENTOS DE LA PAGINA A SU ESTADO PRIMARIO
                clearElements()
//                etNombreArchivo.setText("")
//                txtSignPage.text = "No se ha seleccionado un documento"
//                etNumPagDoc.visibility = View.INVISIBLE
//                txtNumPagsDoc.text = ""

                dialogDocSigned()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun dialogDocSigned() {

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        lifecycleScope.launch(Dispatchers.IO){
            guardarPath("firmado_$docName?${dateFormat.format(calendar.time)}")
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("DOCUMENTO FIRMADO CON EXITO")
        builder.setMessage("Se ha guardado en la carpeta 'VarSign' del dispositivo.\n\n¿Que quiere hacer?")
//        builder.setMessage("Se ha guardado en la carpeta 'Documentos' del dispositivo.\n\n¿Que quiere hacer?")

        builder.setPositiveButton("Abrir documento") { dialog, which ->
            docSeleccionado = null

//            val file = File("/sdcard/VarSign/", "firmado_$docName")
//            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "firmado_$docName")
            val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName")
//            val uri: Uri = Uri.fromFile(file)
            val uri: Uri = FileProvider.getUriForFile(applicationContext, "com.rasamadev.varsign.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Manejar la excepción si no se encuentra una aplicación para abrir PDFs
                Toast.makeText(this, "No se ha encontrado ninguna aplicacion.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNeutralButton("Cerrar") {dialog, which ->
            docSeleccionado = null
            dialog.dismiss()
        }

        builder.setNegativeButton("Volver al inicio") { dialog, which ->
            val i = Intent(applicationContext, MainActivity::class.java)
            startActivity(i)
        }

        val dialog = builder.create()
        dialog.show()
    }

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

    /**
//    private fun firmarnew() {
//        val privateKey = KeyChain.getPrivateKey(this@SingleDocActivity, aliasCert)
//        val certChain = KeyChain.getCertificateChain(this@SingleDocActivity, aliasCert) as Array<X509Certificate>
//
//        val reader = PdfReader("$docPath$docName")
//        val writer = PdfWriter(docPath + "firmado_" + docName)
//        val pdfDoc = PdfDocument(reader, writer)
//
//        val signer = PdfSigner(reader, FileOutputStream(docPath), StampingProperties())
//
//        val appearance = signer.signatureAppearance
//            .setReason("Test Signature")
//            .setLocation("Location")
//            .setReuseAppearance(false)
//            .setPageRect(rec)
//            .setPageNumber(1)
//
//        signer.fieldName = "sig"
//
//        val pks: IExternalSignature = PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, "BC")
//        val digest: IExternalDigest = BouncyCastleDigest()
//        signer.signDetached(
//            digest,
//            pks,
//            certChain,
//            null,
//            null,
//            null,
//            0,
//            PdfSigner.CryptoStandard.CMS
//        )
//
//        pdfDoc.close()
//    }
*/

}