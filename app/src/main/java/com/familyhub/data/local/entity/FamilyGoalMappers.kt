package com.familyhub.data.local.entity

import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.util.fixMojibakeIfNeeded

fun FamilyGoalEntity.toDomain(): FamilyGoal = FamilyGoal(
    id = id,
    title = title.fixMojibakeIfNeeded(),
    description = description?.fixMojibakeIfNeeded(),
    targetPoints = targetPoints,
    currentPoints = currentPoints,
    deadlineAt = deadlineAt,
    status = runCatching { FamilyGoalStatus.valueOf(status) }.getOrDefault(FamilyGoalStatus.ACTIVE),
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPersonal = personal
)

fun FamilyGoal.toEntity(): FamilyGoalEntity = FamilyGoalEntity(
    id = id,
    title = title,
    description = description,
    targetPoints = targetPoints,
    currentPoints = currentPoints,
    deadlineAt = deadlineAt,
    status = status.name,
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    personal = isPersonal
)
