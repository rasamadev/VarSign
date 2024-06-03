package com.rasamadev.varsign

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.security.KeyChain
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.activity.OnBackPressedCallback
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.itextpdf.text.exceptions.BadPasswordException
import com.itextpdf.text.pdf.PdfDocument
import com.itextpdf.text.pdf.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity(), View.OnClickListener {

    // ELEMENTOS PANTALLA

    /** Boton "Instalar certificado" */
    private lateinit var btnInstalarCertificado: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnFirmarDocs: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnDocsFirmados: Button

    // ------------------------------------------------------

    /** Configuracion biometrica */
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var executor: Executor
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    /** Opciones firmar uno o varios documentos */
    private var option = 0

    /** Variable para comprobar si es la primera vez que se ejecuta la aplicacion */
    private var inicioApp = true

    /** Codigos de solicitud de archivos '.pfx' y directorios */
    companion object {
        const val PICK_PFX_REQUEST_CODE = 123
        const val PICK_PDF_REQUEST_CODE = 456
        const val PICK_DIRECTORY = 1
    }

    /** Codigo de solicitud de permisos de escritura */
    private val REQUEST_CODE_PERMISSIONS = 123

    private lateinit var sdh: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuracion de elementos del layout
        btnInstalarCertificado = findViewById<Button>(R.id.btnInstalarCertificado)
        btnFirmarDocs = findViewById<Button>(R.id.btnFirmarDocs)
        btnDocsFirmados = findViewById<Button>(R.id.btnDocsFirmados)

        // Configurar los listeners de clic
        btnInstalarCertificado.setOnClickListener(this)
        btnFirmarDocs.setOnClickListener(this)
        btnDocsFirmados.setOnClickListener(this)

        // COMPROBACION DE SI EL DISPOSITIVO TIENE CONFIGURADO ALGUN PATRON
        checkSecurityConfig()
        // COMPROBACION DE SI LA APLICACION CUENTA CON PERMISOS DE ESCRITURA
        checkPermissions()

        // SI EL USUARIO PULSA EN EL BOTON DE ATRAS DEL DISPOSITIVO
        // SE ABRIRA UN DIALOGO DE CONFIRMACION PARA SALIR DE LA APP
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dialogExitApplication()
            }
        })

        // SI HEMOS VUELTO A ESTA PANTALLA DESPUES DE FIRMAR VARIOS DOCUMENTOS
        if (intent.getStringExtra("docsFirmados") == "true"){
            Utils.mostrarError(this, "Los documentos se han firmado con exito. Se han guardado en la carpeta 'VarSign' del dispositivo.")
            // Utils.mostrarError(this, "Los documentos se han firmado con exito. Se han guardado en la carpeta 'Documentos' del dispositivo.")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            getPaths().collect {
                println("LAUNCH: ${it.path}")
                sdh = it.path
            }
        }

        // TODO (HECHO?) -- ORDENAR MEJOR CLASES
    }

    private fun getPaths() = dataStore.data.map { preferences ->
        ModeloDatos(
            path = preferences[stringPreferencesKey("paths")].orEmpty()
        )
    }

    override fun onResume() {
        super.onResume()
        if(inicioApp){
            inicioApp = false
        }
        else{
            checkSecurityConfig()
            checkPermissions()
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            /** Instalar certificado (Solicitud de archivo .pfx) */
            R.id.btnInstalarCertificado -> {
                openPfxPicker()
            }
            R.id.btnFirmarDocs -> {
                requestBiometricAuthentication()
            }
            R.id.btnDocsFirmados -> {
                // SI NO HAY NIGUN REGISTRO DE DOCUMENTO FIRMADO GUARDADO EN DATASTORE
                if(sdh.isNullOrEmpty()){
                    Utils.mostrarError(this, "¡Aun no has firmado ningun documento!")
                }
                else{
                    startActivity(Intent(this, SignedDocsHistoric::class.java).apply {
                        putExtra("SignedDocsHistoric", sdh)
                    })
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // SOLICITUD DE ARCHIVO .PFX (INSTALAR CERTIFICADO)
        if (requestCode == PICK_PFX_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedPfxUri = data?.data
            selectedPfxUri?.let {
                installPfxCertificate(selectedPfxUri)
            }
        }

        // SOLICITUD DE DIRECTORIO (FIRMAR VARIOS DOCUMENTOS)
        if (requestCode == PICK_DIRECTORY && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val directorySelected = getDirectoryPath(uri)
                    val pdfFileNames = getPdfFileNamesInDirectory(uri)

                    // SI EL DIRECTORIO CONTIENE PDF´S
                    if (pdfFileNames.isNotEmpty()) {
                        val amountOfDocs = pdfFileNames.size
                        val pdfs: MutableList<String> = mutableListOf()
                        var edf: Boolean = false
                        for(i in (0..(amountOfDocs - 1))){
                            val urii = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(directorySelected), pdfFileNames[i]))
//                            if (!isPasswordProtected(uri)){
//                                pdfs.add(i, pdfFileNames[i])
//                            }
                            if (!Utils.isPasswordProtected(contentResolver.openInputStream(urii))){
                                pdfs.add(i, pdfFileNames[i])
                            }
                            else{
                                edf = true
                            }
                        }

                        openVariousDocListActivity(pdfs, directorySelected, edf)
                    }
                    else {
                        Utils.mostrarError(
                            this,
                            "No se han encontrado documentos con la extension '.pdf' en el directorio seleccionado.\n\nPor favor, selecciona otro directorio diferente."
                        )
                    }
                }
                catch (e: BadPasswordException){

                }
            }
        }
//        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == RESULT_OK) {
//            val selectedPdfUri = data?.data
//            selectedPdfUri?.let {
//
//            }
//        }
    }

//    private fun isPasswordProtected(uri: Uri): Boolean {
//        return try {
//            val p = PdfReader(contentResolver.openInputStream(uri))
//            false
//        } catch (e: BadPasswordException) {
//            true
//        }
//    }

    /**
     * METODO QUE CONTROLA LA RESPUESTA A LA SOLICITUD
     * DEL PERMISO "WRITE_EXTERNAL_STORAGE"
     **/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createFolderVarSign()
        }
//        else {
//            dialogPermissionsRequest()
//        }
    }

    /**
     * METODO PARA CREAR LA CARPETA "VARSIGN" EN LA RAIZ DEL DISPOSITIVO
     * */
    private fun createFolderVarSign() {
        val baseDir = Environment.getExternalStorageDirectory()
//        val baseDir = "/sdcard/"
//        val newFolder = Environment.getExternalStoragePublicDirectory("VarSign")
        val newFolder = File(baseDir, "VarSign")
        if (!newFolder.exists()) {
            newFolder.mkdirs()
        }
    }

    private fun checkSecurityConfig() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isKeyguardSecure) {
            dialogSecurityConfig()
        }
    }

    /**
     * METODO QUE COMPRUEBA SI EL DISPOSITIVO TIENE PERMISOS DE ESCRITURA
     * SI EL DISPOSITIVO ES ANDROID 11+ (API 30+), COMPRUEBA EL PERMISO "MANAGE_EXTERNAL_STORAGE"
     * EN CASO CONTRARIO, COMPRUEBA EL PERMISO "WRITE_EXTERNAL_STORAGE"
     * */
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                dialogPermissionsRequest()
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
//                1
//            )
            }
            else {
                createFolderVarSign()
            }
        }
        else{
            if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                dialogPermissionsRequest()
            }
            else {
                createFolderVarSign()
            }
        }
    }

    private fun requestBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
//                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
//                    super.onAuthenticationError(errorCode, errString)
//                    if(errorCode != 10){
//                        Toast.makeText(applicationContext,"ERROR: $errString (Codigo de error: $errorCode)", Toast.LENGTH_SHORT).show()
//                    }
//                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    /** Mostrar dialog con opciones de firmar uno o varios documentos */
                    dialogDocOptions()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Autenticacion fallida.", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AUTENTICACION BIOMETRICA")
            .setSubtitle("Accede usando tu credencial biometrica")
//            .setNegativeButtonText("Use account password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun openPfxPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/x-pkcs12" // Tipo MIME para archivos PFX

        startActivityForResult(intent, PICK_PFX_REQUEST_CODE)
    }

    // MUESTRA LOS ARCHIVOS DE UN DIRECTORIO EN UN ALERTDIALOG (BORRAR)
//    private fun showPdfFilesInDirectory(uri: Uri) {
//        val documentFile = DocumentFile.fromTreeUri(this, uri)
//        val pdfFiles = documentFile?.listFiles()?.filter { it.isFile && it.name?.endsWith(".pdf", true) == true }
//
//        val pdfFileNames = pdfFiles?.mapNotNull { it.name }?.toTypedArray() ?: arrayOf("No PDF files found")
//
//        AlertDialog.Builder(this)
//            .setTitle("PDF Files")
//            .setItems(pdfFileNames, null)
//            .setPositiveButton("OK", null)
//            .show()
//    }

    private fun getPdfFileNamesInDirectory(uri: Uri): List<String> {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        return documentFile?.listFiles()?.filter {
            it.isFile && it.name?.endsWith(".pdf", true) == true && it.length() > 0
        } ?.mapNotNull {
            it.name
        } ?: emptyList()
    }

    private fun getDirectoryPath(uri: Uri): String {
//        val documentFile = DocumentFile.fromTreeUri(this, uri)
//        val a = uri.path
//        val b = uri.pathSegments
//        val c = uri.encodedPath
//        return documentFile?.name ?: "Unknown"
        return uri.lastPathSegment?.substringAfter("primary:") ?: "Unknown"
    }

    private fun openVariousDocListActivity(pdfFileNames: List<String>, directorySelected: String, encryptedDocsFounded: Boolean) {
        val intent = Intent(this, VariousDocListActivity::class.java).apply {
            putStringArrayListExtra("pdfFileNames", ArrayList(pdfFileNames))
            putExtra("directorySelected", directorySelected)
            putExtra("encryptedDocsFounded", encryptedDocsFounded)
        }
        startActivity(intent)
    }

    private fun installPfxCertificate(pfxUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(pfxUri)
            val pfxData = inputStream?.readBytes()
            inputStream?.close()

            if (pfxData != null) {
                val alias = KeyChain.createInstallIntent()
                val installIntent = KeyChain.createInstallIntent()
                installIntent.putExtra(KeyChain.EXTRA_PKCS12, pfxData)
                installIntent.putExtra(KeyChain.EXTRA_NAME, alias)
                startActivity(installIntent)
            } else {
                Toast.makeText(this, "Error al leer el archivo PFX", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al instalar el certificado", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------- DIALOG´S ---------------------------------------- //

    private fun dialogDocOptions() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccione una opción")

        val options = arrayOf(
            "Un documento",
            "Varios documentos (carpeta)"
        )

        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // Un documento
                    // LLEVAR A PANTALLA DE SELECCION DE UN DOCUMENTO CON SELECCION DE ARCHIVO Y DONDE APLICAR LA FIRMA
                    val i = Intent(applicationContext, SingleDocActivity::class.java)
                    startActivity(i)
                }
                1 -> {
                    // SELECCIONAR CARPETA, RECORRER Y MOSTRAR EN LISTA DE CHECKS LOS ARCHIVOS A SELECCIONAR "GUARDAR RUTA"
                    // LLEVAR A PANTALLA DE SELECCION DE VARIOS DOCUMENTOS CON SELECCION DE ARCHIVO, RAZON, Y LOCALIDAD (CREAR)
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    startActivityForResult(intent, PICK_DIRECTORY)
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

    private fun dialogSecurityConfig() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Este dispositivo no cuenta con un metodo de seguridad configurada. Por favor, acceda a los ajustes para configurarlo.")

        builder.setPositiveButton("Ir a ajustes") { dialog, which ->
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No se pudo abrir la configuración de seguridad", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * DIALOGO PRE-SOLICITUD DE PERMISOS DE ESCRITURA
     * */
    private fun dialogPermissionsRequest() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("La aplicacion necesita permisos de almacenamiento para funcionar correctamente.") // TODO PERFECCIONAR MENSAJE

        builder.setPositiveButton("Aceptar") { dialog, which ->
            dialog.dismiss()

            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:com.rasamadev.varsign")
                    )
                )
            }
            else{
                if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSIONS)
                }
            }
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }

    private fun dialogExitApplication() {
        val builder = AlertDialog.Builder(this)
//        builder.setTitle("Salir de la aplicación")
        builder.setMessage("¿Estás seguro de que quieres salir de la aplicación?")

        builder.setPositiveButton("Aceptar") { dialog, which ->
            finishAffinity()
            finish()
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }
}