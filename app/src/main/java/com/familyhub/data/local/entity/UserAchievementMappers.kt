package com.familyhub.data.local.entity

import com.familyhub.domain.model.AchievementType
import com.familyhub.domain.model.UserAchievement

fun UserAchievementEntity.toDomain(): UserAchievement = UserAchievement(
    userId = userId,
    type = AchievementType.valueOf(type),
    earnedAt = earnedAt
)

fun UserAchievement.toEntity(): UserAchievementEntity = UserAchievementEntity(
    userId = userId,
    type = type.name,
    earnedAt = earnedAt
)
