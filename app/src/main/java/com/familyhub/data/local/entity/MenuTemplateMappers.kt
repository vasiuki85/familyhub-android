package com.familyhub.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MealType

data class MenuTemplateWithEntries(
    @Embedded val template: MenuTemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "templateId"
    )
    val entries: List<MenuTemplateEntryEntity>
)

data class MenuTemplateEntities(
    val template: MenuTemplateEntity,
    val entries: List<MenuTemplateEntryEntity>
)

fun MenuTemplateWithEntries.toDomain(): MenuTemplate = MenuTemplate(
    id = template.id,
    name = template.name,
    createdAt = template.createdAt,
    updatedAt = template.updatedAt,
    entries = entries.map { entity ->
        MenuEntry(
            id = entity.id,
            menuId = entity.templateId,
            dayOfWeek = entity.dayOfWeek,
            mealType = MealType.valueOf(entity.mealType),
            recipeId = entity.recipeId,
            customDish = entity.customDish,
            servings = entity.servings,
            assignedMemberId = entity.assignedMemberId,
            cookingTaskId = entity.cookingTaskId,
            prepTaskId = entity.prepTaskId,
            updatedAt = entity.updatedAt
        )
    }
)

fun MenuTemplate.toEntities(): MenuTemplateEntities = MenuTemplateEntities(
    template = MenuTemplateEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    ),
    entries = entries.map { entry ->
        MenuTemplateEntryEntity(
            id = entry.id,
            templateId = entry.menuId,
            dayOfWeek = entry.dayOfWeek,
            mealType = entry.mealType.name,
            recipeId = entry.recipeId,
            customDish = entry.customDish,
            servings = entry.servings,
            assignedMemberId = entry.assignedMemberId,
            cookingTaskId = entry.cookingTaskId,
            prepTaskId = entry.prepTaskId,
            updatedAt = entry.updatedAt
        )
    }
)
