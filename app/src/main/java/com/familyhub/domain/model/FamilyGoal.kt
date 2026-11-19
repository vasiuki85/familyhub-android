package com.familyhub.domain.model

data class FamilyGoal(
    val id: String,
    val title: String,
    val description: String?,
    val targetPoints: Int,
    val currentPoints: Int,
    val deadlineAt: Long?,
    val status: FamilyGoalStatus,
    val createdByUserId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPersonal: Boolean = false
) {
    val isCompleted: Boolean
        get() = status == FamilyGoalStatus.COMPLETED

    val progress: Float
        get() = if (targetPoints <= 0) {
            0f
        } else {
            (currentPoints.coerceAtLeast(0).coerceAtMost(targetPoints).toFloat() / targetPoints.toFloat())
        }
}
