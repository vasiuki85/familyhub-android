package com.familyhub.domain.model

data class TaskCompletion(
    val id: String,
    val taskId: String,
    val userId: String,
    val taskType: TaskType,
    val completionTime: Long,
    val weekStartDate: Long,
    val basePoints: Int,
    val bonusPoints: Int,
    val penaltyPoints: Int,
    val totalPoints: Int,
    val reversedAt: Long? = null,
    val reversedByUserId: String? = null,
    val updatedAt: Long = 0L,
    val generatedRepeatTaskId: String? = null,
    val completionCycle: Long? = null
) {
    val isActive: Boolean get() = reversedAt == null
}
