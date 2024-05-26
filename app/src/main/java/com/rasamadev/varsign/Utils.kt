package com.rasamadev.varsign

import android.app.AlertDialog
import android.content.Context

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
    }
}