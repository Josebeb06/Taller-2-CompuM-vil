package com.example.taller2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class ContactosAdapter(private val context: Context, private val lista: List<Contacto>) : BaseAdapter() {

    override fun getCount(): Int = lista.size
    override fun getItem(position: Int): Any = lista[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_contacto, parent, false)

        val contacto = lista[position]

        val imgContacto = view.findViewById<ImageView>(R.id.imgContacto)
        val txtNumero = view.findViewById<TextView>(R.id.txtNumero)
        val txtNombre = view.findViewById<TextView>(R.id.txtNombre)

        imgContacto.setImageResource(R.drawable.contacto)

        txtNumero.text = contacto.numeroLista.toString()

        txtNombre.text = contacto.nombre

        return view
    }
}
