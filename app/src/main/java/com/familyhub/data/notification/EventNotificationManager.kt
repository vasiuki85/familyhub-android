package com.familyhub.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhub.R
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.ui.MainActivity
import com.familyhub.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class EventNotificationResult {
    SHOWN,
    NOTIFICATIONS_DISABLED,
    PERMISSION_REQUIRED
}

@Singleton
class EventNotificationManager @Inject constructor(
    @ApplicationContext context: Context
) : BaseNotificationManager(context) {

    fun showEventReminder(event: FamilyEvent, occurrenceStart: Long): EventNotificationResult {
        createNotificationChannel(context)
        
        // Проверка разрешений
        when (val permissionStatus = checkNotificationPermissions()) {
            NotificationPermissionStatus.DISABLED -> return EventNotificationResult.NOTIFICATIONS_DISABLED
            NotificationPermissionStatus.PERMISSION_REQUIRED -> return EventNotificationResult.PERMISSION_REQUIRED
            NotificationPermissionStatus.GRANTED -> { /* Разрешения есть, продолжаем */ }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("event_id", event.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                context.getString(R.string.event_notification_title, event.title)
            )
            .setContentText(
                context.getString(
                    R.string.event_notification_text,
                    TimeUtils.formatDateTime(occurrenceStart)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(getNotificationId(event.id), notification)
        return EventNotificationResult.SHOWN
    }

    fun cancelNotification(eventId: String) {
        notificationManager.cancel(getNotificationId(eventId))
    }

    companion object {
        private const val CHANNEL_ID = "familyhub_event_reminders"
        private const val NOTIFICATION_ID_PREFIX = 2000

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.event_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.event_notification_channel_description)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        private fun getNotificationId(eventId: String): Int =
            NOTIFICATION_ID_PREFIX + (eventId.hashCode() and 0x7FFFFFFF)
    }
}
