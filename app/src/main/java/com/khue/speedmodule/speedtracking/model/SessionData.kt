package com.khue.speedmodule.speedtracking.model

data class SessionData(
    val speed: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val tripDistance: Long = 0,
    val tripDistanceF: Float = 0f,
    val avgSpeedF: Float = 0f,
    val timePassed: Long = 0,
    val signalLevel: Int = 0,
    val isPaused: Boolean = false
) {
    override fun toString(): String {
        return "Speed: ${speed*3.6}\nMax speed: ${maxSpeed*3.6}\nTrip distance: $tripDistance\nTrip distanceF: $tripDistanceF\nAvg speedF: $avgSpeedF\nTime passed: $timePassed\nSignal level: $signalLevel"
    }
}