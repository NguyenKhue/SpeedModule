package com.khue.speedmodule.speedtracking.services

import android.content.*
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.khue.speedmodule.speedtracking.model.SessionData
import com.khue.speedmodule.speedtracking.user_session.SpeedTrackingSession
import com.khue.speedmodule.speedtracking.utils.isServiceRunning
import com.khue.speedmodule.speedtracking.utils.notification.NotificationUtil
import com.khue.speedmodule.speedtracking.utils.runOnApiAboveAndElse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.*


internal class BackgroundLocationTrackingService(private val context: Context) {

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

    private var receiver: LocationReceiver = LocationReceiver()

    fun startLocationService(
        distanceFilter: Double = 0.0,
        forceLocationManager: Boolean = false
    ) {

        NotificationUtil.configureNormalNotification(
            "Speed Tracking",
            "Tracking your speed",
            "@mipmap/ic_launcher",
            context.packageName,
            context
        )

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(LocationTrackingService.ACTION_BROADCAST)
        )

        val intent = Intent(context, LocationTrackingService::class.java)
        intent.putExtra("distance_filter", distanceFilter)
        intent.putExtra("force_location_manager", forceLocationManager)

        if(isServiceRunning().not())
            runOnApiAboveAndElse(
                api = Build.VERSION_CODES.O,
                runOnApiHigher = {
                    context.startForegroundService(intent)
                    Log.i("BackgroundLocationService", "startForegroundService")
                },
                runOnApiLower = {
                    context.startService(intent)
                    Log.i("BackgroundLocationService", "startService")
                }
            )
    }

    private fun isServiceRunning(): Boolean {
        return context.isServiceRunning(LocationTrackingService::class.java)
    }

    fun stopLocationService() {
        Log.i("BackgroundLocationTrackingService", "stopLocationService")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        context.stopService(Intent(context, LocationTrackingService::class.java))
    }

    fun setConfiguration(timeInterval: Long?) {
        if (timeInterval != null) LocationTrackingService.UPDATE_INTERVAL_IN_MILLISECONDS =
            timeInterval
    }

    private inner class LocationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> intent.getParcelableExtra(
                    LocationTrackingService.EXTRA_LOCATION, Location::class.java)
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

                SpeedTrackingSession.onNewLocation(location)
            }
        }
    }
}

