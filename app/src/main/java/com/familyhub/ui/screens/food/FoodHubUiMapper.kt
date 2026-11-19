package com.familyhub.ui.screens.food

import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.WeekUtils
import java.util.Calendar

data class FoodMealPreview(
    val mealType: MealType,
    val title: String,
)

data class FoodHubUiModel(
    val todayMeals: List<FoodMealPreview>,
    val recipePreviews: List<Recipe>,
)

object FoodHubUiMapper {
    private const val RECIPE_PREVIEW_LIMIT = 3

    fun map(
        menu: WeeklyMenu?,
        recipes: List<Recipe>,
        nowMillis: Long,
    ): FoodHubUiModel {
        val currentWeekStart = WeekUtils.getWeekStartMillis(nowMillis)
        val todayIndex = dayIndex(nowMillis)
        val recipeById = recipes.associateBy { it.id }
        val todayMeals = menu
            ?.takeIf { it.startDateMillis == currentWeekStart }
            ?.entries
            .orEmpty()
            .asSequence()
            .filter { it.dayOfWeek == todayIndex }
            .sortedBy(MenuEntry::mealType)
            .mapNotNull { entry ->
                val title = entry.recipeId
                    ?.let(recipeById::get)
                    ?.title
                    ?: entry.customDish?.takeIf(String::isNotBlank)
                title?.let { FoodMealPreview(entry.mealType, it) }
            }
            .toList()

        val recipePreviews = recipes
            .sortedWith(
                compareByDescending<Recipe> { it.isFavorite }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.title },
            )
            .take(RECIPE_PREVIEW_LIMIT)

        return FoodHubUiModel(
            todayMeals = todayMeals,
            recipePreviews = recipePreviews,
        )
    }

    private fun dayIndex(nowMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 6
            else -> dayOfWeek - Calendar.MONDAY
        }
    }
}
