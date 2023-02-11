package com.khue.speedmodule

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.khue.speedmodule.databinding.ActivityMainBinding
import com.khue.speedmodule.speedtracking.user_session.SpeedTrackingSession
import com.khue.speedmodule.speedtracking.utils.notification.NotificationUtil
import com.khue.speedmodule.speedtracking.utils.runOnApiAbove
import com.khue.speedmodule.speedtracking.utils.speed.*

class MainActivity : AppCompatActivity() {

    private val permissionId = 3
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.button.setOnClickListener {
            SpeedTrackingSession.stopSession(this)
        }
        binding.button1.setOnClickListener {
            SpeedTrackingSession.pauseSession()
        }
        binding.button2.setOnClickListener {
            SpeedTrackingSession.resumeSession()
        }
        startLocationTracking()
        observeLocation()
    }

    private fun observeLocation() {
        SpeedTrackingSession.sessionDataListener = { newSessionData, _ ->
            val message = "Speed: ${newSessionData.speed}" +
                    "\nMax speed: ${newSessionData.maxSpeed * 3.6} " +
                    "\nAVG speed: ${newSessionData.avgSpeedF}" +
                    "\nTime passed: ${newSessionData.timePassed}" +
                    "\nTrip distance: ${newSessionData.tripDistance}" +
                    "\nTrip distanceF: ${newSessionData.tripDistanceF}" +
                    "\nSignal level: ${newSessionData.signalLevel}"
            NotificationUtil.updateNotification(message, this)
            Log.i("MainActivity", "Location: $newSessionData")
            if (_binding != null) binding.textView.text = message
        }
    }

    private fun requestAccessBGLocationForAndroidQorHigher() {
        runOnApiAbove(Build.VERSION_CODES.Q) {
            if (!checkLocationAccessInBackground() && checkPermissions()) {
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

    private fun startLocationService() {
        SpeedTrackingSession.startSession(context = this)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            speedPermissions.toTypedArray(),
            permissionId
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestAccessBGLocation() {
        Log.i("requestAccessBGLocation", "requestAccessBGLocation")
        ActivityCompat.requestPermissions(
            this,
            optionalPermissionForAndroidQOrHigher.toTypedArray(),
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
                    if (!checkLocationAccessInBackground()) {
                        requestAccessBGLocation()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}