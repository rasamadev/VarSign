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
import java.io.File

class SignedDocsHistoric : AppCompatActivity(), AdapterSignedDocsHistoric.OnItemClickListener {

    // ELEMENTOS PANTALLA

    private lateinit var recyclerView: RecyclerView

    private lateinit var toolBarSignedDocs: MaterialToolbar

    // ------------------------------------------------------

    private lateinit var signedDocsHistoric: String

    private lateinit var splitSDH: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signed_docs_historic)

        recyclerView = findViewById(R.id.rvSignedDocsHistoric)
        toolBarSignedDocs = findViewById(R.id.toolBarSignedDocs)

//        lifecycleScope.launch(Dispatchers.IO) {
//            getPaths().collect {
//                withContext(Dispatchers.Main){
//                    println("LAUNCH: ${it.path}")
//                    signedDocsHistoric = it.path
//                }
//            }
//        }

        setSupportActionBar(toolBarSignedDocs)

        signedDocsHistoric = intent.getStringExtra("SignedDocsHistoric") as String

        splitSDH = signedDocsHistoric.split(",").map { it.trim() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = AdapterSignedDocsHistoric(splitSDH, this)
    }

    override fun onItemClick(position: Int) {
        try {
            val item = (recyclerView.adapter as AdapterSignedDocsHistoric).itemList[position]
    //        Toast.makeText(this, "Pulsaste: $item", Toast.LENGTH_SHORT).show()
            val file = File(Environment.getExternalStoragePublicDirectory("VarSign"), item)

            if(file.exists()){
                val uri: Uri = FileProvider.getUriForFile(applicationContext, "com.rasamadev.varsign.provider", file)

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/pdf")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            }
            else{
                Utils.mostrarError(this, "No se ha encontrado el documento seleccionado en la carpeta 'VarSign'. Es posible que se haya eliminado o movido a otra carpeta")
            }
        } catch (e: ActivityNotFoundException) {
            // Manejar la excepción si no se encuentra una aplicación para abrir PDFs
            Toast.makeText(this, "No se ha encontrado ninguna aplicacion.", Toast.LENGTH_SHORT).show()
        }
    }

//    private fun getPaths() = dataStore.data.map { preferences ->
//        ModeloDatos(
//            path = preferences[stringPreferencesKey("paths")].orEmpty()
//        )
//    }
}