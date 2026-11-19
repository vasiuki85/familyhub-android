package com.familyhub.data.local.entity

import com.familyhub.domain.model.TaskCategory
import com.familyhub.util.fixMojibakeIfNeeded

fun TaskCategoryEntity.toDomain(): TaskCategory = TaskCategory(
    id = id,
    name = name.fixMojibakeIfNeeded(),
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TaskCategory.toEntity(): TaskCategoryEntity = TaskCategoryEntity(
    id = id,
    name = name,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt
)
