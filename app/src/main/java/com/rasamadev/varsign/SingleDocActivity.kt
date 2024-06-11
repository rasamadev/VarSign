package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
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
import com.itextpdf.text.Rectangle
import com.itextpdf.text.exceptions.InvalidPdfException
import com.itextpdf.text.pdf.PdfReader
import com.rasamadev.varsign.adapter.AdapterCanList
import com.rasamadev.varsign.utils.Dialogs
import com.rasamadev.varsign.utils.Utils
import de.tsenger.androsmex.data.CANSpecDO
import de.tsenger.androsmex.data.CANSpecDOStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.KeyFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/**
 * CLASE QUE CONTROLA EL PROCESO DE FIRMADO DE UN UNICO DOCUMENTO
 * MOSTRANDO LA PANTALLA PARA SELECCIONAR UN DOCUMENTO Y EN QUE
 * PAGINA Y POSICION APLICAR LA FIRMA
 */
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

    /** Texto que, en base a si se ha seleccionado o no un documento, muestra:
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

    /** String que guardara la posicion de la firma */
    private lateinit var signPosition: String

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    /** Lector del documento seleccionado */
    private lateinit var pdfReader: PdfReader

    /** Posible contraseña del documento a firmar */
    private lateinit var passwordDoc: ByteArray

    /** Si el documento seleccionado tiene contraseña o no */
    private var docPasswordExists: Boolean = false

    /** Instancia a la BD de CAN´s (SQLite LOCAL) */
    private lateinit var _canStore: CANSpecDOStore

    /** Numero CAN seleccionado */
    private lateinit var can: CANSpecDO

    // ------------------------------------------------------

    /**
     * RECOGEMOS EL PDF SELECCIONADO EN EL EXPLORADOR DE ARCHIVOS
     */
    val getPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uri ->
            try{
                /** COMPROBACION DE SI EL PDF TIENE CONTRASEÑA */
                docName = getFileName(uri) as String
                if(Utils.isPasswordProtected(contentResolver.openInputStream(uri))){
                    docPasswordExists = true
                    dialogRequestPassword(uri)
                }
                else{
                    docPasswordExists = false
                    continueDocSelected(uri)
                }
            }
            catch (e: InvalidPdfException) {
                /**
                 * RESTABLECEMOS LOS ELEMENTOS DE LA PAGINA A SU ESTADO PRIMARIO
                 * Y docSeleccionado = null PARA QUE, CUANDO PULSEMOS EN EL BOTON
                 * "FIRMAR DOCUMENTO", NO HAYA NINGUN DOCUMENTO SELECCIONADO
                 */
                docSeleccionado = null
                clearElements()
                Dialogs.mostrarMensaje(this, "No se pudo abrir '$docName' debido a que no es un tipo de archivo admitido o esta dañado.\n\nPor favor, intentelo de nuevo con otro documento.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_singledoc)
        initView()

        /** RECUPERAMOS LOS CAN´s DE LA BD */
        _canStore = CANSpecDOStore(this)
    }

    /**
     * METODO QUE INICIALIZA LOS ELEMENTOS DE LA PANTALLA
     * Y LOS LISTENER DE LOS BOTONES
     */
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

    /**
     * METODO DE CONFIGURACION DE LOS BOTONES DE LA PANTALLA Y SUS ACCIONES
     */
    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btnSelDoc -> {
                /** ABRIR EXPLORADOR DE ARCHIVOS PARA SELECCIONAR PDF */
                getPdf.launch("application/pdf")
            }
            R.id.btnFirmar -> {
                /** SI NO SE HA SELECCIONADO NINGUN DOCUMENTO */
                if(docSeleccionado == null){
                    Dialogs.mostrarMensaje(this, "¡No has seleccionado un documento!")
                }
                /**
                 * SI EL NUMERO DE PAGINA NO SE HA INDICADO
                 * O ES MENOR QUE 1
                 * O ES MAYOR QUE EL NUMERO DE PAGINAS DEL DOCUMENTO
                 */
                else if(etNumPagDoc.text.toString() == "" || Integer.parseInt(etNumPagDoc.text.toString()) < 1 || Integer.parseInt(etNumPagDoc.text.toString()) > docPages){
                    Dialogs.mostrarMensaje(this, "Por favor, establece un numero de pagina correcto. (Minimo: 1, maximo: $docPages)")
                }
                /** SI YA EXISTE EN LA CARPETA 'VarSign' EL MISMO DOCUMENTO YA FIRMADO O UNO CON NOMBRE SIMILAR */
                else if(File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName").exists()){
                    dialogDocumentAlreadyExists()
                }
                /** SI ESTA CORRECTO, GUARDAMOS EL NUMERO DE LA PAGINA EN LA QUE SE VA A FIRMAR Y PROSEGUIMOS CON LA FIRMA */
                else{
                    numPageSign = Integer.parseInt(etNumPagDoc.text.toString())
                    continueSign()
                }
            }
        }
    }

    /**
     * METODO QUE DEVUELVE LOS ELEMENTOS DE LA PANTALLA AL ESTADO
     * EN EL QUE SE ENCUENTRAN AL ENTRAR DESDE EL MENU DE INICIO
     */
    private fun clearElements() {
        etNombreArchivo.setText("")
        txtSignPage.text = "No se ha seleccionado un documento"
        etNumPagDoc.visibility = View.INVISIBLE
        txtNumPagsDoc.text = ""
    }

    /**
     * METODO QUE RECOGE EL NOMBRE DE UN ARCHIVO A PARTIR DE UN OBJETO Uri
     */
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        /** SI EL ESQUEMA ES DE TIPO '//content' */
        if(uri.scheme == ContentResolver.SCHEME_CONTENT){
            /** CREAMOS UN CURSOR, LO MOVEMOS AL PRIMER REGISTRO Y EXTRAEMOS EL NOMBRE DEL ARCHIVO */
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use{
                if(it.moveToFirst()){
                    fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        /** SI EL ESQUEMA NO ES DE TIPO '//content' */
        if(fileName == null){
            /**
             * EXTRAEMOS LA RUTA DEL ARCHIVO Y SEPARAMOS EL NOMBRE
             * A PARTIR DEL ULTIMO CARACTER '/'
             */
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != -1) {
                fileName = fileName?.substring(cut!! + 1)
            }
        }
        return fileName
    }

    /**
     * METODO QUE DEVUELVE LA RUTA DE UN ARCHIVO EXTRAYENDO
     * EL CONTENIDO QUE HAYA A PARTIR DEL ULTIMO CARACTER '/'
     * HACIA ATRAS
     */
    private fun getFilePath(path: String?): String? {
        if (path != null) {
            val lastSlashIndex = path.lastIndexOf('/')
            if (lastSlashIndex != -1) {
                return path.substring(0, lastSlashIndex + 1).substringAfter(":")
            }
        }
        return null
    }

    /**
     * METODO QUE CARGA EL DOCUMENTO SELECCIONADO, GUARDA SU NOMBRE, RUTA
     * Y NUMERO DE PAGINAS, Y MODIFICA ALGUNOS ELEMENTOS DE LA PANTALLA
     */
    private fun continueDocSelected(uri: Uri) {
        /** GUARDAMOS EL NOMBRE Y RUTA DEL DOCUMENTO SELECCIONADO */
        docName = getFileName(uri) as String // sobra?
        docPath = getFilePath(uri.path) as String

        /** SI EL DOCUMENTO SELECCIONADO ESTA CORRECTO, LO GUARDAMOS */
        docSeleccionado = uri

        /** CARGAMOS EL DOCUMENTO CON O SIN CONTRASEÑA */
        if(docPasswordExists){
            pdfReader = PdfReader(contentResolver.openInputStream(uri), passwordDoc)
        }
        else{
            pdfReader = PdfReader(contentResolver.openInputStream(uri))
        }

        /** RECOGEMOS EL NUMERO DE PAGINAS DEL DOCUMENTO SELECCIONADO */
        docPages = pdfReader.numberOfPages

        /** ESTABLECEMOS EL NOMBRE EN EL EDITTEXT 'etNombreArchivo' */
        etNombreArchivo.setText(docName)

        /**
         * MOSTRAMOS LOS TEXTOS Y EDITTEXT PARA INDICAR EN QUE PAGINA
         * SE QUIERE APLICAR LA FIRMA
         */
        txtSignPage.text = "¿En que pagina quieres aplicar la firma?"
        etNumPagDoc.visibility = View.VISIBLE
        etNumPagDoc.setText("1")
        txtNumPagsDoc.text = "Numero de paginas del documento: $docPages"

        // APLICARIA EL MAXLENGTH EN EL EDITTEXT SEGUN LAS PAGS DEL DOC PERO NO SE PUEDE
    }

    /**
     * METODO QUE CONTINUA EL PROCESO DE FIRMADO.
     *
     * RECOGE LA OPCION DE POSICION DE FIRMA SELECCIONADA Y COMPRUEBA LA ORIENTACION
     * DE LA PAGINA DONDE SE VA A FIRMAR PARA PLASMARLA EN LAS COORDENADAS ADECUADAS.
     */
    private fun continueSign() {
        idLugarFirma = rgLugarFirma.checkedRadioButtonId
        val rectangle: Rectangle = pdfReader.getPageSizeWithRotation(numPageSign)
        if (rectangle.height >= rectangle.width){
            when (idLugarFirma) {
                rbArrIzq.id -> signPosition = "arrIzq"
                rbArrCen.id -> signPosition = "arrCen"
                rbArrDer.id -> signPosition = "arrDer"
                rbAbaIzq.id -> signPosition = "abaIzq"
                rbAbaCen.id -> signPosition = "abaCen"
                rbAbaDer.id -> signPosition = "abaDer"
            }
        }
        else{
            when (idLugarFirma) {
                rbArrIzq.id -> signPosition = "arrIzq"
                rbArrCen.id -> signPosition = "arrCen"
                rbArrDer.id -> signPosition = "arrDer"
                rbAbaIzq.id -> signPosition = "abaIzq"
                rbAbaCen.id -> signPosition = "abaCen"
                rbAbaDer.id -> signPosition = "abaDer"
            }
        }

        dialogSignMethods()
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

    // ---------------------------------------- DIALOG´S ---------------------------------------- //

    /**
     * ALERTDIALOG QUE SE MUESTRA AL ABRIR UN DOCUMENTO PROTEGIDO CON CONTRASEÑA
     * Y QUE CONTIENE UN EDITTEXT PARA ESCRIBIRLA.
     *
     * SI LA CONTRASEÑA ESCRITA ES INCORRECTA, SE MUESTRA UN MENSAJE DE ERROR
     *
     * @param errorPasswordIncorrect: Si es true, se muestra el mensaje de error
     */
    private fun dialogRequestPassword(uri: Uri, errorPasswordIncorrect: Boolean = false) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        if(errorPasswordIncorrect){
            editText.error = "Contraseña incorrecta."
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("DOCUMENTO PROTEGIDO")
            .setMessage("Introduce la contraseña:")
            .setView(editText)

            .setPositiveButton("Aceptar") { dialogInterface, i ->
                passwordDoc = editText.text.toString().toByteArray()
                if(Utils.isPasswordValid(contentResolver.openInputStream(uri), passwordDoc)){
                    continueDocSelected(uri)
                }
                else{
                    dialogRequestPassword(uri, true)
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

    /**
     * ALERTDIALOG DE ADVERTENCIA QUE SE MUESTRA CUANDO SE ENCUENTRA
     * UN ARCHIVO YA FIRMADO CON EL MISMO NOMBRE EN LA CARPETA 'VarSign'
     */
    private fun dialogDocumentAlreadyExists() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Se ha encontrado un documento con el nombre 'firmado_$docName' en la carpeta 'VarSign'.\n\nSi continua, ese documento se sobrescribirá por el documento actual, ¿esta seguro?")

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

    /**
     * ALERTDIALOG DE MENU DE SELECCION DEL METODO DE FIRMA A USAR
     * (POR CERTIFICADO DIGITAL O DNI ELECTRONICO)
     */
    private fun dialogSignMethods() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccione un metodo de firmado")

        val options = arrayOf(
            "Certificado digital",
            "DNI electronico (mediante NFC)"
        )

        builder.setItems(options) { dialog, which ->
            when (which) {
                /** Certificado digital */
                0 -> {
                    KeyChain.choosePrivateKeyAlias(
                        this,
                        object: KeyChainAliasCallback {
                            override fun alias(alias: String?) {
                                if(alias != null){
                                    aliasCert = alias
                                    /** MOSTRAMOS EL MENSAJE DE CONFIRMACION EN EL HILO DE UI */
                                    runOnUiThread{
                                        dialogConfirmSign(true)
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
                /** DNI electronico (NFC) */
                1 -> {
                    /** SI EL DISPOSITIVO CONTIENE LECTOR NFC */
                    if(Utils.NFCExists(this)){
                        /**
                         * COMPROBAMOS SI EL NFC ESTA ACTIVADO PARA QUE LA FIRMA POR DNI
                         * FUNCIONE CORRECTAMENTE, YA QUE SI PASAMOS A LA SIGUIENTE
                         * PANTALLA Y NO ESTA ACTIVADO, NO SE RECONOCERA EL DNI AL INTENTAR
                         * LEERLO
                         */
                        if(Utils.NFCActivated(this)){
                            /** SI NO HAY CAN´S GUARDADOS EN LA BD */
                            if(_canStore.getAll().isEmpty()){
                                Dialogs.dialogNoCans(this)
                            }
                            else{
                                /** ABRIMOS EL ALERTDIALOG DE SELECCION DE UN CAN */
                                val factory = LayoutInflater.from(this)
                                val canListView: View = factory.inflate(R.layout.can_list, null)
                                val ad = AlertDialog.Builder(this).create()
                                ad.setCancelable(true)
                                ad.setIcon(R.drawable.dnie_logo)
                                ad.setView(canListView)
                                val listW = canListView.findViewById<View>(R.id.canList) as ListView
                                listW.adapter = AdapterCanList(this, listW, _canStore)
                                listW.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>, view: View?, position: Int, id: Long ->
                                    can = parent.getItemAtPosition(position) as CANSpecDO
                                    ad.dismiss()
                                    dialogConfirmSign(false)
                                }
                                ad.show()
                            }
                        }
                        /** SI EL DISPOSITIVO TIENE NFC, PERO ESTA DESHABILITADO */
                        else{
                            dialogNFCConfig()
                        }
                    }
                    else{
                        Dialogs.mostrarMensaje(this, "El dispositivo no cuenta con un lector de NFC incorporado, por lo que no es posible firmar usando este metodo.")
                    }
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
     * ALERTDIALOG DE CONFIRMACION PARA FIRMAR EL DOCUMENTO
     */
    private fun dialogConfirmSign(cert: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("CONFIRMACION")
        if(cert){
            builder.setMessage("Se va a proceder a firmar el documento '$docName' con el certificado $aliasCert. ¿Esta seguro?")
        }
        else{
            builder.setMessage("Se va a proceder a firmar el documento '$docName' con el CAN ${can.canNumber}. ¿Esta seguro?")
        }

        builder.setPositiveButton("Aceptar") { dialog, which ->
            /** FIRMA CON CERTIFICADO DIGITAL */
            if(cert){
                /** ARRANCAMOS EN UN NUEVO HILO */
                Executors.newSingleThreadExecutor().execute{
                    /** RECOGEMOS LA CLAVE PRIVADA Y EL CHAIN DEL CERTIFICADO SELECCIONADO */
                    val privateKey = KeyChain.getPrivateKey(applicationContext, aliasCert)
                    val keyFactory = KeyFactory.getInstance(privateKey!!.algorithm, "AndroidKeyStore")
                    val chain: Array<X509Certificate>? = KeyChain.getCertificateChain(applicationContext, aliasCert)
                    if(docPasswordExists){
                        Utils.signDocuments(privateKey, chain, arrayOf(docName), docPath, passwordDoc, numPageSign, signPosition)
                    }
                    else{
                        Utils.signDocuments(privateKey, chain, arrayOf(docName), docPath, byteArrayOf(), numPageSign, signPosition)
                    }

                    Handler(Looper.getMainLooper()).post{
                        /**
                         * GUARDAMOS EN EL DATASTORE DE LA APLICACION EL NOMBRE DEL DOCUMENTO
                         * FIRMADO JUNTO CON LA FECHA Y HORA DE LA FIRMA, AMBOS DATOS SEPARADOS
                         * POR UN '?' (PARA DESPUES SPLITEARLO MEDIANTE ESE SEPARADOR)
                         */
                        val calendar = Calendar.getInstance()
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                        lifecycleScope.launch(Dispatchers.IO){
                            guardarPath("firmado_$docName?${dateFormat.format(calendar.time)}<Certificado digital>$aliasCert")
                        }

                        /** RESTABLECEMOS LOS ELEMENTOS DE LA PAGINA A SU ESTADO PRIMARIO */
                        clearElements()

                        /** MOSTRAMOS MENSAJE DE DOCUMENTO FIRMADO */
                        dialogDocSigned()
                    }
                }
            }
            /** FIRMA CON DNI ELECTRONICO */
            else{
                val intent = Intent(this, DNISignActivity::class.java)
                intent.putExtra("CAN", can.canNumber)
                intent.putExtra("docsSelected", arrayOf(docName))
                intent.putExtra("docPath", docPath)
                intent.putExtra("numPageSign", numPageSign)
                intent.putExtra("signPosition", signPosition)
                if(docPasswordExists){
                    intent.putExtra("passwordDoc", passwordDoc)
                }
                startActivity(intent)
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    /**
     * ALERTDIALOG QUE SE MUESTRA TRAS HABER FIRMADO UN DOCUMENTO CON EXITO.
     * PRESENTA TRES OPCIONES:
     *
     * - ABRIR DOCUMENTO: Intenta abrir el documento firmado con la aplicacion
     * predeterminada del dispositivo
     * - VOLVER AL INICIO: Vuelve a la pantalla de inicio de la aplicacion
     * - CERRAR: Cerrar esta ventana y mantenerse en la misma pantalla
     */
    private fun dialogDocSigned() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("DOCUMENTO FIRMADO CON EXITO")
        builder.setMessage("Se ha guardado en la carpeta 'VarSign' del dispositivo.\n\n¿Que quiere hacer?")

        builder.setPositiveButton("Abrir documento") { dialog, which ->
            /**
             * ESTABLECEMOS docSeleccionado A NULL PARA QUE, AL QUEDARNOS EN LA MISMA PANTALLA, EL
             * USUARIO NO PULSE EN FIRMAR DOCUMENTO SIN HABER SELECCIONADO UNO ANTES
             */
            docSeleccionado = null

            /**
             * ABRIMOS EL DOCUMENTO FIRMADO MEDIANTE EL FILEPROVIDER CONFIGURADO EN EL AndroidManifest
             */
            val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName")
            val uri: Uri = FileProvider.getUriForFile(applicationContext, "com.rasamadev.varsign.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(intent)
            }
            catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No se ha encontrado ninguna aplicacion que pueda realizar esta accion.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNeutralButton("Cerrar") {dialog, which ->
            /**
             * ESTABLECEMOS docSeleccionado A NULL PARA QUE, AL QUEDARNOS EN LA MISMA PANTALLA, EL
             * USUARIO NO PULSE EN FIRMAR DOCUMENTO SIN HABER SELECCIONADO UNO ANTES
             */
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

    /**
     * ALERTDIALOG QUE SE MUESTRA CUANDO EL USUARIO INTENTA ACCEDER
     * AL METODO DE FIRMA POR DNI PERO TIENE EL LECTOR NFC DESHABILITADO.
     * CONTIENE UN BOTON QUE LLEVA A LOS AJUSTES DE CONECTIVIDAD DEL DISPOSITIVO.
     */
    private fun dialogNFCConfig() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Es necesario que el lector NFC este activado en su dispositivo antes de seguir con el proceso de firma. Por favor, acceda a los ajustes para configurarlo.")

        builder.setPositiveButton("Ir a ajustes") { dialog, which ->
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No se pudo abrir la configuración de seguridad", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, which ->
            dialog.dismiss()
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }
}