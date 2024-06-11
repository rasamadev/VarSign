package com.rasamadev.varsign

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
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

    /** Lista de documentos seleccionados para firmar */
    private lateinit var docsSelected: MutableList<String>

    /** Alias del certificado seleccionado */
    private lateinit var aliasCert: String

    /** Nombre del directorio seleccionado */
    private lateinit var directorySelected: String

    /** String que guardara la posicion de la firma */
    private lateinit var signPosition: String

    /** Instancia a la BD de CAN´s (SQLite LOCAL) */
    private lateinit var _canStore: CANSpecDOStore

    /** Numero CAN seleccionado */
    private lateinit var can: CANSpecDO

    // ------------------------------------------------------

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
            Dialogs.mostrarMensaje(this, "Se han eliminado de la lista uno o varios documentos protegidos con contraseña.\n\nSi desea firmar esos documentos, por favor, hagalo mediante la opcion de firma de 'Un documento'.")
        }

        /** RECUPERAMOS LOS CAN´s DE LA BD */
        _canStore = CANSpecDOStore(this)
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
                    Dialogs.mostrarMensaje(this, "¡Selecciona al menos un documento!")
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
     * ALERTDIALOG DE ADVERTENCIA QUE SE MUESTRA CUANDO SE ENCUENTRAN
     * UNO O MAS ARCHIVOS YA FIRMADOS CON EL MISMO NOMBRE EN LA CARPETA 'VarSign'
     */
    private fun dialogDocumentsAlreadyExists(docsFounded: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Se ha encontrado en la carpeta 'VarSign' una copia con el mismo nombre de los siguientes documentos seleccionados:\n\n$docsFounded\nSi continua, seran sobrescritos por los documentos actuales, ¿Esta seguro?")

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
                                val ad = android.app.AlertDialog.Builder(this).create()
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
                        Dialogs.mostrarMensaje(this, "El dispositivo no cuenta con un lector de NFC incorporado.")
                    }
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
     * ALERTDIALOG QUE SE MUESTRA CUANDO EL USUARIO INTENTA ACCEDER
     * AL METODO DE FIRMA POR DNI PERO TIENE EL LECTOR NFC DESHABILITADO.
     * CONTIENE UN BOTON QUE LLEVA A LOS AJUSTES DE CONECTIVIDAD DEL DISPOSITIVO.
     */
    private fun dialogNFCConfig() {
        val builder = android.app.AlertDialog.Builder(this)
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

    /**
     * ALERTDIALOG DE CONFIRMACION PARA FIRMAR LOS DOCUMENTOS SELECCIONADOS.
     * ESTOS DOCUMENTOS SE MUESTRAN EN UNA LISTA
     */
    private fun dialogConfirmSign(cert: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("SE VAN A FIRMAR LOS SIGUIENTES DOCUMENTOS:")

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
                if(cert){
                    /** ARRANCAMOS EN UN NUEVO HILO */
                    Executors.newSingleThreadExecutor().execute{
                        /** RECOGEMOS LA CLAVE PRIVADA Y EL CHAIN DEL CERTIFICADO SELECCIONADO */
                        val privateKey = KeyChain.getPrivateKey(applicationContext, aliasCert)
                        val keyFactory = KeyFactory.getInstance(privateKey!!.algorithm, "AndroidKeyStore")
                        val chain: Array<X509Certificate>? = KeyChain.getCertificateChain(applicationContext, aliasCert)
                        Utils.signDocuments(privateKey, chain, docsSelected.toTypedArray(), directorySelected, byteArrayOf(), 1, signPosition)

                        Handler(Looper.getMainLooper()).post{
                            /**
                             * GUARDAMOS EN EL DATASTORE DE LA APLICACION EL NOMBRE DEL DOCUMENTO
                             * FIRMADO JUNTO CON LA FECHA Y HORA DE LA FIRMA, AMBOS DATOS SEPARADOS
                             * POR UN '?' (PARA DESPUES SPLITEARLO MEDIANTE ESE SEPARADOR)
                             */
                            for(docName: String in docsSelected){
                                val calendar = Calendar.getInstance()
                                val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                                lifecycleScope.launch(Dispatchers.IO){
                                    guardarPath("firmado_$docName?${dateFormat.format(calendar.time)}<Certificado digital>$aliasCert")
                                }
                            }

                            val i = Intent(applicationContext, MainActivity::class.java)
                            i.putExtra("docsFirmados", "true")
                            startActivity(i)
                        }
                    }
                }
                else{
                    val intent = Intent(applicationContext, DNISignActivity::class.java)
                    intent.putExtra("CAN", can.canNumber)
                    intent.putExtra("docPath", directorySelected)
                    intent.putExtra("docsSelected", docsSelected.toTypedArray())
                    intent.putExtra("signPosition", signPosition)
                    startActivity(intent)
                }
            }
            setNegativeButton("Cancelar") { dialog, which ->
                dialog.dismiss()
            }
        }

        val dialog = builder.create()
        dialog.show()
    }
}