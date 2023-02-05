package com.khue.speedmodule.speedtracking.model

data class SessionData(
    val speed: Double,
    val maxSpeed: Double,
    val tripDistance: Long,
    val tripDistanceF: Float,
    val avgSpeedF: Float,
    val timePassed: Long,
    val signalLevel: Int,
) {
    override fun toString(): String {
        return "Speed: $speed\nMax speed: $maxSpeed\nTrip distance: $tripDistance\nTrip distanceF: $tripDistanceF\nAvg speedF: $avgSpeedF\nTime passed: $timePassed\nSignal level: $signalLevel"
    }
}