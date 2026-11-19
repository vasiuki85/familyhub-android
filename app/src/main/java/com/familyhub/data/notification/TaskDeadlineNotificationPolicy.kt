package com.familyhub.data.notification

import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.utils.TaskReminderSchedule

object TaskDeadlineNotificationPolicy {
    fun shouldNotify(
        task: Task,
        currentUserId: String,
        now: Long,
        windowEnd: Long,
    ): Boolean {
        if (currentUserId.isBlank()) return false
        if (task.assignedToUserId != currentUserId) return false
        if (task.dueDate == null) return false
        if (task.status != TaskStatus.TODO) return false
        val fireAt = TaskReminderSchedule.resolveFireAtMillis(task) ?: return false
        return fireAt in now..windowEnd
    }

    fun shouldScheduleOneTimeNotification(
        task: Task,
        currentUserId: String,
        now: Long,
    ): Boolean {
        if (currentUserId.isBlank()) return false
        if (task.assignedToUserId != currentUserId) return false
        if (task.dueDate == null) return false
        if (task.status != TaskStatus.TODO) return false
        val fireAt = TaskReminderSchedule.resolveFireAtMillis(task) ?: return false
        return fireAt > now
    }
}
