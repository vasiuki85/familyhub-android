package com.familyhub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val difficulty: String,
    val isFavorite: Boolean,
    val imageUrl: String?,
    val notes: String,
    val tags: List<String>,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long
)
