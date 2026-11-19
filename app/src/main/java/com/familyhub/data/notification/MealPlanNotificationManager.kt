package com.familyhub.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.ui.MainActivity
import com.familyhub.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "meal_plan"
private const val NOTIFICATION_ID = 3021
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

enum class MealPlanNotificationResult {
    SHOWN,
    NO_MENU_FOR_TODAY,
    PERMISSION_REQUIRED,
    NOTIFICATIONS_DISABLED
}

@Singleton
class MealPlanNotificationManager @Inject constructor(
    @ApplicationContext context: Context
) : BaseNotificationManager(context) {

    fun showTodayPlanNotification(
        menu: WeeklyMenu,
        recipes: Map<String, Recipe>,
        members: Map<String, FamilyMember>
    ): MealPlanNotificationResult {
        createNotificationChannel(context)
        
        // Проверка разрешений
        
        when (val permissionStatus = checkNotificationPermissions()) {
            NotificationPermissionStatus.DISABLED -> {
                cancelTodayNotification()
                return MealPlanNotificationResult.NOTIFICATIONS_DISABLED
            }
            NotificationPermissionStatus.PERMISSION_REQUIRED -> {
                return MealPlanNotificationResult.PERMISSION_REQUIRED
            }
            NotificationPermissionStatus.GRANTED -> { /* Разрешения есть, продолжаем */ }
        }
        val todayStart = TimeUtils.getStartOfDay(TimeUtils.now())
        val weekStart = WeekUtils.getWeekStartMillis(todayStart)
        if (menu.startDateMillis != weekStart) {
            cancelTodayNotification()
            return MealPlanNotificationResult.NO_MENU_FOR_TODAY
        }
        val dayOffset = ((todayStart - weekStart) / DAY_MILLIS).toInt()
        if (dayOffset !in 0..6) {
            cancelTodayNotification()
            return MealPlanNotificationResult.NO_MENU_FOR_TODAY
        }
        val entries = menu.entries.filter { it.dayOfWeek == dayOffset }
        if (entries.isEmpty()) {
            cancelTodayNotification()
            return MealPlanNotificationResult.NO_MENU_FOR_TODAY
        }

        val lines = entries.map { entry ->
            val mealLabel = context.getString(
                when (entry.mealType) {
                    MealType.BREAKFAST -> R.string.meal_breakfast
                    MealType.LUNCH -> R.string.meal_lunch
                    MealType.DINNER -> R.string.meal_dinner
                    MealType.SNACK -> R.string.meal_snack
                }
            )
            val dish = entry.recipeId?.let { recipes[it]?.title }
                ?: entry.customDish
                ?: context.getString(R.string.menu_share_no_meal)
            val assigned = entry.assignedMemberId?.let { id -> members[id]?.displayName }
            val base = context.getString(R.string.menu_notification_line_format, mealLabel, dish)
            if (assigned.isNullOrBlank()) {
                base
            } else {
                base + context.getString(R.string.menu_notification_assigned_suffix, assigned)
            }
        }

        val contentText = lines.joinToString(separator = "; ")
        val style = NotificationCompat.InboxStyle().also { inbox ->
            lines.forEach { inbox.addLine(it) }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_meal_plan)
            .setContentTitle(context.getString(R.string.menu_notification_title_today))
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return MealPlanNotificationResult.SHOWN
    }

    fun cancelTodayNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.menu_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.menu_notification_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
