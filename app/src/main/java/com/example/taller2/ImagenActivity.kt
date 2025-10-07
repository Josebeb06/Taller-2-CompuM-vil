package com.example.taller2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityImagenBinding

class ImagenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagenBinding
    private var uriFoto: Uri? = null

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                binding.imageView.setImageURI(uri)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                binding.imageView.setImageBitmap(bitmap)
                Toast.makeText(this, "Foto tomada correctamente", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()

        binding.btnGaleria.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnCamara.setOnClickListener {
            cameraLauncher.launch(null)
        }
    }

    private fun checkPermissions() {
        val permisos = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val faltan = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltan.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltan.toTypedArray(), 100)
        }
    }
}
