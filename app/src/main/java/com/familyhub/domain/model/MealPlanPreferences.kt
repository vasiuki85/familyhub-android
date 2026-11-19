package com.familyhub.domain.model

data class MealPlanPreferences(
    val preferVegetarian: Boolean = false,
    val excludedTags: Set<String> = emptySet(),
    val maxPrepTimeMinutes: Int = 90,
    val balanceByDifficulty: Boolean = true,
    val rewardPromptHour: Int = 21
) {
    companion object {
        fun default() = MealPlanPreferences()
    }
}
