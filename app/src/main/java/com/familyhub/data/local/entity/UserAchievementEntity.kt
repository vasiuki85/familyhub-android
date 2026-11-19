package com.familyhub.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "user_achievements",
    primaryKeys = ["userId", "type"]
)
data class UserAchievementEntity(
    val userId: String,
    val type: String,
    val earnedAt: Long
)
