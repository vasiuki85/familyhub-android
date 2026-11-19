package com.familyhub.data.local.entity

import com.familyhub.domain.model.DailyAssignment

fun DailyAssignmentEntity.toDomain(): DailyAssignment = DailyAssignment(
    id = id,
    weekStartMillis = weekStartMillis,
    dayOfWeek = dayOfWeek,
    assignedMemberId = assignedMemberId,
    rewardPoints = rewardPoints,
    isRated = isRated,
    updatedAt = updatedAt
)

fun DailyAssignment.toEntity(): DailyAssignmentEntity = DailyAssignmentEntity(
    id = id,
    weekStartMillis = weekStartMillis,
    dayOfWeek = dayOfWeek,
    assignedMemberId = assignedMemberId,
    rewardPoints = rewardPoints,
    isRated = isRated,
    updatedAt = updatedAt
)
