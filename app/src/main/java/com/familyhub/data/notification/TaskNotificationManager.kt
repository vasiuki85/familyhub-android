package com.familyhub.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhub.R
import com.familyhub.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class TaskNotificationResult {
    SHOWN,
    NOTIFICATIONS_DISABLED,
    PERMISSION_REQUIRED
}

@Singleton
class TaskNotificationManager @Inject constructor(
    @ApplicationContext context: Context
) : BaseNotificationManager(context) {
    
    companion object {
        private const val CHANNEL_ID = "task_deadline_channel"
        private const val NOTIFICATION_ID_PREFIX = 1000
        private const val NOTIFICATION_ID_PREFIX_ROTATION = 1100
        
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.task_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.task_notification_channel_description)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        fun getNotificationId(taskId: String): Int {
            return NOTIFICATION_ID_PREFIX + (taskId.hashCode() and 0x7FFFFFFF)
        }
        
        fun getRotationNotificationId(taskTitle: String, assigneeName: String): Int {
            return NOTIFICATION_ID_PREFIX_ROTATION + ((taskTitle + assigneeName).hashCode() and 0x7FFFFFFF)
        }
    }
    
    init {
        createNotificationChannel(context)
    }
    
    fun showTaskDeadlineNotification(taskTitle: String, taskId: String, timeUntilDeadline: String): TaskNotificationResult {
        createNotificationChannel(context)
        
        // Проверка разрешений
        when (val permissionStatus = checkNotificationPermissions()) {
            NotificationPermissionStatus.DISABLED -> return TaskNotificationResult.NOTIFICATIONS_DISABLED
            NotificationPermissionStatus.PERMISSION_REQUIRED -> return TaskNotificationResult.PERMISSION_REQUIRED
            NotificationPermissionStatus.GRANTED -> { /* Разрешения есть, продолжаем */ }
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.task_notification_deadline_title))
            .setContentText(context.getString(R.string.task_notification_deadline_text, taskTitle, timeUntilDeadline))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(getNotificationId(taskId), notification)
        return TaskNotificationResult.SHOWN
    }

    fun showTaskRotationNotification(taskTitle: String, assigneeName: String): TaskNotificationResult {
        createNotificationChannel(context)
        
        // Проверка разрешений
        when (val permissionStatus = checkNotificationPermissions()) {
            NotificationPermissionStatus.DISABLED -> return TaskNotificationResult.NOTIFICATIONS_DISABLED
            NotificationPermissionStatus.PERMISSION_REQUIRED -> return TaskNotificationResult.PERMISSION_REQUIRED
            NotificationPermissionStatus.GRANTED -> { /* Разрешения есть, продолжаем */ }
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (taskTitle + assigneeName).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.task_rotation_notification_title))
            .setContentText(
                context.getString(
                    R.string.task_rotation_notification_body,
                    taskTitle,
                    assigneeName
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(getRotationNotificationId(taskTitle, assigneeName), notification)
        return TaskNotificationResult.SHOWN
    }

    fun showTaskAssignedNotification(taskTitle: String, taskId: String): TaskNotificationResult {
        createNotificationChannel(context)

        when (val permissionStatus = checkNotificationPermissions()) {
            NotificationPermissionStatus.DISABLED -> return TaskNotificationResult.NOTIFICATIONS_DISABLED
            NotificationPermissionStatus.PERMISSION_REQUIRED -> return TaskNotificationResult.PERMISSION_REQUIRED
            NotificationPermissionStatus.GRANTED -> { /* ok */ }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.task_notification_assigned_title))
            .setContentText(context.getString(R.string.task_notification_assigned_text, taskTitle))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(getNotificationId(taskId), notification)
        return TaskNotificationResult.SHOWN
    }
    
    fun cancelNotification(taskId: String) {
        notificationManager.cancel(getNotificationId(taskId))
    }
}
