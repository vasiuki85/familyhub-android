package com.familyhub.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhub.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class ShoppingNotificationResult {
    SHOWN,
    PERMISSION_REQUIRED,
    NOTIFICATIONS_DISABLED
}

class ShoppingNotificationManager @Inject constructor(
    @ApplicationContext context: Context
) : BaseNotificationManager(context) {

    fun showActivityNotification(title: String, message: String): ShoppingNotificationResult {
        createNotificationChannel(context)
        return when (val status = checkNotificationPermissions()) {
            NotificationPermissionStatus.GRANTED -> {
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_shopping_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(title.hashCode(), notification)
                ShoppingNotificationResult.SHOWN
            }
            NotificationPermissionStatus.PERMISSION_REQUIRED -> ShoppingNotificationResult.PERMISSION_REQUIRED
            NotificationPermissionStatus.DISABLED -> ShoppingNotificationResult.NOTIFICATIONS_DISABLED
        }
    }

    companion object {
        private const val CHANNEL_ID = "shopping_activity_channel"
        private const val CHANNEL_NAME = "shopping_activity"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.shopping_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.shopping_notification_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
