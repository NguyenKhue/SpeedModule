package com.khue.speedmodule.speedtracking.user_session

import android.location.Location
import android.os.CountDownTimer
import com.khue.speedmodule.speedtracking.model.SessionData
import kotlin.math.*

object SpeedTrackingSession {

    private var speed: Double = 0.0
    private var maxSpeed: Double = 0.0
    private var tripDistance: Long = 0
    private var tripDistanceF = 0.0f
    private var avgSpeedF = 0.0f
    private var timePassed: Long = 0
    private var pauseTimePassed: Long = 0
    private var signalLevel: Int = 0

    private var currentDistance: Long = 0
    private var isStarted = false
    private var isPaused = false
    private const val maxTime: Long = 43200000
    private var curTime = 0.0
    private var oldLat = 0.0
    private var oldLong = 0.0
    private var shouldSkipAfterResume = false
    var sessionDataListener: ((SessionData)->Unit)? = null

    private val sessionTimer = object : CountDownTimer(43200000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            timePassed = (maxTime - millisUntilFinished) - pauseTimePassed
            sessionDataListener?.invoke(getSessionData())
        }

        override fun onFinish() {}
    }

    private val pauseTimer = object : CountDownTimer(43200000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            pauseTimePassed += (maxTime - millisUntilFinished)
        }

        override fun onFinish() {}
    }


    fun startSession(onStartSession: () -> Unit = {}) {
        resetSession()
        isStarted = true
        sessionTimer.start()
    }

    fun stopSession(onStopSession: () -> Unit = {}) {
        isStarted = false
        sessionTimer.cancel()
    }

    fun pauseSession(onPauseSession: () -> Unit = {}) {
        isPaused = true
        pauseTimer.start()
    }

    fun resumeSession(onResumeSession: () -> Unit = {}) {
        isPaused = false
        shouldSkipAfterResume = true
        pauseTimer.cancel()
    }

    private fun resetSession() {
        speed = 0.0
        maxSpeed = 0.0
        tripDistance = 0
        tripDistanceF = 0.0f
        avgSpeedF = 0.0f
        timePassed = 0
        pauseTimePassed = 0
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

    fun onNewLocation(location: Location) {
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
            tripDistanceF = (tripDistance / 1000000).toFloat()
            avgSpeedF = ((tripDistance / timePassed) * 36 / 10000).toFloat()
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
        return (6371000 * c).roundToLong()
    }
}