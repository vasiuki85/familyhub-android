package com.familyhub.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.ui.theme.FhModulePalette
import com.familyhub.ui.theme.FhTheme

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoutes.HOME, FamilyHubNavIcons.Today, R.string.nav_redesign_today),
    BottomNavItem(NavRoutes.TASKS, FamilyHubNavIcons.Tasks, R.string.nav_label_tasks),
    BottomNavItem(NavRoutes.SHOPPING, FamilyHubNavIcons.Shopping, R.string.nav_label_shopping),
    BottomNavItem(NavRoutes.FOOD, FamilyHubNavIcons.Food, R.string.nav_redesign_food),
    BottomNavItem(NavRoutes.RECIPES, FamilyHubNavIcons.Recipes, R.string.nav_label_recipes),
    BottomNavItem(NavRoutes.MENU, FamilyHubNavIcons.Menu, R.string.nav_label_menu),
    BottomNavItem(NavRoutes.CALENDAR, FamilyHubNavIcons.Calendar, R.string.nav_label_calendar),
    BottomNavItem(NavRoutes.RATING, FamilyHubNavIcons.Rating, R.string.nav_label_rating),
    BottomNavItem(NavRoutes.REWARDS, FamilyHubNavIcons.Rewards, R.string.nav_label_rewards),
    BottomNavItem(NavRoutes.PENALTIES, FamilyHubNavIcons.Penalties, R.string.nav_label_penalties),
    BottomNavItem(NavRoutes.RULES, FamilyHubNavIcons.Rules, R.string.nav_label_rules),
    BottomNavItem(NavRoutes.GOALS, FamilyHubNavIcons.Goals, R.string.nav_label_goals),
    BottomNavItem(NavRoutes.STATS, FamilyHubNavIcons.Stats, R.string.nav_label_stats),
    BottomNavItem(NavRoutes.REPORT, FamilyHubNavIcons.Report, R.string.nav_label_report),
    BottomNavItem(NavRoutes.APPRECIATIONS, FamilyHubNavIcons.Appreciations, R.string.nav_label_appreciations),
    BottomNavItem(NavRoutes.AWESOMENESS, FamilyHubNavIcons.Awesomeness, R.string.nav_label_awesomeness),
    BottomNavItem(NavRoutes.CONTACTS, FamilyHubNavIcons.Contacts, R.string.nav_label_contacts),
    BottomNavItem(NavRoutes.SETTINGS, FamilyHubNavIcons.Settings, R.string.nav_label_settings)
)

val primaryBottomNavItems = listOf(
    NavRoutes.HOME,
    NavRoutes.TASKS,
    NavRoutes.SHOPPING,
    NavRoutes.FOOD,
).map { route -> bottomNavItems.first { it.route == route } }

/** Fixed last slot in the bottom bar; not a navigation destination and never persisted. */
object BottomNavMore {
    val icon: ImageVector = FamilyHubNavIcons.More
    @StringRes val labelRes: Int = R.string.nav_label_more
}

const val MIN_BOTTOM_NAV_ITEMS = 3
const val MAX_BOTTOM_NAV_ITEMS = 4

fun isBottomNavRouteActive(currentRoute: String?, itemRoute: String): Boolean {
    if (currentRoute == null) return false
    return if (itemRoute == NavRoutes.SETTINGS) {
        currentRoute == NavRoutes.SETTINGS || currentRoute.startsWith("${NavRoutes.SETTINGS}?")
    } else if (itemRoute == NavRoutes.FOOD) {
        currentRoute in setOf(NavRoutes.FOOD, NavRoutes.MENU, NavRoutes.RECIPES)
    } else if (itemRoute == NavRoutes.GOALS) {
        currentRoute in setOf(NavRoutes.GOALS, NavRoutes.GOAL_DETAIL)
    } else {
        currentRoute == itemRoute
    }
}

internal fun shouldPopToStartDestination(route: String, startRoute: String?): Boolean =
    startRoute != null && route == startRoute

fun NavController.navigateToBottomNavDestination(route: String) {
    val startDestinationId = graph.startDestinationId
    val startRoute = graph.findNode(startDestinationId)?.route
    if (shouldPopToStartDestination(route, startRoute)) {
        popBackStack(startDestinationId, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private val legacyDefaultBottomNavRoutes = setOf(
    listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.MENU, NavRoutes.CALENDAR, NavRoutes.SETTINGS),
    listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.SHOPPING, NavRoutes.RATING, NavRoutes.SETTINGS),
    listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.SHOPPING, NavRoutes.RATING),
)

fun migrateStoredBottomNavRoutes(storedRoutes: List<String>): List<String> =
    if (storedRoutes in legacyDefaultBottomNavRoutes) {
        primaryBottomNavItems.map { it.route }
    } else {
        storedRoutes
    }

fun normalizeBottomNavRoutes(selectedRoutes: List<String>): List<String> {
    val knownDistinctRoutes = migrateStoredBottomNavRoutes(selectedRoutes)
        .distinct()
        .mapNotNull { route -> bottomNavItems.find { it.route == route }?.route }

    return knownDistinctRoutes
        .filterNot { route ->
            NavRoutes.FOOD in knownDistinctRoutes &&
                route in setOf(NavRoutes.MENU, NavRoutes.RECIPES)
        }
        .take(MAX_BOTTOM_NAV_ITEMS)
}

fun resolveBottomNavRoutes(storedRoutes: List<String>): List<String> {
    val normalized = normalizeBottomNavRoutes(storedRoutes)
    return if (normalized.size in MIN_BOTTOM_NAV_ITEMS..MAX_BOTTOM_NAV_ITEMS) {
        normalized
    } else {
        primaryBottomNavItems.map { it.route }
    }
}

fun getActiveBottomNavItems(selectedRoutes: List<String>): List<BottomNavItem> {
    return resolveBottomNavRoutes(selectedRoutes)
        .map { route -> bottomNavItems.first { it.route == route } }
}

object BottomNavEditorSlotsMapper {
    fun map(storedRoutes: List<String>): List<String?> {
        val routes = resolveBottomNavRoutes(storedRoutes)
        return List(MAX_BOTTOM_NAV_ITEMS) { index -> routes.getOrNull(index) }
    }
}

@Composable
internal fun navigationPaletteForRoute(route: String): FhModulePalette = when (route) {
    NavRoutes.TASKS -> FhTheme.modules.task
    NavRoutes.SHOPPING -> FhTheme.modules.shopping
    NavRoutes.FOOD,
    NavRoutes.MENU,
    NavRoutes.RECIPES -> FhTheme.modules.food
    NavRoutes.RATING,
    NavRoutes.REWARDS,
    NavRoutes.AWESOMENESS -> FhTheme.modules.reward
    NavRoutes.GOALS,
    NavRoutes.GOAL_DETAIL -> FhTheme.modules.goals
    else -> FhModulePalette(
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
        container = MaterialTheme.colorScheme.primaryContainer,
        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
