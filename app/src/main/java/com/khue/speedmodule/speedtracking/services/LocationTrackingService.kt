package com.khue.speedmodule.speedtracking.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.khue.speedmodule.speedtracking.utils.notification.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class LocationTrackingService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var forceLocationManager: Boolean = false
    private lateinit var locationReceiver: BroadcastReceiver
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mLocationManager: LocationManager
    private var mFusedLocationCallback: LocationCallback? = null
    private var mLocationManagerCallback: LocationListener? = null
    private var isGoogleApiAvailable: Boolean = false

    companion object {
        private val TAG = LocationTrackingService::class.java.simpleName
        internal const val ACTION_BROADCAST = "speed.tracking.broadcast"
        internal const val EXTRA_LOCATION = "speed.tracking.location"
        var UPDATE_INTERVAL_IN_MILLISECONDS: Long = 500L
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val distanceFilter = intent?.getDoubleExtra("distance_filter", 0.0)
        if (intent != null) {
            forceLocationManager = intent.getBooleanExtra("force_location_manager", false)
        }
        if (distanceFilter != null) {
            createLocationRequest(distanceFilter)
        } else {
            createLocationRequest(0.0)
        }

        startLocationTrackingService()
        requestLocationUpdates()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        val googleAPIAvailability =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext)

        isGoogleApiAvailable = googleAPIAvailability == ConnectionResult.SUCCESS

        if (isGoogleApiAvailable && !this.forceLocationManager) {
            Log.d(TAG, "onCreate() - Google Play Services is available.")
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            mFusedLocationCallback = object : LocationCallback() {
                override fun onLocationResult(local: LocationResult) {
                    super.onLocationResult(local)
                    Log.i(TAG, "new location by FusedLocationProviderClient")
                    onNewLocation(local.lastLocation!!)
                }
            }
        } else {
            Log.d(TAG, "onCreate() - Google Play Services not available. Using LocationManager.")
            mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            mLocationManagerCallback = LocationListener { location ->
                Log.i(TAG, "new location by LocationManager")
                onNewLocation(location)
            }
        }

        locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (LocationManager.PROVIDERS_CHANGED_ACTION == intent!!.action) {
                    val isGpsEnabled =
                        mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    val isNetworkEnabled =
                        mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    Log.d(
                        "LocationSwitchStateReceiver",
                        "isGpsEnabled: ${isGpsEnabled || isNetworkEnabled}"
                    )

                    val isEnable = isGpsEnabled || isNetworkEnabled

                    // TODO: set notification to notify user GPS turn off
                }
            }
        }

        registerLocationReceiver()
    }

    private fun requestLocationUpdates() {
        try {
            if (isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.requestLocationUpdates(
                    mLocationRequest!!, mFusedLocationCallback!!, Looper.getMainLooper()
                )
            } else {
                val isGpsEnabled =
                    mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled =
                    mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                when {
                    isGpsEnabled -> mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 0L, 0f, mLocationManagerCallback!!
                    )
                    isNetworkEnabled -> mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0L, 0f, mLocationManagerCallback!!
                    )
                    else -> {
                        Log.d(TAG, "No location provider available")
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun registerLocationReceiver() {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        filter.addAction(Intent.ACTION_PROVIDER_CHANGED)
        registerReceiver(locationReceiver, filter)
    }

    private fun onNewLocation(location: Location) {
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun createLocationRequest(distanceFilter: Double) {
        mLocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_IN_MILLISECONDS
        )
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
            .setMinUpdateDistanceMeters(distanceFilter.toFloat())
            .build()
    }

    private fun startLocationTrackingService() {
        startForeground(NotificationUtil.notificationId, NotificationUtil.getNotificationBuilder(this).build())
    }

    private fun stopLocationTrackingService() {
        Log.i(TAG, "stopLocationTrackingService")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        scope.cancel()
        unregisterReceiver(locationReceiver)
        try {
            if (isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.removeLocationUpdates(mFusedLocationCallback!!)
            } else {
                mLocationManager.removeUpdates(mLocationManagerCallback!!)
            }
            NotificationUtil.cancelNotification()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // todo: call end session here
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved")
    }
}