package com.familyhub.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
/**
 * Базовый класс для всех NotificationManager, содержащий общую логику проверки разрешений.
 * Устраняет дублирование кода между TaskNotificationManager, EventNotificationManager и MealPlanNotificationManager.
 */
abstract class BaseNotificationManager(
    protected val context: Context
) {
    protected val notificationManager = NotificationManagerCompat.from(context)
    
    /**
     * Проверяет, есть ли у приложения разрешения на отправку уведомлений.
     * @return статус разрешений для уведомлений
     */
    protected fun checkNotificationPermissions(): NotificationPermissionStatus {
        if (!notificationManager.areNotificationsEnabled()) {
            return NotificationPermissionStatus.DISABLED
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return NotificationPermissionStatus.PERMISSION_REQUIRED
            }
        }
        
        return NotificationPermissionStatus.GRANTED
    }
}

/**
 * Статус разрешений на отправку уведомлений.
 */
enum class NotificationPermissionStatus {
    /** Разрешения есть, можно отправлять уведомления */
    GRANTED,
    /** Требуется разрешение POST_NOTIFICATIONS (Android 13+) */
    PERMISSION_REQUIRED,
    /** Уведомления отключены в настройках устройства */
    DISABLED
}

