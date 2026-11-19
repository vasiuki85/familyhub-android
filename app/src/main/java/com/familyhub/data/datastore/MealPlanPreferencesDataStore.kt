package com.familyhub.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyhub.domain.model.MealPlanPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mealPlanDataStore: DataStore<Preferences> by preferencesDataStore(name = "meal_plan_preferences")

class MealPlanPreferencesDataStore(private val context: Context) {

    private val KEY_PREFER_VEGETARIAN = intPreferencesKey("prefer_vegetarian")
    private val KEY_EXCLUDED_TAGS = stringPreferencesKey("excluded_tags")
    private val KEY_MAX_PREP = intPreferencesKey("max_prep_time")
    private val KEY_BALANCE_DIFFICULTY = intPreferencesKey("balance_difficulty")
    private val KEY_REWARD_PROMPT_HOUR = intPreferencesKey("reward_prompt_hour")

    val preferencesFlow: Flow<MealPlanPreferences> =
        context.mealPlanDataStore.data.map { prefs ->
            MealPlanPreferences(
                preferVegetarian = (prefs[KEY_PREFER_VEGETARIAN] ?: 0) == 1,
                excludedTags = (prefs[KEY_EXCLUDED_TAGS] ?: "")
                    .split(',')
                    .mapNotNull { tag -> tag.trim().takeIf { it.isNotEmpty() } }
                    .toSet(),
                maxPrepTimeMinutes = prefs[KEY_MAX_PREP] ?: 90,
                balanceByDifficulty = (prefs[KEY_BALANCE_DIFFICULTY] ?: 1) == 1,
                rewardPromptHour = prefs[KEY_REWARD_PROMPT_HOUR] ?: 21
            )
        }

    suspend fun updatePreferences(update: MealPlanPreferences) {
        context.mealPlanDataStore.edit { prefs ->
            prefs[KEY_PREFER_VEGETARIAN] = if (update.preferVegetarian) 1 else 0
            prefs[KEY_EXCLUDED_TAGS] = update.excludedTags.joinToString(",")
            prefs[KEY_MAX_PREP] = update.maxPrepTimeMinutes
            prefs[KEY_BALANCE_DIFFICULTY] = if (update.balanceByDifficulty) 1 else 0
            prefs[KEY_REWARD_PROMPT_HOUR] = update.rewardPromptHour
        }
    }
}
