package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityMapaBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

class MapaActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {

    private lateinit var binding: ActivityMapaBinding
    private lateinit var gMap: GoogleMap

    // Ubicación
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null
    private var myLastLocation: Location? = null
    private var lastMarker: Marker? = null

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    // Demo override (para emulador): null = sin override; true = oscuro; false = claro
    private var demoOverrideDark: Boolean? = null

    // Archivo JSON interno
    private val logFileName = "locations_log.json"

    private val reqLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if ((fine || coarse) && ::gMap.isInitialized) {
            enableMyLocation()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Se requiere permiso de ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Volver
        binding.fabBack.setOnClickListener { finish() }

        // DEMO: dejar presionado para forzar modo oscuro/claro en el emulador
        binding.fabBack.setOnLongClickListener {
            demoOverrideDark = when (demoOverrideDark) {
                null -> true   // primera vez: forzar oscuro
                true -> false  // luego: forzar claro
                false -> null  // luego: quitar override (volver a sensor/tema)
            }
            applyMapStyle(resolveDarkMode())
            val msg = when (demoOverrideDark) {
                true -> "Modo oscuro FORZADO (demo)"
                false -> "Modo claro FORZADO (demo)"
                else -> "Override desactivado (sensor/tema del sistema)"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            true
        }

        // Clientes
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Sensor de luz (puede ser null en emulador)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Cargar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Buscar por texto (Geocoder directo al dar ENTER/botón de búsqueda)
        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) geocodeByText(query)
                true
            } else false
        }
    }

    override fun onMapReady(map: GoogleMap) {
        gMap = map
        gMap.uiSettings.isZoomControlsEnabled = true
        gMap.uiSettings.isCompassEnabled = true
        gMap.uiSettings.isMapToolbarEnabled = true

        // Punto inicial (por si aún no hay permisos)
        val bogota = LatLng(4.6526, -74.0942)
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 12f))

        // Long click: poner marcador con dirección + toast distancia
        gMap.setOnMapLongClickListener { latLng ->
            val address = reverseGeocode(latLng) ?: "Marcador"
            placeOrMoveMarker(latLng, address)
            toastDistanceTo(latLng)
        }

        // Aplicar estilo inicial según sensor/tema del sistema (o override demo)
        applyMapStyle(resolveDarkMode())

        enableMyLocation()
        startLocationUpdates()
    }

    /** ======= ESTILO (sensor / tema / demo) ======= */

    // Determina si debe usarse oscuro
    private fun resolveDarkMode(): Boolean {
        // 1) Si hay override de demo, úsalo
        demoOverrideDark?.let { return it }

        // 2) Si hay sensor y ya emitió evento, no decidimos aquí (onSensorChanged se encargará).
        //    Pero como fallback inicial usamos el tema del sistema:
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return isNight
    }

    private fun applyMapStyle(isDark: Boolean) {
        if (!::gMap.isInitialized) return
        if (isDark) {
            gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } else {
            gMap.setMapStyle(null) // claro por defecto
        }
    }

    /** ======= UBICACIÓN ======= */

    private fun enableMyLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            gMap.isMyLocationEnabled = true
        } else {
            reqLocationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!(fine || coarse)) return

        // Actualiza cada ~5s y solo si se movió ≥ 30m
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(30f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val previous = myLastLocation
                myLastLocation = loc

                // Log si se movió 30m+ (LocationRequest ya lo filtra; esto es respaldo)
                if (previous == null || previous.distanceTo(loc) >= 30f) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    saveLocationToJson(latLng)
                    placeOrMoveMarker(latLng, "Mi posición")
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback as LocationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    /** ======= JSON interno ======= */

    private fun saveLocationToJson(point: LatLng) {
        try {
            val file = File(filesDir, logFileName)
            val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()

            val now = System.currentTimeMillis()
            val obj = JSONObject().apply {
                put("lat", point.latitude)
                put("lng", point.longitude)
                put("timestamp", now)
            }
            arr.put(obj)
            file.writeText(arr.toString())
        } catch (e: IOException) {
            Toast.makeText(this, "Error guardando JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /** ======= MARCADORES ======= */

    private fun placeOrMoveMarker(position: LatLng, title: String?) {
        if (lastMarker == null) {
            lastMarker = gMap.addMarker(MarkerOptions().position(position).title(title ?: "Marcador"))
        } else {
            lastMarker!!.position = position
            lastMarker!!.title = title
        }
        lastMarker?.showInfoWindow()
    }

    /** ======= GEOCODER ======= */

    private fun geocodeByText(query: String) {
        try {
            val list = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)
            if (!list.isNullOrEmpty()) {
                val addr = list[0]
                val point = LatLng(addr.latitude, addr.longitude)
                val title = addr.featureName ?: query
                placeOrMoveMarker(point, title)
                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
                toastDistanceTo(point)
            } else {
                Toast.makeText(this, "No se encontró dirección", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Geocoder error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reverseGeocode(point: LatLng): String? {
        return try {
            val list = Geocoder(this, Locale.getDefault())
                .getFromLocation(point.latitude, point.longitude, 1)
            list?.firstOrNull()?.getAddressLine(0)
        } catch (_: Exception) { null }
    }

    /** ======= DISTANCIA ======= */

    private fun toastDistanceTo(target: LatLng) {
        val src = myLastLocation ?: return
        val results = FloatArray(1)
        Location.distanceBetween(src.latitude, src.longitude, target.latitude, target.longitude, results)
        val meters = results[0]
        val text = if (meters < 1000) {
            "${meters.roundToInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.2f km", meters / 1000f)
        }
        Toast.makeText(this, "Distancia a ese punto: $text", Toast.LENGTH_LONG).show()
    }

    /** ======= CICLO DE VIDA / SENSORES ======= */

    override fun onResume() {
        super.onResume()
        // Si hay sensor, registramos; si no, usamos tema del sistema (hasta que el usuario use el override demo)
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            applyMapStyle(resolveDarkMode())
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || !::gMap.isInitialized) return
        // Si el usuario activó override demo, NO tocar el estilo
        if (demoOverrideDark != null) return

        val lux = event.values.firstOrNull() ?: return
        val isDark = lux < 20f  // umbral simple
        applyMapStyle(isDark)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
