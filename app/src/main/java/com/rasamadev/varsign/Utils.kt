package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.Context
import com.itextpdf.text.exceptions.BadPasswordException
import com.itextpdf.text.pdf.PdfReader

import java.io.InputStream

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

        fun IsPasswordValid(ins: InputStream?, password: ByteArray): Boolean {
            return try {
                val pdfReader = PdfReader(ins, password);
                true
            } catch (e: BadPasswordException) {
                false
            }
        }
    }
}