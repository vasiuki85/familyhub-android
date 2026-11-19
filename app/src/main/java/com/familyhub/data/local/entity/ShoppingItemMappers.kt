package com.familyhub.data.local.entity

import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.model.ShoppingActivityType
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingItemUnit
import com.familyhub.domain.model.ShoppingList
import com.familyhub.util.fixMojibakeIfNeeded
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun ShoppingItemEntity.toDomain(): ShoppingItem = ShoppingItem(
    id = id,
    listId = listId,
    title = title.fixMojibakeIfNeeded(),
    createdByUserId = createdByUserId,
    isBought = isBought,
    createdAt = createdAt,
    categoryId = categoryId,
    quantity = quantity,
    quantityUnit = ShoppingItemUnit.fromId(quantityUnit),
    note = note?.fixMojibakeIfNeeded(),
    boughtAt = boughtAt
)

fun ShoppingItem.toEntity(): ShoppingItemEntity = ShoppingItemEntity(
    id = id,
    listId = listId,
    title = title,
    createdByUserId = createdByUserId,
    isBought = isBought,
    createdAt = createdAt,
    categoryId = categoryId,
    quantity = quantity,
    quantityUnit = quantityUnit?.id,
    note = note,
    boughtAt = boughtAt
)

fun ShoppingListEntity.toDomain(): ShoppingList = ShoppingList(
    id = id,
    title = title.fixMojibakeIfNeeded(),
    colorHex = colorHex,
    iconName = iconName,
    createdAt = createdAt,
    isArchived = isArchived,
    archivedAt = archivedAt,
    templateKey = templateKey,
    participantIds = participantIds
)

fun ShoppingList.toEntity(): ShoppingListEntity = ShoppingListEntity(
    id = id,
    title = title,
    colorHex = colorHex,
    iconName = iconName,
    createdAt = createdAt,
    isArchived = isArchived,
    archivedAt = archivedAt,
    templateKey = templateKey,
    participantIds = participantIds
)

fun ShoppingCategoryEntity.toDomain(): ShoppingCategory = ShoppingCategory(
    id = id,
    listId = listId,
    title = title.fixMojibakeIfNeeded(),
    colorHex = colorHex,
    iconName = iconName,
    sortOrder = sortOrder
)

fun ShoppingCategory.toEntity(): ShoppingCategoryEntity = ShoppingCategoryEntity(
    id = id,
    listId = listId,
    title = title,
    colorHex = colorHex,
    iconName = iconName,
    sortOrder = sortOrder
)

private val payloadJson = Json { encodeDefaults = true }

fun ShoppingActivityEntity.toDomain(): ShoppingActivity = ShoppingActivity(
    id = id,
    listId = listId,
    itemId = itemId,
    actorId = actorId,
    actionType = ShoppingActivityType.fromId(actionType),
    payload = payload?.let { runCatching { payloadJson.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
        ?: emptyMap(),
    occurredAt = occurredAt
)

fun ShoppingActivity.toEntity(): ShoppingActivityEntity = ShoppingActivityEntity(
    id = id,
    listId = listId,
    itemId = itemId,
    actorId = actorId,
    actionType = actionType.id,
    payload = if (payload.isEmpty()) null else payloadJson.encodeToString(payload),
    occurredAt = occurredAt
)

