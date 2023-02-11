package com.khue.speedmodule.speedtracking.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

inline fun runOnApiAbove(api: Int, f: () -> Unit) {
    if (Build.VERSION.SDK_INT >= api) {
        f()
    }
}

inline fun runOnApiAboveAndElse(api: Int, runOnApiHigher: () -> Unit, runOnApiLower: () -> Unit) {
    if (Build.VERSION.SDK_INT >= api) {
        runOnApiHigher()
    } else runOnApiLower()
}

@Suppress("DEPRECATION")
fun <T> Context.isServiceRunning(service: Class<T>): Boolean {
    return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == service.name }
}