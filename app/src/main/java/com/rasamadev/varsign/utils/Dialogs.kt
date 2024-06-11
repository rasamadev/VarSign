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

        /**
         * METODO QUE MUESTRA UN ALERTDIALOG DE ADVERTENCIA DE
         * QUE EL USUARIO TIENE QUE AÑADIR UN CAN ANTES DE PODER
         * FIRMAR CON DNI ELECTRONICO
         */
        fun dialogNoCans(context: Context){
            val builder = AlertDialog.Builder(context)
            builder.setTitle("¡AÑADA UN CAN PRIMERO!")
            builder.setMessage("Puede añadirlo desde el menu principal de la aplicacion pulsando en el boton 'AÑADIR CAN'")

            builder.setPositiveButton("Aceptar") { dialog, which ->
                dialog.dismiss()
            }

            builder.setCancelable(false)
            val dialog = builder.create()
            dialog.show()
        }
    }
}