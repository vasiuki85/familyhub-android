package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "menu_template_entries",
    foreignKeys = [
        ForeignKey(
            entity = MenuTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("templateId"),
        Index("recipeId"),
        Index(value = ["templateId", "dayOfWeek", "mealType"], unique = true)
    ]
)
data class MenuTemplateEntryEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val dayOfWeek: Int,
    val mealType: String,
    val recipeId: String?,
    val customDish: String?,
    val servings: Int,
    val assignedMemberId: String?,
    val cookingTaskId: String?,
    val prepTaskId: String?,
    val updatedAt: Long = 0L
)
