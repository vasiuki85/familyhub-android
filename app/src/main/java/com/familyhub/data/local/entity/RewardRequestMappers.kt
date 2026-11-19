package com.familyhub.data.local.entity

import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.RewardRequestStatus

fun RewardRequestEntity.toDomain(): RewardRequest = RewardRequest(
    id = id,
    rewardId = rewardId,
    rewardTitle = rewardTitle,
    rewardCost = rewardCost,
    requesterUserId = requesterUserId,
    note = note,
    status = RewardRequestStatus.fromName(status),
    requestedAt = requestedAt,
    processedAt = processedAt,
    processedByUserId = processedByUserId
)

fun RewardRequest.toEntity(): RewardRequestEntity = RewardRequestEntity(
    id = id,
    rewardId = rewardId,
    rewardTitle = rewardTitle,
    rewardCost = rewardCost,
    requesterUserId = requesterUserId,
    note = note,
    status = status.name,
    requestedAt = requestedAt,
    processedAt = processedAt,
    processedByUserId = processedByUserId
)
