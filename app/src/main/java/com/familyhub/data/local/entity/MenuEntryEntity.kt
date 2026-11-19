package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "menu_entries",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyMenuEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuId"],
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
        Index("menuId"),
        Index("recipeId"),
        Index(value = ["menuId", "dayOfWeek", "mealType"], unique = true)
    ]
)
data class MenuEntryEntity(
    @PrimaryKey val id: String,
    val menuId: String,
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
