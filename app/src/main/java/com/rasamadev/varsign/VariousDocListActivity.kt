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

class VariousDocListActivity : AppCompatActivity(), View.OnClickListener {

    // ELEMENTOS PANTALLA


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

//    /** Ruta del directorio seleccionado */
//    private lateinit var path: String

    /** Lista de documentos seleccionados para firmar */
    private lateinit var docsSelected: MutableList<String>

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    /** Nombre del directorio seleccionado */
    private lateinit var directorySelected: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_variousdoclist)

        toolBarVariousDocs = findViewById(R.id.toolBarVariousDocs)
        cbDocsContainer = findViewById<LinearLayout>(R.id.cbDocsContainer)
        btnCancelar = findViewById<Button>(R.id.btnCancelar)
        btnAceptar = findViewById<Button>(R.id.btnAceptar)

        btnCancelar.setOnClickListener(this)
        btnAceptar.setOnClickListener(this)

        setSupportActionBar(toolBarVariousDocs)

        val pdfFileNames = intent.getStringArrayListExtra("pdfFileNames")
        directorySelected = intent.getStringExtra("directorySelected") as String
        pdfFileNames?.forEach { fileName ->
            val checkBox = CheckBox(this).apply {
                text = fileName
                textSize = 26f
                isChecked = true
            }
            cbDocsContainer.addView(checkBox)
        }
        if(intent.getBooleanExtra("encryptedDocsFounded", true)){
            Utils.mostrarError(this, "Se han eliminado de la lista uno o varios documentos protegidos con contraseña.\n\nSi desea firmar esos documentos, por favor, hagalo mediante la opcion de firma de 'Un documento'")
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btnCancelar -> {
                onBackPressed()
            }
            R.id.btnAceptar -> {
                docsSelected = mutableListOf<String>()
                for (i in 0 until cbDocsContainer.childCount) {
                    val child = cbDocsContainer.getChildAt(i)
                    if (child is CheckBox && child.isChecked) {
                        docsSelected.add(child.text.toString())
                    }
                }

                // SI NO SE HA SELECCIONADO NINGUN DOCUMENTO
                if (docsSelected.isEmpty()) {
                    Utils.mostrarError(this, "¡Selecciona al menos un documento!")
                }
                else{
                    dialogSignPosition()
                }

            }
        }
    }

    private fun signVariousDocuments() {
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
//                    val pks: ExternalSignature = PrivateKeySignature(
//                        privateKey,
//                        DigestAlgorithms.SHA256,
//                        provider.getName()
//                    )

                    val tmp = File.createTempFile("eid", ".pdf", cacheDir)
                    for(doc: String in docsSelected){
//                        val file = Uri.fromFile(File("content://com.android.externalstorage.documents" + directoryPath, doc))
//                        val file = Uri.fromFile(File("/sdcard/$directorySelected/$doc"))
                        val file = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(directorySelected), doc))
                        // TODO (HECHO?) COMPROBANTE SI EL DIRECTORIO DOCUMENTS NO EXISTE, A CARPETA DESCARGAS?
                        // TODO ARCHIVO CON EL MISMO NOMBRE??
//                        val filesigned = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "firmado_$doc")
                        val filesigned = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$doc")
//                        val filesigned = File(Environment.getExternalStoragePublicDirectory(directorySelected), "firmado_$doc")
//                        val filesigned = File("/sdcard/VarSign/", "firmado_$doc")
                        val fos = FileOutputStream(filesigned)

                        val reader = PdfReader(contentResolver.openInputStream(file!!))
                        val stamper = PdfStamper.createSignature(reader, fos, '\u0000')

                        val appearance = stamper.signatureAppearance
                        appearance.setVisibleSignature(rec, 1, "sig")
                        appearance.imageScale = -1f

                        val digest: ExternalDigest = BouncyCastleDigest()

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

                        lifecycleScope.launch(Dispatchers.IO){
                            guardarPath("firmado_$doc")
                        }

//                        sign(
//                            file,
//                            fos,
//                            chain,
//                            pks,
////                            DigestAlgorithms.SHA256,
////                            provider.getName(),
//                            MakeSignature.CryptoStandard.CADES,
//                            1,
//                            rec
//                        )
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

//    @Throws(GeneralSecurityException::class, IOException::class, DocumentException::class)
//    fun sign(
//        uri: Uri?,
//        os: FileOutputStream?,
//        chain: Array<X509Certificate>?,
//        pk: ExternalSignature,
////        digestAlgorithm: String?,
////        provider: String?,
//        subfilter: MakeSignature.CryptoStandard,
//        signPage: Int,
//        rec: Rectangle
//    ){
//        val reader = PdfReader(contentResolver.openInputStream(uri!!))
//        val stamper = PdfStamper.createSignature(reader, os, '\u0000')
//
//        val appearance = stamper.signatureAppearance
//        appearance.setVisibleSignature(rec, signPage, "sig")
//        appearance.imageScale = -1f
//
//        val digest: ExternalDigest = BouncyCastleDigest()
//
////        CustomMakeSignature.signDetached(
////            appearance,
////            digest,
////            pk,
////            chain as Array<X509Certificate>,
////            null,
////            null,
////            null,
////            0,
////            subfilter,
////            null as SignaturePolicyIdentifier?
////        )
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
                arrIzq.id -> rec = Rectangle(20f, 800f, 130f, 830f)
                arrCen.id -> rec = Rectangle(243f, 800f, 353f, 830f)
                arrDer.id -> rec = Rectangle(466f, 800f, 576f, 830f)
                abaIzq.id -> rec = Rectangle(20f, 20f, 130f, 50f)
                abaCen.id -> rec = Rectangle(243f, 20f, 353f, 50f)
                abaDer.id -> rec = Rectangle(466f, 20f, 576f, 50f)
            }
            dialogSignMethods()
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        builder.create().show()
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

        builder.create().show()
    }

    private fun dialogConfirmSign() {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("SE VAN A FIRMAR LOS SIGUIENTES DOCUMENTOS:")

        // TODO PERFECCIONAR STRING DE LISTA DE DOCUMENTOS??
        var list: String = ""
        for (doc: String in docsSelected) {
            list += "\n\n- $doc"
        }
        list += "\n\n\n\nSi esta de acuerdo, pulse en el boton 'Aceptar'.\n\nNOTA: La firma se aplicará en la pagina 1 de cada documento."
        builder.setMessage(list)

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