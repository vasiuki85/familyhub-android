package com.familyhub.domain.model

data class UserAchievement(
    val userId: String,
    val type: AchievementType,
    val earnedAt: Long
)
