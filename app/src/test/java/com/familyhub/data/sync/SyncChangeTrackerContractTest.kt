package com.familyhub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncChangeTrackerContractTest {

    @Test
    fun syncTrackedTables_matchesFh207Contract() {
        val expected = setOf(
            "family_members",
            "tasks",
            "shopping_lists",
            "shopping_categories",
            "shopping_items",
            "shopping_activity",
            "family_events",
            "task_completions",
            "user_achievements",
            "rewards",
            "reward_requests",
            "user_wallets",
            "task_rotations",
            "user_scores",
            "house_rules",
            "task_templates",
            "task_categories",
            "appreciations",
            "family_goals",
            "goal_stages",
            "goal_steps",
            "emergency_contacts",
            "penalties",
            "recipes",
            "recipe_ingredients",
            "recipe_steps",
            "weekly_menus",
            "menu_entries",
            "menu_templates",
            "menu_template_entries",
            "daily_assignments",
        )
        assertEquals(expected, SYNC_TRACKED_TABLES)
        assertEquals(31, SYNC_TRACKED_TABLES.size)
    }
}
