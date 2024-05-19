package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.ContentResolver
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.security.KeyChainException
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.security.BouncyCastleDigest
import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalDigest
import com.itextpdf.text.pdf.security.ExternalSignature
import com.itextpdf.text.pdf.security.MakeSignature.CryptoStandard
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

class SingleDocActivity : AppCompatActivity(), View.OnClickListener {

    /** Boton "Instalar certificado" */
    private lateinit var btnSelDoc: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnFirmar: Button

    /** Campo de nombre del documento seleccionado */
    private lateinit var etNombreArchivo: EditText

    /** Campo de localizacion */
    private lateinit var etLocalizacion: EditText

    /** RadioGroup de opciones de lugar de la firma */
    private lateinit var rgLugarFirma: RadioGroup

    /** RadioButtons del RadioGroup "rbLugarFirma */
    private lateinit var rbArrIzq: RadioButton
    private lateinit var rbArrCen: RadioButton
    private lateinit var rbArrDer: RadioButton
    private lateinit var rbAbaIzq: RadioButton
    private lateinit var rbAbaCen: RadioButton
    private lateinit var rbAbaDer: RadioButton

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

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    companion object {
        const val PICK_PDF_REQUEST_CODE = 123
    }

    val getPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uri ->
            val path = uri.path
            println(path)
            docSeleccionado = uri
            docName = getFileName(uri) as String
            docPath = getFilePath(path) as String
            println("NOMBRE: $docName")
            println("RUTA: $docPath")
            etNombreArchivo.setText(docName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_singledoc)

        btnSelDoc = findViewById<Button>(R.id.btnSelDoc)
        btnFirmar = findViewById<Button>(R.id.btnFirmar)
        etNombreArchivo = findViewById<EditText>(R.id.etNombreArchivo)
        etLocalizacion = findViewById<EditText>(R.id.etLocalizacion)
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
                if(docSeleccionado == null){
                    alertDialogDocNotSelected()
                }
                else{
                    idLugarFirma = rgLugarFirma.checkedRadioButtonId
                    KeyChain.choosePrivateKeyAlias(
                        this,
                        object:KeyChainAliasCallback {
                            override fun alias(alias: String?) {
                                if(alias != null){
//                                    alertDialogConfirmSign(docName, alias)

                                    val h = Handler(Looper.getMainLooper())
                                    h.post {
                                        aliasCert = alias
                                        alertDialogConfirmSign()
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
            }
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == RESULT_OK) {
//            val uri: Uri? = data!!.data
//            etNombreArchivo.setText()
//        }
//    }

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

    private fun alertDialogDocNotSelected() {
        val builder = AlertDialog.Builder(this)
//        builder.setTitle("ADVERTENCIA")
        builder.setMessage("¡No has seleccionado un documento!")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun alertDialogConfirmSign() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("CONFIRMACION")
        builder.setMessage("Se va a proceder a firmar el documento '$docName' con el certificado $aliasCert. ¿Esta seguro?")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            // FIRMAR DOC Y GUARDAR COMO firmado_NOMBRE EN MISMA CARPETA
            // VOLVER A PANTALLA INICIO Y ALERTDIALOG CON "ACEPTAR" Y "VER DOCUMENTO"

        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun firmar() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun onPostExecute(aVoid: Void?) {
//                super.onPostExecute(aVoid)
//                mProgressDialog.dismiss()
//                val i = Intent(applicationContext, DoneActivity::class.java)
//                val f =
//                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//                        .toString() + "/sign_" + getFileName(mFileUri)
//                i.putExtra("uri", f)
//                startActivity(i)
            }

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
                    val file = File(docPath, "firmado_$docName")
                    val fos = FileOutputStream(file)
                    sign(
                        docSeleccionado,
                        fos,
                        chain,
                        pks,
                        DigestAlgorithms.SHA256,
                        provider.getName(),
                        CryptoStandard.CADES,
                        etLocalizacion.text.toString()
                    )
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

    @Throws(
        GeneralSecurityException::class,
        IOException::class,
        DocumentException::class
    )
    fun sign(
        uri: Uri?, os: FileOutputStream?,
        chain: Array<X509Certificate>?,
        pk: ExternalSignature?, digestAlgorithm: String?, provider: String?,
        subfilter: CryptoStandard?,
        location: String?
    ) {
        // Creating the reader and the stamper
        val reader = PdfReader(contentResolver.openInputStream(uri!!))
        val stamper = PdfStamper.createSignature(reader, os, '\u0000')
        // Creating the appearance
        val appearance = stamper.signatureAppearance
        // appearance.setReason(reason);
        appearance.setLocation(location);
        when (idLugarFirma) {
            rbArrIzq.id -> rec = Rectangle(36f, 748f, 144f, 780f)
            rbArrCen.id -> rec = Rectangle(36f, 748f, 144f, 780f)
            rbArrDer.id -> rec = Rectangle(36f, 748f, 144f, 780f)
            rbAbaIzq.id -> rec = Rectangle(36f, 748f, 144f, 780f)
            rbAbaCen.id -> rec = Rectangle(36f, 748f, 144f, 780f)
            rbAbaDer.id -> rec = Rectangle(36f, 748f, 144f, 780f)
        }


        appearance.setVisibleSignature(Rectangle(36f, 748f, 144f, 780f), 1, "sig")
        //        appearance.setImage(Image.getInstance(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/image.png"));
        appearance.imageScale = -1f
        // Creating the signature
        val digest: ExternalDigest = BouncyCastleDigest()
//        CustomMakeSignature.signDetached(
//            appearance,
//            digest,
//            pk,
//            chain,
//            null,
//            null,
//            null,
//            0,
//            subfilter
//        )
    }
}