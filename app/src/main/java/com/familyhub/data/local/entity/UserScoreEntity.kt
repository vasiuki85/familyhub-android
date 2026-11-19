package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_scores",
    primaryKeys = ["userId", "weekStartDate"]
)
data class UserScoreEntity(
    val userId: String,
    val weekStartDate: Long,
    val score: Int,
    val updatedAt: Long = 0L
)

