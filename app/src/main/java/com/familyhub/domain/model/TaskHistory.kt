package com.familyhub.domain.model

/**
 * История изменений задачи
 */
data class TaskHistory(
    val id: String,
    val taskId: String,
    val changedByUserId: String,
    val changeType: TaskHistoryChangeType,
    val changeDescription: String,
    val oldValue: String?,
    val newValue: String?,
    val timestamp: Long
)

enum class TaskHistoryChangeType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    DELETED,
    ASSIGNED,
    PRIVACY_CHANGED,
    ROTATED
}

