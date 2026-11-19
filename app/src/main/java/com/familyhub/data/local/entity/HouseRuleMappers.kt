package com.familyhub.data.local.entity

import com.familyhub.domain.model.HouseRule
import com.familyhub.util.fixMojibakeIfNeeded

fun HouseRuleEntity.toDomain(): HouseRule = HouseRule(
    id = id,
    title = title.fixMojibakeIfNeeded(),
    description = description?.fixMojibakeIfNeeded(),
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    category = category?.fixMojibakeIfNeeded()
)

fun HouseRule.toEntity(): HouseRuleEntity = HouseRuleEntity(
    id = id,
    title = title,
    description = description,
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    category = category
)
