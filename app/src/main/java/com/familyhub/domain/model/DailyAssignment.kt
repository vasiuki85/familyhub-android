package com.familyhub.domain.model

data class DailyAssignment(
    val id: String,
    val weekStartMillis: Long,
    val dayOfWeek: Int,
    val assignedMemberId: String?,
    val rewardPoints: Int? = null,
    val isRated: Boolean = false,
    val updatedAt: Long = 0L
)
