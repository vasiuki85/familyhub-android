package com.familyhub.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.familyhub.data.work.TaskDeadlineCheckWorker
import com.familyhub.domain.model.Task
import com.familyhub.domain.utils.TaskReminderSchedule
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskNotificationScheduler @Inject constructor(
    private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val WORK_NAME_PERIODIC = "task_deadline_check"
        private const val WORK_NAME_TASK = "task_deadline_"
        private const val ACTION_TASK_DEADLINE = "com.familyhub.action.TASK_DEADLINE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_TASK_DUE_DATE = "task_due_date"
        private const val PERIODIC_INTERVAL_HOURS = 6L
        private const val TAG = "TaskNotificationScheduler"
    }

    fun scheduleTaskDeadlineNotification(task: Task, currentUserId: String) {
        val now = System.currentTimeMillis()
        if (!TaskDeadlineNotificationPolicy.shouldScheduleOneTimeNotification(
                task,
                currentUserId,
                now,
            )
        ) {
            cancelTaskDeadlineNotification(task.id)
            return
        }

        val fireAt = TaskReminderSchedule.resolveFireAtMillis(task)!!
        val pendingIntent = taskAlarmPendingIntent(task)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                fireAt,
                pendingIntent,
            )
        } else {
            Log.w(TAG, "Exact alarm permission is unavailable; using an inexact task reminder")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                fireAt,
                pendingIntent,
            )
        }
    }

    fun cancelTaskDeadlineNotification(taskId: String) {
        taskAlarmPendingIntent(taskId)?.let(alarmManager::cancel)
        // Cancel work created by older app versions as well.
        workManager.cancelUniqueWork(WORK_NAME_TASK + taskId)
    }

    private fun taskAlarmPendingIntent(task: Task): PendingIntent {
        val intent = taskAlarmIntent(task.id).apply {
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_DUE_DATE, task.dueDate!!)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun taskAlarmPendingIntent(taskId: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            taskAlarmIntent(taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun taskAlarmIntent(taskId: String): Intent =
        Intent(context, TaskDeadlineAlarmReceiver::class.java).apply {
            action = "$ACTION_TASK_DEADLINE.$taskId"
            putExtra(EXTRA_TASK_ID, taskId)
        }

    fun schedulePeriodicCheck() {
        val periodicRequest = PeriodicWorkRequestBuilder<TaskDeadlineCheckWorker>(
            PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
