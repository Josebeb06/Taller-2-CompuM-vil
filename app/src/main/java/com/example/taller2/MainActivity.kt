package com.example.taller2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taller2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContactos.setOnClickListener {
            startActivity(Intent(this, ContactosActivity::class.java))
        }

        binding.btnImagen.setOnClickListener {
            startActivity(Intent(this, ImagenActivity::class.java))
        }

        binding.btnSalir.setOnClickListener {
            finish()
        }
    }
}
