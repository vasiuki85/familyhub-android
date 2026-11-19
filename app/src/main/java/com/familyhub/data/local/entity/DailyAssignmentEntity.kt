package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_assignments",
    indices = [Index(value = ["weekStartMillis", "dayOfWeek"], unique = true)]
)
data class DailyAssignmentEntity(
    @PrimaryKey val id: String,
    val weekStartMillis: Long,
    val dayOfWeek: Int,
    val assignedMemberId: String?,
    val rewardPoints: Int? = null,
    val isRated: Boolean = false,
    val updatedAt: Long = 0L
)
