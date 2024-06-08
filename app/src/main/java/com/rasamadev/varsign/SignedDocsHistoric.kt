package com.rasamadev.varsign

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.rasamadev.varsign.adapter.AdapterSignedDocsHistoric
import java.io.File

/**
 * CLASE QUE MUESTRA LA PANTALLA DEL HISTORICO DE DOCUMENTOS FIRMADOS
 * POR EL USUARIO Y CONTROLA CUANDO SE PULSA EN UNO
 */
class SignedDocsHistoric : AppCompatActivity(), AdapterSignedDocsHistoric.OnItemClickListener {

    // ELEMENTOS PANTALLA

    /** Lista de documentos firmados */
    private lateinit var rvSignedDocsHistoric: RecyclerView

    /** toolbar 'Top Bar' */
    private lateinit var toolBarSignedDocs: MaterialToolbar

    // ------------------------------------------------------

    /** String del historico de documentos firmados */
    private lateinit var signedDocsHistoric: String

    /** List de los distintos documentos spliteada */
    private lateinit var splitSDH: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signed_docs_historic)
        initView()

        /**
         * RECOGEMOS EL EXTRA DE LA ACTIVITY
         * (String de historico de documentos firmados)
         */
        signedDocsHistoric = intent.getStringExtra("SignedDocsHistoric") as String

        /** LO SPLITEAMOS Y LO APLICAMOS AL RECYCLERVIEW DE LA PANTALLA */
        splitSDH = signedDocsHistoric.split(",").map { it.trim() }.reversed()
        rvSignedDocsHistoric.layoutManager = LinearLayoutManager(this)
        rvSignedDocsHistoric.adapter = AdapterSignedDocsHistoric(splitSDH, this)

        // TODO ITEM > MOSTRAR METODO FIRMA USADO
    }

    /**
     * METODO QUE INICIALIZA LOS ELEMENTOS DE LA PANTALLA
     * Y ESTABLECE LA TOOLBAR
     */
    private fun initView() {
        rvSignedDocsHistoric = findViewById(R.id.rvSignedDocsHistoric)
        toolBarSignedDocs = findViewById(R.id.toolBarSignedDocs)

        setSupportActionBar(toolBarSignedDocs)
    }

    /**
     * METODO DE CONFIGURACION DE LA PULSACION DE UN DOCUMENTO DE LA LISTA
     */
    override fun onItemClick(position: Int) {
        try {
            /** RECOGEMOS EL ITEM SELECCIONADO */
            val item = (rvSignedDocsHistoric.adapter as AdapterSignedDocsHistoric).itemList[position]

            /**
             * COMPROBAMOS SI EXISTE EL DOCUMENTO DEL ITEM SELECCIONADO
             * - SI EXISTE, LO ABRIMOS
             * - SI NO, SE MUESTRA UN MENSAJE DE ERROR INDICANDO QUE NO EXISTE
             */
            val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), item.substringBefore("?"))
            if(file.exists()){
                val uri: Uri = FileProvider.getUriForFile(applicationContext, "com.rasamadev.varsign.provider", file)

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/pdf")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            }
            else{
                Utils.mostrarError(this, "No se ha encontrado el documento seleccionado en la carpeta 'VarSign'. Es posible que se haya eliminado o movido a otra carpeta.")
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No se ha encontrado ninguna aplicacion que pueda realizar esta accion.", Toast.LENGTH_SHORT).show()
        }
    }
}