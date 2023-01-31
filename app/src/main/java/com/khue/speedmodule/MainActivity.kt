package com.khue.speedmodule

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.khue.speedmodule.databinding.ActivityMainBinding
import com.khue.speedmodule.speedtracking.BackgroundLocationTrackingService
import com.khue.speedmodule.speedtracking.LocationTrackingService

class MainActivity : AppCompatActivity() {

    private val permissionId = 3
    private lateinit var locationService: BackgroundLocationTrackingService
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationService = BackgroundLocationTrackingService.getInstance(this.applicationContext)
        binding.button.setOnClickListener {
            locationService.stopLocationService()
        }
        startLocationTracking()
        observeLocation()
    }

    private fun observeLocation() {
        lifecycleScope.launchWhenStarted {
            locationService.location.collect {
                Log.i("MainActivity", "Location: $it")
                binding.textView.text = "Latitude: ${it?.latitude}  \n Longitude: ${it?.longitude} \n speed: ${(it?.speed)?.let { speed -> speed * 3.6 }} km/h - ${(it?.speed)} m/s"
            }
        }
    }

    private fun requestAccessBGLocationForAndroidQorHigher() {
        runOnApiAbove(Build.VERSION_CODES.Q) {
            if (!checkLocationAccessBG() && checkPermissions()) {
                requestAccessBGLocation()
            }
        }
    }

    private fun startLocationTracking() {
        requestAccessBGLocationForAndroidQorHigher()
        if (checkPermissions() && isLocationEnabled()) {
            startLocationService()
        } else if (!checkPermissions()) {
            requestPermissions()
        } else if (!isLocationEnabled()) {
            Toast.makeText(this, "Please turn on location", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun startLocationService() {
        if (!application.isServiceRunning(LocationTrackingService::class.java)) {
            locationService.startLocationService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkLocationAccessBG(): Boolean {
        val isGranted =
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, "access bg $isGranted", Toast.LENGTH_LONG).show()
        return isGranted
    }

    private fun checkPermissions(): Boolean {

        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            permissionId
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestAccessBGLocation() {
        Log.i("requestAccessBGLocation", "requestAccessBGLocation")
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            permissionId
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionId) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startLocationService()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!checkLocationAccessBG()) {
                        requestAccessBGLocation()
                    }
                }
            }
        }
    }
}

inline fun runOnApiAbove(api: Int, f: () -> Unit) {
    if (Build.VERSION.SDK_INT > api) {
        f()
    }
}

@Suppress("DEPRECATION")
fun <T> Context.isServiceRunning(service: Class<T>): Boolean {
    return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it -> it.service.className == service.name }
}