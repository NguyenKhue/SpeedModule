package com.khue.speedmodule.speedtracking

import android.content.*
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow


class BackgroundLocationService(private val context: Context) {

    companion object {
        const val BROADCAST_NAME = "location.background.tracking"
        const val LOCATION = "location"
        lateinit var instance: BackgroundLocationService

        @JvmStatic
        fun getInstance(context: Context): BackgroundLocationService {
            if (!Companion::instance.isInitialized) {
                instance = BackgroundLocationService(context)
            }
            return instance
        }
    }

    private var receiver: MyReceiver = MyReceiver()
    private var service: LocationUpdatesService? = null

    var location: MutableStateFlow<Location?> = MutableStateFlow(null)


    private var bound: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            bound = true
            val binder = service as LocationUpdatesService.LocalBinder
            this@BackgroundLocationService.service = binder.service
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
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
        if (!bound) {
            val intent = Intent(context, LocationUpdatesService::class.java)
            intent.putExtra("distance_filter", distanceFilter)
            intent.putExtra("force_location_manager", forceLocationManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
            //context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.i("BackgroundLocationService", "bindService")
        }

        return 0
    }

    fun stopLocationService(): Int {
        service?.removeLocationUpdates()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)

        if (bound) {
            bound = false
            context.unbindService(serviceConnection)
        }

        return 0
    }

    fun setAndroidNotification(title: String?, message: String?, icon: String?): Int {
        if (title != null) LocationUpdatesService.NOTIFICATION_TITLE = title
        if (message != null) LocationUpdatesService.NOTIFICATION_MESSAGE = message
        if (icon != null) LocationUpdatesService.NOTIFICATION_ICON = icon

        if (service != null) {
            service?.updateNotification()
        }

        return 0
    }

    fun setConfiguration(timeInterval: Long?): Int {
        if (timeInterval != null) LocationUpdatesService.UPDATE_INTERVAL_IN_MILLISECONDS =
            timeInterval

        return 0
    }

    /**
     * Requests a location updated.
     * If permission is denied, it requests the needed permission
     */
    private fun requestLocation() {
        //service?.requestLocationUpdates()
        service?.requestLocationUpdatesNotUseCallBack()
    }

    private inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location =
                intent.getParcelableExtra<Location>(LocationUpdatesService.EXTRA_LOCATION)
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
                this@BackgroundLocationService.location.value = location
            }
        }
    }
}

