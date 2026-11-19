package com.familyhub.data.local.entity

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.util.fixMojibakeIfNeeded

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title.fixMojibakeIfNeeded(),
    description = description?.fixMojibakeIfNeeded(),
    createdByUserId = createdByUserId,
    assignedToUserId = assignedToUserId,
    dueDate = dueDate,
    dueDateHasTime = dueDateHasTime,
    reminderMode = runCatching { TaskReminderMode.valueOf(reminderMode) }.getOrDefault(TaskReminderMode.NONE),
    reminderMinutesOfDay = reminderMinutesOfDay,
    repeatRule = RepeatRule.valueOf(repeatRule),
    status = TaskStatus.valueOf(status),
    createdAt = createdAt,
    orderIndex = orderIndex,
    isPrivate = isPrivate,
    taskType = TaskType.fromName(taskType),
    priority = TaskPriority.fromName(priority),
    rewardPoints = rewardPoints,
    categoryId = categoryId,
    attachments = attachments.map { it.toDomain() },
    comments = comments.map { it.toDomain() },
    requiresApproval = requiresApproval,
    completionCycle = completionCycle
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    createdByUserId = createdByUserId,
    assignedToUserId = assignedToUserId,
    dueDate = dueDate,
    dueDateHasTime = dueDateHasTime,
    reminderMode = reminderMode.name,
    reminderMinutesOfDay = reminderMinutesOfDay,
    repeatRule = repeatRule.name,
    status = status.name,
    createdAt = createdAt,
    orderIndex = orderIndex,
    isPrivate = isPrivate,
    taskType = taskType.name,
    priority = priority.name,
    rewardPoints = rewardPoints,
    categoryId = categoryId,
    attachments = attachments.map { it.toPayload() },
    comments = comments.map { it.toPayload() },
    requiresApproval = requiresApproval,
    completionCycle = completionCycle
)

