package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import com.itextpdf.text.exceptions.BadPasswordException
import com.itextpdf.text.pdf.PdfReader

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
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
import com.itextpdf.text.pdf.PdfDocument
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

class Utils {
    companion object {
        fun mostrarError(context: Context, message: String) {
            val builder = AlertDialog.Builder(context)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }

        fun isPasswordProtected(ins: InputStream?): Boolean {
            return try {
                val p = PdfReader(ins)
                false
            } catch (e: BadPasswordException) {
                true
            }
        }
    }
}