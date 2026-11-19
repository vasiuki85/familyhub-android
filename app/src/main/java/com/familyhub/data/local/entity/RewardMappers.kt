package com.familyhub.data.local.entity

import com.familyhub.domain.model.Reward

fun RewardEntity.toDomain(): Reward = Reward(
    id = id,
    title = title,
    description = description,
    cost = cost,
    createdByUserId = createdByUserId,
    isActive = isActive,
    createdAt = createdAt
)

fun Reward.toEntity(): RewardEntity = RewardEntity(
    id = id,
    title = title,
    description = description,
    cost = cost,
    createdByUserId = createdByUserId,
    isActive = isActive,
    createdAt = createdAt
)
