package com.example.frontend.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.frontend.MainActivity
import com.example.frontend.R
import com.google.firebase.messaging.FirebaseMessaging

/** Shared FCM topic/channel setup and local notification rendering. */
object AlertNotifications {
    const val ALERT_TOPIC = "alerts"
    const val CHANNEL_ID = "alerts"
    const val CHANNEL_NAME = "Terrarium alerts"

    private const val TAG = "AlertNotifications"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Temperature and humidity threshold alerts"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun subscribeToAlerts() {
        FirebaseMessaging.getInstance()
            .subscribeToTopic(ALERT_TOPIC)
            .addOnFailureListener { error ->
                Log.w(TAG, "Could not subscribe to $ALERT_TOPIC", error)
            }
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        data: Map<String, String>,
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationKey = listOf(
            data["timestamp"].orEmpty(),
            data["type"].orEmpty(),
            data["condition"].orEmpty(),
        ).joinToString("-")

        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(
                notificationKey.hashCode(),
                notification,
            )
        } else {
            Log.w(TAG, "Notification permission is not granted")
        }
    }
}
