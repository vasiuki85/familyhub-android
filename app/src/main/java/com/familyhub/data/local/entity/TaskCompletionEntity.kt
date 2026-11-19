package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_completions",
    indices = [
        Index(value = ["taskId", "completionCycle"], unique = true)
    ]
)
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val userId: String,
    val taskType: String,
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
)
