package com.familyhub.data.local.entity

import com.familyhub.domain.model.UserScore

fun UserScoreEntity.toDomain(): UserScore = UserScore(
    userId = userId,
    weekStartDate = weekStartDate,
    score = score,
    updatedAt = updatedAt
)

fun UserScore.toEntity(): UserScoreEntity = UserScoreEntity(
    userId = userId,
    weekStartDate = weekStartDate,
    score = score,
    updatedAt = updatedAt
)
