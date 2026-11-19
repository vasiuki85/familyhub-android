package com.familyhub.ui.navigation

import androidx.annotation.StringRes
import com.familyhub.R

internal data class MoreDestinationGroup(
    @StringRes val titleRes: Int,
    val routes: List<String>,
)

internal val moreDestinationGroups = listOf(
    MoreDestinationGroup(
        titleRes = R.string.nav_more_group_planning,
        routes = listOf(
            NavRoutes.CALENDAR,
            NavRoutes.MENU,
            NavRoutes.RECIPES,
            NavRoutes.GOALS,
        ),
    ),
    MoreDestinationGroup(
        titleRes = R.string.nav_more_group_motivation,
        routes = listOf(
            NavRoutes.RATING,
            NavRoutes.REWARDS,
            NavRoutes.PENALTIES,
            NavRoutes.AWESOMENESS,
        ),
    ),
    MoreDestinationGroup(
        titleRes = R.string.nav_more_group_family,
        routes = listOf(
            NavRoutes.RULES,
            NavRoutes.CONTACTS,
            NavRoutes.APPRECIATIONS,
            NavRoutes.STATS,
            NavRoutes.REPORT,
        ),
    ),
)

internal fun getGroupedMoreDestinationItems(
    barRoutes: List<String>,
): List<Pair<MoreDestinationGroup, List<BottomNavItem>>> {
    val overflowRoutes = getMoreDestinationItems(barRoutes).map { it.route }.toSet()
    return moreDestinationGroups.mapNotNull { group ->
        val items = group.routes.mapNotNull { route ->
            bottomNavItems.find { it.route == route && route in overflowRoutes }
        }
        if (items.isEmpty()) null else group to items
    }
}
