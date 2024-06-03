package com.rasamadev.varsign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdapterSignedDocsHistoric(
    val itemList: List<String>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<AdapterSignedDocsHistoric.MyViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val textViewName: TextView = itemView.findViewById(R.id.docSignedTextName)
        val textViewDate: TextView = itemView.findViewById(R.id.docSignedTextDate)
        val imageView: ImageView = itemView.findViewById(R.id.docSignedIcon)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_signed_docs_historic, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textViewName.text = itemList[position].substringBefore("?")
        holder.textViewDate.text = "Firmado: ${itemList[position].substringAfter("?")}"
        holder.imageView.setImageResource(R.drawable.ic_document)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

