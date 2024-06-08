package com.rasamadev.varsign.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rasamadev.varsign.R

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
        val textViewPerson: TextView = itemView.findViewById(R.id.docSignedTextPerson)
        val textViewMethod: TextView = itemView.findViewById(R.id.docSignedTextMethod)
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
        holder.textViewDate.text = "Fecha: ${itemList[position].substringAfter("?").substringBefore("<")}"
        holder.textViewPerson.text = "Firmado por: ${itemList[position].substringAfter(">")}"
        holder.textViewMethod.text = "Metodo de firma: ${itemList[position].substringAfter("<").substringBefore(">")}"

        holder.imageView.setImageResource(R.drawable.ic_document)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

