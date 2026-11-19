package com.familyhub.ui.navigation

import com.familyhub.ui.screens.settings.SettingsInitialSyncAction

object NavRoutes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val SHOPPING = "shopping"
    const val FOOD = "food"
    const val CALENDAR = "calendar"
    const val RATING = "rating"
    const val SETTINGS = "settings"
    const val SETTINGS_ROUTE = "settings?joinFamilyId={joinFamilyId}&syncAction={syncAction}"
    const val SETTINGS_ARG_JOIN_FAMILY_ID = "joinFamilyId"
    const val SETTINGS_ARG_SYNC_ACTION = "syncAction"
    const val REWARDS = "rewards"
    const val RULES = "rules"
    const val APPRECIATIONS = "appreciations"
    const val REPORT = "report"
    const val STATS = "stats"
    const val GOALS = "goals"
    const val GOAL_DETAIL = "goal_detail"
    const val GOAL_DETAIL_ARG_GOAL_ID = "goalId"
    const val GOAL_DETAIL_ROUTE = "$GOAL_DETAIL/{$GOAL_DETAIL_ARG_GOAL_ID}"

    fun goalDetailDestination(goalId: String): String = "$GOAL_DETAIL/$goalId"
    const val RECIPES = "recipes"
    const val MENU = "menu"
    const val EDIT_PROFILE = "edit_profile"
    const val EDIT_PROFILE_ROUTE = "$EDIT_PROFILE/{memberId}"
    const val CALENDAR_SYNC = "calendar_sync"
    const val AWESOMENESS = "awesomeness"
    const val CONTACTS = "contacts"
    const val PENALTIES = "penalties"
}

fun NavRoutes.settingsDestination(
    syncAction: SettingsInitialSyncAction? = null,
    joinFamilyId: String? = null,
): String {
    val action = syncAction?.name
    return buildString {
        append(NavRoutes.SETTINGS)
        append("?")
        append("${NavRoutes.SETTINGS_ARG_JOIN_FAMILY_ID}=${joinFamilyId.orEmpty()}")
        append("&")
        append("${NavRoutes.SETTINGS_ARG_SYNC_ACTION}=${action.orEmpty()}")
    }
}
