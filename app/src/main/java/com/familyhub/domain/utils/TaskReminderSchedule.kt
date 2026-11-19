package com.familyhub.domain.utils

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.util.TimeUtils
import java.util.Calendar

object TaskReminderSchedule {
    const val DEFAULT_REMINDER_MINUTES_OF_DAY: Int = 8 * 60
    private const val ONE_HOUR_MILLIS: Long = 60 * 60 * 1000L
    private const val MINUTES_PER_DAY: Int = 24 * 60

    fun resolveFireAtMillis(task: Task): Long? {
        val dueDate = task.dueDate ?: return null
        return when (task.reminderMode) {
            TaskReminderMode.NONE -> null
            TaskReminderMode.AT_DUE -> if (task.dueDateHasTime) dueDate else null
            TaskReminderMode.ONE_HOUR_BEFORE ->
                if (task.dueDateHasTime) dueDate - ONE_HOUR_MILLIS else null
            TaskReminderMode.AT_TIME -> {
                val minutes = task.reminderMinutesOfDay ?: return null
                if (minutes !in 0 until MINUTES_PER_DAY) return null
                TimeUtils.getStartOfDay(dueDate) + minutes * 60_000L
            }
        }
    }

    fun normalizeAfterClearingTime(
        reminderMode: TaskReminderMode,
        reminderMinutesOfDay: Int?,
    ): Pair<TaskReminderMode, Int?> {
        return when (reminderMode) {
            TaskReminderMode.AT_DUE, TaskReminderMode.ONE_HOUR_BEFORE ->
                TaskReminderMode.NONE to null
            TaskReminderMode.AT_TIME -> reminderMode to reminderMinutesOfDay
            TaskReminderMode.NONE -> reminderMode to reminderMinutesOfDay
        }
    }

    fun normalizeAfterClearingDate(): Triple<Boolean, TaskReminderMode, Int?> =
        Triple(false, TaskReminderMode.NONE, null)

    fun defaultReminderForNewDueDate(): Pair<TaskReminderMode, Int> =
        TaskReminderMode.AT_TIME to DEFAULT_REMINDER_MINUTES_OF_DAY

    fun mergeDueTime(dueDateMillis: Long, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun dueTimeParts(dueDateMillis: Long): Pair<Int, Int> {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
    }
}
