package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.Context
import com.itextpdf.text.exceptions.BadPasswordException
import com.itextpdf.text.pdf.PdfReader

import java.io.InputStream

/**
 * CLASE QUE CONTIENE METODOS DE AYUDA QUE SE PUEDEN LLAMAR DIRECTAMENTE
 * DESDE EL NOMBRE DE LA CLASE, SIN INSTANCIAR UN OBJETO.
 * SON SIMILARES A LOS METODOS ESTATICOS DE JAVA
 */
class Utils {
    companion object {

        /**
         * METODO QUE MUESTRA UN ALERTDIALOG DE ADVERTENCIA/ERROR
         * CON UN MENSAJE PERSONALIZADO
         */
        fun mostrarError(context: Context, message: String) {
            val builder = AlertDialog.Builder(context)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }

        /**
         * METODO QUE COMPRUEBA SI UN DOCUMENTO PDF ESTA PROTEGIDO
         * POR CONTRASEÑA.
         *
         * @return true si tiene contraseña, false si no la tiene.
         */
        fun isPasswordProtected(ins: InputStream?): Boolean {
            return try {
                val p = PdfReader(ins)
                false
            } catch (e: BadPasswordException) {
                true
            }
        }

        /**
         * METODO QUE COMPRUEBA SI LA CONTRASEÑA INTRODUCIDA PARA
         * UN DOCUMENTO ES VALIDA.
         *
         * @return true si es correcta, false si es incorrecta
         */
        fun isPasswordValid(ins: InputStream?, password: ByteArray): Boolean {
            return try {
                val pdfReader = PdfReader(ins, password);
                true
            } catch (e: BadPasswordException) {
                false
            }
        }
    }
}