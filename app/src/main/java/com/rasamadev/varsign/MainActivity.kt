package com.rasamadev.varsign

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.security.KeyChain
import android.security.KeyChainException
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity(), View.OnClickListener {

    /** Boton "Instalar certificado" */
    private lateinit var btnInstalarCertificado: Button

    /** Boton "Firmar documento(s)" */
    private lateinit var btnFirmarDocs: Button

    /** Configuracion biometrica */
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var executor: Executor
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private var option = 0

    private var inicioApp = true

    companion object {
        const val PICK_PFX_REQUEST_CODE = 123
        const val PICK_PDF_REQUEST_CODE = 456
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuracion de elementos del layout
        btnInstalarCertificado = findViewById<Button>(R.id.btnInstalarCertificado)
        btnFirmarDocs = findViewById<Button>(R.id.btnFirmarDocs)

        // Configurar los listeners de clic
        btnInstalarCertificado.setOnClickListener(this)
        btnFirmarDocs.setOnClickListener(this)

        // COMPROBAR SI EL DISPOSITIVO TIENE CONFIGURADO ALGUN PATRON
        // EN EL CASO DE QUE NO, INSISTIR?
        checkSecurityConfig()
    }

    override fun onResume() {
        super.onResume()
        if(inicioApp){
            inicioApp = false
        }
        else{
            checkSecurityConfig()
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.btnInstalarCertificado -> {
                option = 1
//                requestBiometricAuthentication()
            }
            R.id.btnFirmarDocs -> {
                option = 2
//                requestBiometricAuthentication()
            }
        }
        requestBiometricAuthentication()
    }

    private fun checkSecurityConfig() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isKeyguardSecure) {
            alertDialogSecurityConfig()
        }
    }

    private fun requestBiometricAuthentication() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if(errorCode != 10){
                        Toast.makeText(applicationContext,"ERROR: $errString (Codigo de error: $errorCode)", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    /** Instalar certificado (Solicitud de archivo .pfx) */
                    if(option == 1) {
                        openPfxPicker()
                    }
                    /** Mostrar dialog con opciones de firmar uno o varios documentos */
                    else if(option == 2){
                        // MOSTRAR MENU CON DOS APARTADOS DE FIRMAR UNO O VARIOS DOCUMENTOS
                        alertDialogDocOptions()
                    }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PFX_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedPfxUri = data?.data
            selectedPfxUri?.let {
                installPfxCertificate(selectedPfxUri)
            }
        }
//        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == RESULT_OK) {
//            val selectedPdfUri = data?.data
//            selectedPdfUri?.let {
//
//            }
//        }
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

    // -------------------------------- ALERTDIALOG´S --------------------------------

    private fun alertDialogDocOptions() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccione una opción")

        // Opciones de la lista
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
                    // Varios documentos
                    // SELECCIONAR CARPETA, RECORRER Y MOSTRAR EN LISTA DE CHECKS LOS ARCHIVOS A SELECCIONAR "GUARDAR RUTA"
                    // LLEVAR A PANTALLA DE SELECCION DE VARIOS DOCUMENTOS CON SELECCION DE ARCHIVO, RAZON, Y LOCALIDAD (CREAR)
                }
            }
            dialog.dismiss() // Cerrar el diálogo después de seleccionar una opción
        }

        builder.setPositiveButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun alertDialogSecurityConfig() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("ADVERTENCIA")
        builder.setMessage("Este dispositivo no cuenta con un metodo de seguridad configurada. Por favor, acceda a los ajustes para configurarlo.")

        builder.setPositiveButton("Ir a ajustes") { dialog, which ->
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
            } else {
                // Si no hay una actividad que pueda manejar el Intent, muestra un mensaje al usuario
                Toast.makeText(this, "No se pudo abrir la configuración de seguridad", Toast.LENGTH_SHORT).show()
            }
        }

//        // Configurar el botón negativo y su evento onClick
//        builder.setNegativeButton("Cancelar") { dialog, which ->
//            // Lógica al hacer clic en "Cancelar"
//            dialog.dismiss() // Cierra el diálogo
//        }
//
//        // Configurar el botón neutral y su evento onClick (opcional)
//        builder.setNeutralButton("Más tarde") { dialog, which ->
//            // Lógica al hacer clic en "Más tarde"
//            dialog.dismiss() // Cierra el diálogo
//        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
    }
}