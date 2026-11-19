package com.familyhub.domain.model

data class Recipe(
    val id: String,
    val title: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val difficulty: RecipeDifficulty,
    val isFavorite: Boolean,
    val imageUrl: String?,
    val notes: String,
    val tags: List<String>,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val category: RecipeCategory,
    val createdAt: Long,
    val updatedAt: Long
)
