package com.khue.speedmodule.speedtracking.user_session

import android.content.Context
import android.location.Location
import android.os.CountDownTimer
import com.khue.speedmodule.speedtracking.model.SessionData
import com.khue.speedmodule.speedtracking.services.BackgroundLocationTrackingService
import kotlin.math.*

object SpeedTrackingSession {

    private const val MAX_TIME_SESSION = 86400000L
    private const val COUNT_DOWN_DURATION = 1000L
    private const val EARTH_RADIUS = 6371000

    private var speed: Double = 0.0     // m/s
    private var maxSpeed: Double = 0.0  // m/s
    private var tripDistance: Long = 0  // in meters
    private var tripDistanceF = 0.0f // in km
    private var avgSpeedF = 0.0f // km/h
    private var timePassed: Long = 0 // in seconds
    private var currentPauseTimePassed: Long = 0
    private var totalPauseTimePassed: Long = 0
    private var signalLevel: Int = 0

    private var currentDistance: Long = 0
    private var isStarted = false
    private var isPaused = false
    private var curTime = 0.0
    private var oldLat = 0.0
    private var oldLong = 0.0
    private var shouldSkipAfterResume = false

    var sessionDataListener: ((SessionData, Boolean)->Unit)? = null
    var onStartSessionListener: (()->Unit)? = null
    var onStopSessionListener: (()->Unit)? = null
    var onPauseSessionListener: (()->Unit)? = null
    var onResumeSessionListener: (()->Unit)? = null

    private val sessionTimer = object : CountDownTimer(MAX_TIME_SESSION, COUNT_DOWN_DURATION) {
        override fun onTick(millisUntilFinished: Long) {
            timePassed = ((MAX_TIME_SESSION - millisUntilFinished) - totalPauseTimePassed)/1000
            if(!isPaused) sessionDataListener?.invoke(getSessionData(), isPaused)
        }

        override fun onFinish() {}
    }

    private val pauseTimer = object : CountDownTimer(MAX_TIME_SESSION, COUNT_DOWN_DURATION) {
        override fun onTick(millisUntilFinished: Long) {
            currentPauseTimePassed = (MAX_TIME_SESSION - millisUntilFinished)
        }

        override fun onFinish() {
        }
    }

    fun startSession(context: Context) {
        resetSession()
        isStarted = true
        sessionTimer.start()
        onStartSessionListener?.invoke()
        BackgroundLocationTrackingService.getInstance(context).startLocationService()
    }

    fun stopSession(context: Context) {
        isStarted = false
        sessionTimer.cancel()
        onStopSessionListener?.invoke()
        BackgroundLocationTrackingService.getInstance(context).stopLocationService()
    }

    fun pauseSession() {
        isPaused = true
        pauseTimer.start()
        onPauseSessionListener?.invoke()
    }

    fun resumeSession() {
        isPaused = false
        shouldSkipAfterResume = true
        totalPauseTimePassed += currentPauseTimePassed
        pauseTimer.cancel()
        onResumeSessionListener?.invoke()
    }

    private fun resetSession() {
        speed = 0.0
        maxSpeed = 0.0
        tripDistance = 0
        tripDistanceF = 0.0f
        avgSpeedF = 0.0f
        timePassed = 0
        currentPauseTimePassed = 0
        signalLevel = 0
        currentDistance = 0
        isStarted = false
        isPaused = false
        curTime = 0.0
        oldLat = 0.0
        oldLong = 0.0
    }

    private fun getSessionData(): SessionData {
        return SessionData(
            speed,
            maxSpeed,
            tripDistance,
            tripDistanceF,
            avgSpeedF,
            timePassed,
            signalLevel
        )
    }

    private fun initUserLocation(location: Location) {
        if(oldLat == 0.0 && oldLong == 0.0) {
            oldLat = location.latitude
            oldLong = location.longitude
        }
    }

    fun onNewLocation(location: Location) {
        initUserLocation(location)
        val latitude = location.latitude
        val longitude = location.longitude
        signalLevel = if (location.accuracy <= 4) {
            3
        } else if (location.accuracy <= 10) {
            2
        } else if (location.accuracy <= 50) {
            1
        } else {
            0
        }

        if (isStarted && !isPaused) {
            val newTime = System.currentTimeMillis().toDouble()
            if (location.hasSpeed()) {
                speed = location.speed.toDouble()
                updateMaxSpeed()
            } else {
                if(!shouldSkipAfterResume) {
                    val distance: Double =
                        calculateDistance(latitude, longitude, oldLat, oldLong).toDouble()
                    val timeDifferent = newTime - curTime
                    speed = distance / timeDifferent
                    updateMaxSpeed()
                }
                curTime = newTime
                shouldSkipAfterResume = false
            }

            currentDistance = calculateDistance(oldLat, oldLong, latitude, longitude)
            tripDistance += currentDistance
            tripDistanceF = (tripDistance / 1000).toFloat()
            avgSpeedF = (((tripDistance * 1f) / timePassed.coerceAtLeast(1)) * 3.6).toFloat()
        }

        oldLat = latitude
        oldLong = longitude
    }

    private fun updateMaxSpeed() {
        if (speed > maxSpeed) {
            maxSpeed = speed
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Long {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lng2 - lng1)
        val a = (sin(dLat / 2) * sin(dLat / 2)
                + (cos(Math.toRadians(lat1))
                * cos(Math.toRadians(lat2)) * sin(dLon / 2)
                * sin(dLon / 2)))
        val c = 2 * asin(sqrt(a))
        return (EARTH_RADIUS * c).roundToLong() // in meters
    }
}