package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskHistory
import com.familyhub.domain.model.TaskHistoryChangeType

fun TaskHistoryEntity.toDomain(): TaskHistory = TaskHistory(
    id = id,
    taskId = taskId,
    changedByUserId = changedByUserId,
    changeType = TaskHistoryChangeType.valueOf(changeType),
    changeDescription = changeDescription,
    oldValue = oldValue,
    newValue = newValue,
    timestamp = timestamp
)

fun TaskHistory.toEntity(): TaskHistoryEntity = TaskHistoryEntity(
    id = id,
    taskId = taskId,
    changedByUserId = changedByUserId,
    changeType = changeType.name,
    changeDescription = changeDescription,
    oldValue = oldValue,
    newValue = newValue,
    timestamp = timestamp
)

