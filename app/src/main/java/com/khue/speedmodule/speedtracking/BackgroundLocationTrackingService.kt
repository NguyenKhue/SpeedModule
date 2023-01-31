package com.khue.speedmodule.speedtracking

import android.content.*
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow


class BackgroundLocationTrackingService(private val context: Context) {

    companion object {
        const val BROADCAST_NAME = "location.background.tracking"
        const val LOCATION = "location"
        lateinit var instance: BackgroundLocationTrackingService

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


    private var bound: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            bound = true
            val binder = service as LocationTrackingService.LocalBinder
            this@BackgroundLocationTrackingService.service = binder.service
            Log.i("BackgroundLocationService", "onServiceConnected")
            requestLocation()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            bound = false
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            bound = false
        }
    }

    fun startLocationService(
        distanceFilter: Double = 0.0,
        forceLocationManager: Boolean = false
    ): Int {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(LocationTrackingService.ACTION_BROADCAST)
        )
        if (!bound) {
            val intent = Intent(context, LocationTrackingService::class.java)
            intent.putExtra("distance_filter", distanceFilter)
            intent.putExtra("force_location_manager", forceLocationManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                Log.i("BackgroundLocationService", "startForegroundService")
            } else {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.i("BackgroundLocationService", "bindService")
            }
        }

        return 0
    }

    fun stopLocationService(): Int {
        Log.i("BackgroundLocationTrackingService", "stopLocationService")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)

        if (bound) {
            bound = false
            context.unbindService(serviceConnection)
            service?.stopLocationTrackingService()
        } else {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
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
            val location =
                intent.getParcelableExtra<Location>(LocationTrackingService.EXTRA_LOCATION)
            if (location != null) {
                val locationMap = mutableMapOf<String, Any>()
                locationMap["latitude"] = location.latitude
                locationMap["longitude"] = location.longitude
                locationMap["altitude"] = location.altitude
                locationMap["accuracy"] = location.accuracy.toDouble()
                locationMap["bearing"] = location.bearing.toDouble()
                locationMap["speed"] = location.speed.toDouble()
                locationMap["time"] = location.time.toDouble()
                locationMap["is_mock"] =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else location.isFromMockProvider

//                val geocoder = Geocoder(context, Locale.getDefault())
//                val list: List<Address> =
//                    geocoder.getFromLocation(location.latitude, location.longitude, 1)

                //send location to activity
//                Log.i("Location:", list[0].getAddressLine(0))
                this@BackgroundLocationTrackingService.location.value = location
            }
        }
    }
}

