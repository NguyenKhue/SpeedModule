package com.khue.speedmodule.speedtracking

import android.content.*
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*


class BackgroundLocationTrackingService(private val context: Context) {

    companion object {
        private lateinit var instance: BackgroundLocationTrackingService

        @JvmStatic
        fun getInstance(context: Context): BackgroundLocationTrackingService {
            if (!Companion::instance.isInitialized) {
                instance = BackgroundLocationTrackingService(context)
            }
            return instance
        }
    }

    private var receiver: MyReceiver = MyReceiver()
    private var service: LocationTrackingService? = null

    var location: MutableStateFlow<Location?> = MutableStateFlow(null)

    fun startLocationService(
        distanceFilter: Double = 0.0,
        forceLocationManager: Boolean = false
    ): Int {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(LocationTrackingService.ACTION_BROADCAST)
        )
        val intent = Intent(context, LocationTrackingService::class.java)
        intent.putExtra("distance_filter", distanceFilter)
        intent.putExtra("force_location_manager", forceLocationManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
            Log.i("BackgroundLocationService", "startForegroundService")
        } else {
            context.startService(intent)
            Log.i("BackgroundLocationService", "startService")
        }

        return 0
    }

    fun stopLocationService(): Int {
        Log.i("BackgroundLocationTrackingService", "stopLocationService")

        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)

        context.stopService(Intent(context, LocationTrackingService::class.java))

        return 0
    }

    fun setAndroidNotification(title: String?, message: String?, icon: String?): Int {
        if (title != null) LocationTrackingService.NOTIFICATION_TITLE = title
        if (message != null) LocationTrackingService.NOTIFICATION_MESSAGE = message
        if (icon != null) LocationTrackingService.NOTIFICATION_ICON = icon

        service?.startLocationTrackingService()

        return 0
    }

    fun setConfiguration(timeInterval: Long?): Int {
        if (timeInterval != null) LocationTrackingService.UPDATE_INTERVAL_IN_MILLISECONDS =
            timeInterval

        return 0
    }

    /**
     * Requests a location updated.
     * If permission is denied, it requests the needed permission
     */
    private fun requestLocation() {
        service?.startLocationTrackingService()
    }

    private inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> intent.getParcelableExtra(LocationTrackingService.EXTRA_LOCATION, Location::class.java)
                else -> @Suppress("DEPRECATION") intent.getParcelableExtra(LocationTrackingService.EXTRA_LOCATION)
            }

            if (location != null) {
                val locationMap = mutableMapOf<String, Any>()
                locationMap["latitude"] = location.latitude
                locationMap["longitude"] = location.longitude
                locationMap["altitude"] = location.altitude
                locationMap["accuracy"] = location.accuracy.toDouble()
                locationMap["bearing"] = location.bearing.toDouble()
                locationMap["speed"] = location.speed.toDouble()
                locationMap["time"] = location.time.toDouble()
                locationMap["is_mock"] = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> location.isMock
                    else -> @Suppress("DEPRECATION") location.isFromMockProvider
                }
                this@BackgroundLocationTrackingService.location.value = location
            }
        }
    }
}

