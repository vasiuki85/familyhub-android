package com.familyhub.domain.model

data class TaskTemplate(
    val id: String,
    val title: String,
    val description: String?,
    val repeatRule: RepeatRule,
    val defaultAssigneeId: String?,
    val isPrivate: Boolean,
    val taskType: TaskType,
    val createdByUserId: String,
    val createdAt: Long,
    val categoryId: String?,
    val priority: TaskPriority = TaskPriority.MEDIUM
)
