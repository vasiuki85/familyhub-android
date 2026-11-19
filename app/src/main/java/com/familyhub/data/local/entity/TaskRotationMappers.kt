package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskRotationConfig
import com.familyhub.domain.model.TaskRotationFrequency

fun TaskRotationEntity.toDomain(): TaskRotationConfig = TaskRotationConfig(
    taskId = taskId,
    memberIds = memberIds,
    currentIndex = currentIndex,
    frequency = TaskRotationFrequency.valueOf(frequency),
    startDateTime = startDateTime,
    nextRotationAt = nextRotationAt,
    lastRotationAt = lastRotationAt
)

fun TaskRotationConfig.toEntity(): TaskRotationEntity = TaskRotationEntity(
    taskId = taskId,
    memberIds = memberIds,
    currentIndex = currentIndex,
    frequency = frequency.name,
    startDateTime = startDateTime,
    nextRotationAt = nextRotationAt,
    lastRotationAt = lastRotationAt
)
