package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DataWipeDao {
    @Query("DELETE FROM menu_template_entries")
    suspend fun clearMenuTemplateEntries()

    @Query("DELETE FROM menu_templates")
    suspend fun clearMenuTemplates()

    @Query("DELETE FROM menu_entries")
    suspend fun clearMenuEntries()

    @Query("DELETE FROM weekly_menus")
    suspend fun clearWeeklyMenus()

    @Query("DELETE FROM recipe_ingredients")
    suspend fun clearRecipeIngredients()

    @Query("DELETE FROM recipe_steps")
    suspend fun clearRecipeSteps()

    @Query("DELETE FROM recipes")
    suspend fun clearRecipes()

    @Query("DELETE FROM shopping_activity")
    suspend fun clearShoppingActivity()

    @Query("DELETE FROM shopping_items")
    suspend fun clearShoppingItems()

    @Query("DELETE FROM shopping_categories")
    suspend fun clearShoppingCategories()

    @Query("DELETE FROM shopping_lists")
    suspend fun clearShoppingLists()

    @Query("DELETE FROM task_history")
    suspend fun clearTaskHistory()

    @Query("DELETE FROM task_completions")
    suspend fun clearTaskCompletions()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM task_rotations")
    suspend fun clearTaskRotations()

    @Query("DELETE FROM task_templates")
    suspend fun clearTaskTemplates()

    @Query("DELETE FROM task_categories")
    suspend fun clearTaskCategories()

    @Query("DELETE FROM family_events")
    suspend fun clearFamilyEvents()

    @Query("DELETE FROM rewards")
    suspend fun clearRewards()

    @Query("DELETE FROM reward_requests")
    suspend fun clearRewardRequests()

    @Query("DELETE FROM user_scores")
    suspend fun clearUserScores()

    @Query("DELETE FROM user_achievements")
    suspend fun clearUserAchievements()

    @Query("DELETE FROM user_wallets")
    suspend fun clearUserWallets()

    @Query("DELETE FROM house_rule_blocks")
    suspend fun clearHouseRuleBlocks()

    @Query("DELETE FROM house_rules")
    suspend fun clearHouseRules()

    @Query("DELETE FROM appreciations")
    suspend fun clearAppreciations()

    @Query("DELETE FROM daily_assignments")
    suspend fun clearDailyAssignments()

    @Query("DELETE FROM emergency_contacts")
    suspend fun clearEmergencyContacts()

    @Query("DELETE FROM goal_steps")
    suspend fun clearGoalSteps()

    @Query("DELETE FROM goal_stages")
    suspend fun clearGoalStages()

    @Query("DELETE FROM family_goals")
    suspend fun clearFamilyGoals()

    @Query("DELETE FROM penalties")
    suspend fun clearPenalties()

    @Query("DELETE FROM family_members")
    suspend fun clearFamilyMembers()
}
