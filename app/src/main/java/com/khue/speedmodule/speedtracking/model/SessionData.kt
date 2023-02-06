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
        return "Speed: ${speed*3.6}\nMax speed: ${maxSpeed*3.6}\nTrip distance: $tripDistance\nTrip distanceF: $tripDistanceF\nAvg speedF: $avgSpeedF\nTime passed: $timePassed\nSignal level: $signalLevel"
    }
}