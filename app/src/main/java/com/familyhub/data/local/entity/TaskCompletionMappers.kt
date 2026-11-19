package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.model.TaskType

fun TaskCompletionEntity.toDomain(): TaskCompletion = TaskCompletion(
    id = id,
    taskId = taskId,
    userId = userId,
    taskType = TaskType.fromName(taskType),
    completionTime = completionTime,
    weekStartDate = weekStartDate,
    basePoints = basePoints,
    bonusPoints = bonusPoints,
    penaltyPoints = penaltyPoints,
    totalPoints = totalPoints,
    reversedAt = reversedAt,
    reversedByUserId = reversedByUserId,
    updatedAt = updatedAt,
    generatedRepeatTaskId = generatedRepeatTaskId,
    completionCycle = completionCycle
)

fun TaskCompletion.toEntity(): TaskCompletionEntity = TaskCompletionEntity(
    id = id,
    taskId = taskId,
    userId = userId,
    taskType = taskType.name,
    completionTime = completionTime,
    weekStartDate = weekStartDate,
    basePoints = basePoints,
    bonusPoints = bonusPoints,
    penaltyPoints = penaltyPoints,
    totalPoints = totalPoints,
    reversedAt = reversedAt,
    reversedByUserId = reversedByUserId,
    updatedAt = updatedAt,
    generatedRepeatTaskId = generatedRepeatTaskId,
    completionCycle = completionCycle
)
