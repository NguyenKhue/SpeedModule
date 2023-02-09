package com.khue.speedmodule.speedtracking.utils.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.khue.speedmodule.R
import kotlin.random.Random

object NotificationUtil {
    val notificationId: Int = Random.nextInt(0, Int.MAX_VALUE)
    private var mNotificationManager: NotificationManager? = null
    private var notificationTile: String? = null
    private var notificationMessage: String? = null
    private var notificationIcon = "@mipmap/ic_launcher"
    private var packageName: String? = null
    private val channelId = "speed_tracking_channel_$notificationId"
    private val EXTRA_STARTED_FROM_NOTIFICATION =
        "$packageName.started_from_notification"
    private var notificationPendingIntent: PendingIntent? = null

    fun configureNormalNotification(
        notificationTile: String,
        notificationMessage: String,
        notificationIcon: String,
        packageName: String,
        context: Context
    ) {
        this.notificationTile = notificationTile
        this.notificationMessage = notificationMessage
        this.notificationIcon = notificationIcon
        this.packageName = packageName

        println("packageName: $packageName")
        createNotificationManager(context)
        notificationPendingIntent = createPendingIntent(context)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, getMainActivityClass(context))
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
        return PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun getNotificationBuilder(context: Context): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(notificationTile).setOngoing(true).setSound(null)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(context.resources.getIdentifier(notificationIcon, "mipmap", packageName))
            .setWhen(System.currentTimeMillis())
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setContentIntent(notificationPendingIntent)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(channelId)
        }

        return builder
    }

    private fun createNotificationManager(context: Context) {
        mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.app_name)
            val mChannel =
                NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
            mChannel.setSound(null, null)
            mNotificationManager!!.createNotificationChannel(mChannel)
        }
    }

    fun cancelNotification() {
        mNotificationManager?.cancel(notificationId)
    }

    fun updateNotification(message: String, context: Context) {
        notificationMessage = message
        mNotificationManager?.notify(notificationId!!, getNotificationBuilder(context).build())
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
}