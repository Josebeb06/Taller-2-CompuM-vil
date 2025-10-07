package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityContactosBinding

class ContactosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactosBinding
    private val listaContactos = mutableListOf<Contacto>()
    private val REQUEST_CONTACT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACT)
        } else {
            cargarContactos()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACT && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarContactos()
        }
    }

    private fun cargarContactos() {
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        var contador = 1
        cursor?.use {
            while (it.moveToNext()) {
                val nombre = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                listaContactos.add(Contacto(contador, nombre))
                contador++
            }
        }

        if (listaContactos.isEmpty()) {
            // si no hay contactos
            listaContactos.add(Contacto(0, "Sin contactos guardados"))
        }

        val adapter = ContactosAdapter(this, listaContactos)
        binding.listViewContactos.adapter = adapter
    }
}
