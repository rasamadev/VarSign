package com.rasamadev.varsign

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.security.KeyChain
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.rasamadev.varsign.utils.Dialogs
import com.rasamadev.varsign.utils.ModeloDatos
import com.rasamadev.varsign.utils.Utils
import de.tsenger.androsmex.data.CANSpecDO
import de.tsenger.androsmex.data.CANSpecDOStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

/**
 * INSTANCIA AL DATASTORE DE LA APLICACION DONDE
 * GUARDAMOS EL HISTORICO DE DOCUMENTOS FIRMADOS
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity(), View.OnClickListener {

    // ELEMENTOS PANTALLA

    /** Boton "Instalar certificado" */
    private lateinit var btnInstalarCertificado: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnFirmarDocs: Button

    /** Boton "Documentos firmados" */
    private lateinit var btnDocsFirmados: Button

    /** Boton "Añadir CAN" */
    private lateinit var btnAddCan: Button

    // ------------------------------------------------------

    /** Configuracion biometrica */
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var executor: Executor
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    /** Variable para comprobar si es la primera vez que se ejecuta la aplicacion */
    private var inicioApp = true

    /** Codigos de solicitud de archivos '.pfx' y directorios */
    companion object {
        const val PICK_PFX_REQUEST_CODE = 123
        const val PICK_DIRECTORY = 1
    }

    /** Codigo de solicitud de permisos de escritura */
    private val REQUEST_CODE_PERMISSIONS = 123

    /**
     * String que recoge el historial de documentos firmados del DataStore
     * sdh = SignedDocumentsHistoric
     */
    private lateinit var sdh: String

    /** Instancia a la BD de CAN´s (SQLite LOCAL) */
    private lateinit var _canStore: CANSpecDOStore

    // ------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()

        /** COMPROBAMOS LA SEGURIDAD Y PERMISOS DEL DISPOSITIVO */
        checkSecurityAndPermissionsConfig()

        /**
         * SI EL USUARIO PULSA EN EL BOTON DE ATRAS DEL DISPOSITIVO
         * SE ABRIRA UN DIALOGO DE CONFIRMACION PARA SALIR DE LA APP
         */
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dialogExitApplication()
            }
        })

        /**
         * SI HEMOS VUELTO A ESTA PANTALLA DESPUES
         * DE FIRMAR VARIOS DOCUMENTOS POR CERTIFICADO
         */
        if (intent.getStringExtra("docsFirmados") == "true"){
            Dialogs.mostrarMensaje(this, "Los documentos se han firmado exitosamente mediante el uso de certificado digital. Se han guardado en la carpeta 'VarSign' del dispositivo.")
        }

        /**
         * SI HEMOS VUELTO A ESTA PANTALLA DESPUES
         * DE FIRMAR UNO O VARIOS DOCUMENTOS POR
         * DNI
         */
        if (intent.getStringExtra("docsFirmados") == "trueDNI"){
            Dialogs.mostrarMensaje(this, "El documento (o documentos) se han firmado exitosamente mediante el uso de DNI electronico. Se ha guardado en la carpeta 'VarSign' del dispositivo.")
        }

        /**
         * CORRUTINA QUE SE EJECUTA EN EL HILO "IO" QUE NOS PERMITIRA
         * EXTRAER EL HISTORIAL DE DOCUMENTOS FIRMADOS DEL DATASTORE
         * EN FORMATO "String"
         */
        lifecycleScope.launch(Dispatchers.IO) {
            getPaths().collect {
                sdh = it.path
            }
        }

        /** RECUPERAMOS LOS CAN´s DE LA BD */
        _canStore = CANSpecDOStore(this)
    }

    /**
     * METODO QUE SE EJECUTA CUANDO EL ESTADO DE LA APLICACION PASA A "Resumed"
     * (POR EJEMPLO, CUANDO SE NAVEGA A OTRA APLICACION).
     *
     * AL INICIAR LA APLICACION NO SE COMPRUEBAN LA SEGURIDAD Y PERMISOS, YA
     * QUE SE REALIZA EN EL METODO onCreate() Y ASI NO SE MUESTRAN VARIOS
     * DIALOGOS DE ADVERTENCIA (SI SE DIERA EL CASO)
     */
    override fun onResume() {
        super.onResume()
        if(inicioApp){
            inicioApp = false
        }
        else{
            checkSecurityAndPermissionsConfig()
        }
    }

    /**
     * METODO DE CONFIGURACION DE LOS BOTONES DE LA PANTALLA Y SUS ACCIONES
     */
    override fun onClick(view: View?) {
        when (view?.id) {
            /** Instalar certificado (Solicitud de archivo .pfx) */
            R.id.btnInstalarCertificado -> {
                openPfxPicker()
            }
            /** Firmar uno o varios documentos (Requiere autenticacion biometrica) */
            R.id.btnFirmarDocs -> {
                requestBiometricAuthentication()
            }
            /** Abrir historial de documentos firmados */
            R.id.btnDocsFirmados -> {
                /** SI NO HAY NIGUN REGISTRO DE DOCUMENTO FIRMADO GUARDADO EN DATASTORE */
                if(sdh.isNullOrEmpty()){
                    Dialogs.mostrarMensaje(this, "¡Aun no has firmado ningun documento!")
                }
                else{
                    startActivity(Intent(this, SignedDocsHistoricActivity::class.java).apply {
                        putExtra("SignedDocsHistoric", sdh)
                    })
                }
            }
            R.id.btnAddCan -> {
                /** SI EL DISPOSITIVO CONTIENE LECTOR NFC */
                if(Utils.NFCExists(this)){
                    dialogAddCan()
                }
                else{
                    Dialogs.mostrarMensaje(this, "El dispositivo no cuenta con un lector de NFC incorporado, por lo que esta opcion queda deshabiitada.")
                }
            }
        }
    }

    /**
     * METODO QUE CONTROLA EL RESULTADO DE ELEGIR UN ARCHIVO CONCRETO
     * CUANDO EL DISPOSITIVO LO SOLICITE.
     *
     * LO UTILIZAMOS PARA CONTROLAR LA SELECCION DE UN ARCHIVO DE CERTIFICADO
     * Y DE UNA CARPETA QUE PUEDE CONTENER VARIOS DOCUMENTOS.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        /** SOLICITUD DE ARCHIVO .PFX (INSTALAR CERTIFICADO) */
        if (requestCode == PICK_PFX_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedPfxUri = data?.data
            selectedPfxUri?.let {
                installPfxCertificate(selectedPfxUri)
            }
        }

        /** SOLICITUD DE DIRECTORIO (FIRMAR VARIOS DOCUMENTOS) */
        if (requestCode == PICK_DIRECTORY && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                /**
                 * Recogemos la ruta del directorio seleccionado
                 * y los archivos .pdf que contenga
                 */
                val directorySelected = getDirectoryPath(uri)
                val pdfFileNames = getPdfFileNamesInDirectory(uri)

                /** SI EL DIRECTORIO CONTIENE PDF´S */
                if (pdfFileNames.isNotEmpty()) {
                    val amountOfDocs = pdfFileNames.size            // Cantidad de documentos encontrados en el directorio
                    val pdfs: MutableList<String> = mutableListOf() // Lista mutable que contendrá documentos a mostrar en la siguiente pantalla
                    var edf = false                                 // edf = Encrypted Docs Founded

                    /**
                     * Comprobamos si existe algun documento encriptado en el directorio
                     * De ser asi, no se añadiran en la lista y se mostrara un mensaje de
                     * advertencia en la pantalla siguiente
                     */
                    var i = 0
                    var indice = 0
                    while (i < amountOfDocs) {
                        val urii = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(directorySelected), pdfFileNames[i]))
                        if (!Utils.isPasswordProtected(contentResolver.openInputStream(urii))) {
                            pdfs.add(indice, pdfFileNames[i])
                            indice++
                        } else {
                            edf = true
                        }
                        i++
                    }

                    openVariousDocListActivity(pdfs, directorySelected, edf)
                }
                /** SI EL DIRECTORIO NO CONTIENE PDF´S (Se mostrara un mensaje de error) */
                else {
                    Dialogs.mostrarMensaje(
                        this,
                        "No se han encontrado documentos con la extension '.pdf' en el directorio seleccionado.\n\nPor favor, selecciona otro directorio diferente."
                    )
                }
            }
        }
    }

    /**
     * METODO QUE CONTROLA LA RESPUESTA A LA SOLICITUD
     * DEL PERMISO "WRITE_EXTERNAL_STORAGE".
     *
     * SI ACEPTAMOS EL PERMISO, CREARA LA CARPETA 'VarSign'
     * EN EL ALMACENAMIENTO DEL DISPOSITIVO.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createFolderVarSign()
        }
    }

    /**
     * METODO QUE INICIALIZA LOS ELEMENTOS DE LA PANTALLA
     * Y LOS LISTENER DE LOS BOTONES
     */
    private fun initView() {
        btnInstalarCertificado = findViewById<Button>(R.id.btnInstalarCertificado)
        btnFirmarDocs = findViewById<Button>(R.id.btnFirmarDocs)
        btnDocsFirmados = findViewById<Button>(R.id.btnDocsFirmados)
        btnAddCan = findViewById(R.id.btnAddCan)

        btnInstalarCertificado.setOnClickListener(this)
        btnFirmarDocs.setOnClickListener(this)
        btnDocsFirmados.setOnClickListener(this)
        btnAddCan.setOnClickListener(this)
    }

    /**
     * METODO QUE DEVUELVE UN "String" DEL HISTORICO DE DOCUMENTOS
     * FIRMADOS ALMACENADO EN DATASTORE
     */
    private fun getPaths() = dataStore.data.map { preferences ->
        ModeloDatos(
            path = preferences[stringPreferencesKey("paths")].orEmpty()
        )
    }

    /**
     * METODO PARA CREAR LA CARPETA "VARSIGN" EN EL ALMACENAMIENTO DEL DISPOSITIVO
     * (SI YA EXISTE, NO CREA UNA NUEVA)
     */
    private fun createFolderVarSign() {
        val baseDir = Environment.getExternalStorageDirectory()
        val newFolder = File(baseDir, "VarSign")
        if (!newFolder.exists()) {
            newFolder.mkdirs()
        }
    }

    /**
     * METODO QUE PRIMERO COMPRUEBA SI EL DISPOSITIVO TIENE CONFIGURADO ALGUN
     * METODO DE AUTENTICACION. SI ES ASI, COMPRUEBA LOS PERMISOS.
     *
     * SE COMPRUEBA UNO DESPUES DEL OTRO EN VEZ DE LOS DOS A LA VEZ PARA QUE,
     * EN EL CASO DE QUE SE CUMPLAN AMBAS CONDICIONES, NO SE MUESTREN VARIOS
     * MENSAJES DE ALERTA, SINO UNO POR UNO
     */
    private fun checkSecurityAndPermissionsConfig() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(!keyguardManager.isKeyguardSecure){
            dialogSecurityConfig()
        }
        else{
            checkPermissions()
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

    /**
     * METODO QUE MUESTRA EL DIALOGO DE SOLICITUD DE AUTENTICACION BIOMETRICA
     * Y CONTROLA SI LA AUTENTICACION SE HA REALIZADO O NO CON EXITO
     */
    private fun requestBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                /** SI LA AUTENTICACION TIENE EXITO */
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    /** Mostrar dialog con opciones de firmar uno o varios documentos */
                    dialogDocOptions()
                }

                /** SI LA AUTENTICACION ES FALLIDA */
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Autenticacion fallida.", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AUTENTICACION BIOMETRICA")
            .setSubtitle("Accede usando tu credencial biometrica")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * METODO QUE ABRE EL EXPLORADOR DE ARCHIVOS DEL DISPOSITIVO
     * PARA SELECCIONAR UN ARCHIVO TIPO '.pfx' O '.p12'.
     *
     * AL ESPECIFICARLE ESE TIPO DE ARCHIVO, EL EXPLORADOR SOLO
     * NOS DEJARA ESCOGER ARCHIVOS DEL MISMO
     */
    private fun openPfxPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/x-pkcs12" // Tipo MIME para archivos PFX

        startActivityForResult(intent, PICK_PFX_REQUEST_CODE)
    }

    /**
     * METODO QUE RECOGE DE UN OBJETO 'Uri' QUE CONTENDRA UN DIRECTORIO
     * LOS ARCHIVOS:
     * - CON LA EXTENSION .pdf
     * - DE TAMAÑO > 0 bytes
     * - QUE NO SEAN NULOS
     */
    private fun getPdfFileNamesInDirectory(uri: Uri): List<String> {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        return documentFile?.listFiles()?.filter {
            it.isFile && it.name?.endsWith(".pdf", true) == true && it.length() > 0
        } ?.mapNotNull {
            it.name
        } ?: emptyList()
    }

    /**
     * METODO QUE DEVUELVE LA RUTA RELATIVA DEL DIRECTORIO RECOGIDO
     * MEDIANTE UN OBJETO 'Uri'
     */
    private fun getDirectoryPath(uri: Uri): String {
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

    /**
     * METODO QUE, A TRAVES DE LA LIBRERIA 'KeyChain', ABRE UN INTENT
     * QUE GESTIONA LA INSTALACION DEL CERTIFICADO DIGITAL EN EL DISPOSITIVO
     * MEDIANTE EL ARCHIVO .pfx O .p12 SELECCIONADO
     */
    private fun installPfxCertificate(pfxUri: Uri) {
        try{
            val inputStream = contentResolver.openInputStream(pfxUri)
            val pfxData = inputStream?.readBytes()
            inputStream?.close()

            if(pfxData != null){
                val alias = KeyChain.createInstallIntent()
                val installIntent = KeyChain.createInstallIntent()
                installIntent.putExtra(KeyChain.EXTRA_PKCS12, pfxData)
                installIntent.putExtra(KeyChain.EXTRA_NAME, alias)
                startActivity(installIntent)
            }
            else {
                Toast.makeText(this, "Error al leer el archivo PFX", Toast.LENGTH_SHORT).show()
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al instalar el certificado", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------- DIALOG´S ---------------------------------------- //

    /**
     * ALERTDIALOG QUE MUESTRA DOS OPCIONES A SELECCIONAR
     * PARA FIRMAR UNO O VARIOS DOCUMENTOS
     */
    private fun dialogDocOptions() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("SELECCIONE UNA OPCIÓN")

        val options = arrayOf(
            "Un documento",
            "Varios documentos de una carpeta"
        )

        builder.setItems(options) { dialog, which ->
            when (which) {
                /**
                 * Un documento
                 * LLEVA A PANTALLA DE SELECCION DE UN DOCUMENTO CON SELECCION DE ARCHIVO Y DONDE APLICAR LA FIRMA
                 */
                0 -> {
                    val i = Intent(applicationContext, SingleDocActivity::class.java)
                    startActivity(i)
                }
                /**
                 * Varios documentos
                 * SELECCIONAR CARPETA, RECORRER Y MOSTRAR EN LISTA DE CHECKS LOS ARCHIVOS A SELECCIONAR
                 */
                1 -> {
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

    /**
     * ALERTDIALOG QUE SE MOSTRARA SI EL DISPOSITIVO NO TIENE UN METODO DE
     * AUTENTICACION CONFIGURADO.
     *
     * EL BOTON "Ir a ajustes" LLEVA A LOS AJUSTES DE SEGURIDAD
     */
    private fun dialogSecurityConfig() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Este dispositivo no cuenta con un metodo de bloqueo de seguridad configurado.\n\nPor favor, acceda a los ajustes y despues a la configuracion de seguridad para habilitar al menos un metodo de bloqueo.")

        builder.setPositiveButton("Ir a ajustes") { dialog, which ->
            val intent = Intent(Settings.ACTION_SETTINGS)
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
     * ALERTDIALOG PRE-SOLICITUD DE PERMISOS DE ESCRITURA
     */
    private fun dialogPermissionsRequest(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Es necesario que la aplicacion tenga permisos de almacenamiento para funcionar correctamente.")

        builder.setPositiveButton("Dar permisos") { dialog, which ->
            dialog.dismiss()

            /**
             * SI LA VERSION DE LA API QUE USA EL DISPOSITIVO
             * ES MAYOR O IGUAL A 30 (ANDROID 11o+)
             *
             * SE ABRIRAN LOS AJUSTES DE PERMISOS DE ALMACENAMIENTO
             */
            if(Build.VERSION.SDK_INT >= 30){
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:com.rasamadev.varsign")
                    )
                )
            }
            /**
             * EN CASO CONTRARIO, SE MOSTRARA UNA VENTANA DE SOLICITUD DE PERMISOS
             * DE ESCRITURA
             */
            else{
                if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSIONS)
                }
            }
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * ALERTDIALOG QUE SE MOSTRARA AL PULSAR EL BOTON DE 'ATRAS' DEL DISPOSITIVO
     * EN LA PANTALLA PRINCIPAL
     *
     * SI PULSAMOS EN 'Aceptar', SALDREMOS DE LA APLICACION
     */
    private fun dialogExitApplication(){
        val builder = AlertDialog.Builder(this)
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

    /**
     * ALERTDIALOG PARA AÑADIR UN CAN A LA BD LOCAL
     */
    private fun dialogAddCan(){
        val factory = LayoutInflater.from(this)
        val canEntryView = factory.inflate(R.layout.sample_can, null)
        val ad = AlertDialog.Builder(this).create()
//        ad.setCancelable(false)
        ad.setView(canEntryView)
        ad.setButton(
            AlertDialog.BUTTON_POSITIVE, "Aceptar"
        ) { dialog: DialogInterface?, which: Int ->
            val text = ad.findViewById<View>(R.id.can_edit) as EditText
            val numcan = text.text.toString()
            if(numcan == "" || numcan.length < 6){
                dialogAddCan()
                Dialogs.mostrarMensaje(this, "Por favor, introduzca un numero de 6 digitos.")
            }
            else{
                _canStore!!.save(CANSpecDO(numcan, "", ""))
                Toast.makeText(this, "CAN añadido.", Toast.LENGTH_SHORT).show()
            }
        }
        ad.setButton(
            AlertDialog.BUTTON_NEGATIVE, "Cancelar"
        ) { dialog: DialogInterface?, which: Int -> ad.dismiss() }
        ad.show()
    }

    /**
     * ALERTDIALOG DE AYUDA AL USUARIO REFERENTE A LA INSTALACION
     * DE UN CERTIFICADO
     */
    fun dialogDudaCert(view: View) {
        Dialogs.mostrarMensaje(this, "Para instalar su certificado digital en su dispositivo movil, debe seleccionar desde el explorador de archivos el fichero de copia de seguridad de su certificado con formato '.pfx' o '.p12'.\n\nUna vez seleccionado, se le solicitará la contraseña que establecio en el momento de la solicitud del certificado. Cuando haya ingresado la contraseña correctamente, se le dara la opcion de establecer el alias y el almacen donde guardar el certificado.")
    }

    /**
     * ALERTDIALOG DE AYUDA AL USUARIO REFERENTE A LO QUE ES EL
     * NUMERO CAN DEL DNI ELECTRONICO
     */
    fun dialogDudaCAN(view: View) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_duda_can, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
        val dialog = builder.create()
        dialog.show()
    }
}