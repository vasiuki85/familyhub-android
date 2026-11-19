package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId"), Index(value = ["recipeId", "stepOrder"], unique = true)]
)
data class RecipeStepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val stepOrder: Int,
    val description: String
)
