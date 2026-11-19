package com.familyhub.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.familyhub.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaskDeadlineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(TaskNotificationScheduler.EXTRA_TASK_ID)
            ?: return
        val taskTitle = intent.getStringExtra(TaskNotificationScheduler.EXTRA_TASK_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: return
        val dueDate = intent.getLongExtra(TaskNotificationScheduler.EXTRA_TASK_DUE_DATE, -1L)
            .takeIf { it > 0L }
            ?: return

        val now = System.currentTimeMillis()
        val timeUntil = getTimeUntilDeadline(context, dueDate, now)
        val result = TaskNotificationManager(context.applicationContext)
            .showTaskDeadlineNotification(taskTitle, taskId, timeUntil)
        when (result) {
            TaskNotificationResult.SHOWN -> Log.d(TAG, "Task deadline notification delivered")
            TaskNotificationResult.PERMISSION_REQUIRED ->
                Log.w(TAG, "Task deadline notification permission is missing")
            TaskNotificationResult.NOTIFICATIONS_DISABLED ->
                Log.w(TAG, "Task deadline notifications are disabled")
        }
    }

    private fun getTimeUntilDeadline(context: Context, dueDate: Long, now: Long): String {
        val diff = dueDate - now
        val hours = diff / TimeUnit.HOURS.toMillis(1)
        return when {
            hours < 1 -> context.getString(R.string.task_deadline_less_than_hour)
            hours < 24 -> context.getString(R.string.task_deadline_hours, hours.toInt())
            else -> DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault(),
            ).format(Date(dueDate))
        }
    }

    private companion object {
        const val TAG = "TaskDeadlineAlarmReceiver"
    }
}
