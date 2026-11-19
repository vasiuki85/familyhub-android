package com.familyhub.data.local.entity

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.model.TaskType

fun TaskTemplateEntity.toDomain(): TaskTemplate {
    val repeat = runCatching { RepeatRule.valueOf(repeatRule) }.getOrDefault(RepeatRule.NONE)
    val type = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.ROUTINE)
    return TaskTemplate(
        id = id,
        title = title,
        description = description,
        repeatRule = repeat,
        defaultAssigneeId = defaultAssigneeId,
        isPrivate = isPrivate,
        taskType = type,
        createdByUserId = createdByUserId,
        createdAt = createdAt,
        categoryId = categoryId,
        priority = TaskPriority.fromName(priority)
    )
}

fun TaskTemplate.toEntity(): TaskTemplateEntity {
    return TaskTemplateEntity(
        id = id,
        title = title,
        description = description,
        repeatRule = repeatRule.name,
        defaultAssigneeId = defaultAssigneeId,
        isPrivate = isPrivate,
        taskType = taskType.name,
        createdByUserId = createdByUserId,
        createdAt = createdAt,
        categoryId = categoryId,
        priority = priority.name
    )
}
