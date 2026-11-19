package com.familyhub.domain.model

data class RecipeIngredient(
    val id: String,
    val recipeId: String,
    val name: String,
    val quantity: String
)
