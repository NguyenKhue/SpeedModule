package com.khue.speedmodule.speedtracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.*
import android.location.LocationListener
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.*

class SpeedService: Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var forceLocationManager: Boolean = false
    private lateinit var locationReceiver: BroadcastReceiver
    private val mBinder = LocalBinder()
    private var mNotificationManager: NotificationManager? = null
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationManager: LocationManager? = null
    private var mFusedLocationCallback: LocationCallback? = null
    private var mLocationManagerCallback: LocationListener? = null
    private var isGoogleApiAvailable: Boolean = false

    companion object {
        var NOTIFICATION_TITLE = "Location service"
        var NOTIFICATION_MESSAGE = "Location service is running"
        var NOTIFICATION_ICON = "@mipmap/ic_launcher"

        private const val PACKAGE_NAME = "com.khue.speedmodule"
        private val TAG = SpeedService::class.java.simpleName
        private const val CHANNEL_ID = "channel_01"
        internal const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        internal const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION =
            "$PACKAGE_NAME.started_from_notification"
        var UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000L
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
        private const val NOTIFICATION_ID = 12345678
    }

    private val notification: NotificationCompat.Builder
        @SuppressLint("UnspecifiedImmutableFlag")
        get() {

            val intent = Intent(this, getMainActivityClass(this))
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            intent.action = "Localisation"
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    1,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )


            val builder = NotificationCompat.Builder(this, "BackgroundLocation")
                .setContentTitle(NOTIFICATION_TITLE)
                .setOngoing(true)
                .setSound(null)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(resources.getIdentifier(NOTIFICATION_ICON, "mipmap", packageName))
                .setWhen(System.currentTimeMillis())
                .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_MESSAGE))
                .setContentIntent(pendingIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID)
            }

            return builder
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

        startSpeedService()
        requestLocationUpdates()

        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        val googleAPIAvailability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)

        isGoogleApiAvailable = googleAPIAvailability == ConnectionResult.SUCCESS

        if (isGoogleApiAvailable && !this.forceLocationManager) {
            Log.d(TAG, "onCreate() - Google Play Services is available.")
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            mFusedLocationCallback = object : LocationCallback() {
                override fun onLocationResult(local: LocationResult) {
                    super.onLocationResult(local)
                    Log.e(TAG, "new location")
                    onNewLocation(local.lastLocation!!)
                }
            }
        } else {
            Log.d(TAG, "onCreate() - Google Play Services not available. Using LocationManager.")
            mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager?

            mLocationManagerCallback = LocationListener { location ->
                Log.e(TAG, "new location 1")
                onNewLocation(location)
            }
        }

        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Application Name"
            val mChannel =
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            mChannel.setSound(null, null)
            mNotificationManager!!.createNotificationChannel(mChannel)
        }

        locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (LocationManager.PROVIDERS_CHANGED_ACTION == intent!!.action) {
                    val locationManager =
                        context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val isGpsEnabled =
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    val isNetworkEnabled =
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

    fun requestLocationUpdates() {
        try {
            if (isGoogleApiAvailable && !this.forceLocationManager) {
                mFusedLocationClient!!.requestLocationUpdates(
                    mLocationRequest!!,
                    mFusedLocationCallback!!,
                    Looper.getMainLooper()
                )
            } else {
                mLocationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    mLocationManagerCallback!!
                )
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
        val simpleDate = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val formatted = simpleDate.format(Date())
        var address: String? = null
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val list: List<Address> =
                if(Build.VERSION.SDK_INT >= 33) {
                    var locations = mutableListOf<Address>()
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object: Geocoder.GeocodeListener {
                        override fun onError(errorMessage: String?) {
                            super.onError(errorMessage)
                        }

                        override fun onGeocode(list: MutableList<Address>) {
                            locations = list
                        }
                    })
                    locations
                }
                // this method is deprecated in android 13
                else geocoder.getFromLocation(location.latitude, location.longitude, 1)?.toList() ?: emptyList()

            address = list[0].getAddressLine(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun createLocationRequest(distanceFilter: Double) {
        mLocationRequest = LocationRequest.create()
        mLocationRequest!!.apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = Priority.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = distanceFilter.toFloat()
        }
    }

    fun startSpeedService() {
        startForeground(NOTIFICATION_ID, notification.build())
    }

    fun stopSpeedService() {
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
                mLocationManager!!.removeUpdates(mLocationManagerCallback!!)
            }
            mNotificationManager!!.cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSpeedService()
        Log.i(TAG, "onTaskRemoved")
    }

    private fun getMainActivityClass(context: Context): Class<*>? {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val className = launchIntent?.component?.className ?: return null

        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    inner class LocalBinder : Binder() {
        internal val service: SpeedService
            get() = this@SpeedService
    }
}