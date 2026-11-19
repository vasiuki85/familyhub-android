package com.familyhub.domain.model

data class RecipeStep(
    val id: String,
    val recipeId: String,
    val order: Int,
    val description: String
)
