package com.rasamadev.varsign.utils

import android.app.AlertDialog
import android.content.Context

class Dialogs {
    companion object{
        /**
         * METODO QUE MUESTRA UN ALERTDIALOG DE ADVERTENCIA/ERROR
         * CON UN MENSAJE PERSONALIZADO
         */
        fun mostrarMensaje(context: Context, message: String) {
            val builder = AlertDialog.Builder(context)
            builder.setMessage(message)
            builder.setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }
    }
}