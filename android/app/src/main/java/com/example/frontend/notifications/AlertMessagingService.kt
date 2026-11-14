package com.example.frontend.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM alert messages while the app is in the foreground.
 *
 * Notification messages received in the background are displayed by the FCM
 * SDK using the `alerts` channel configured by [AlertNotifications].
 */
class AlertMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        AlertNotifications.ensureChannel(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: "Terrarium alert"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: "A configured threshold was crossed."

        AlertNotifications.show(
            context = this,
            title = title,
            body = body,
            data = message.data,
        )
    }

    override fun onNewToken(token: String) {
        // Tokens can rotate; keep the device subscribed to the alert topic.
        AlertNotifications.subscribeToAlerts()
    }
}
