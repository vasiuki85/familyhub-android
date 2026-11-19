package com.familyhub.domain.model

data class MenuEntry(
    val id: String,
    val menuId: String,
    val dayOfWeek: Int,
    val mealType: MealType,
    val recipeId: String?,
    val customDish: String?,
    val servings: Int,
    val assignedMemberId: String?,
    val cookingTaskId: String?,
    val prepTaskId: String?,
    val updatedAt: Long = 0L
)
