package com.familyhub.ui.navigation

/**
 * Keeps global navigation off authentication and detail-only destinations.
 *
 * A null route is treated as unresolved during NavHost hydration, so the bar does not flash
 * before the actual destination becomes authoritative.
 */
object BottomBarVisibilityPolicy {
    private val hiddenRoutes = setOf(
        NavRoutes.AUTH,
        NavRoutes.EDIT_PROFILE_ROUTE,
        NavRoutes.CALENDAR_SYNC,
    )

    fun shouldShow(currentRoute: String?): Boolean =
        currentRoute != null && currentRoute !in hiddenRoutes
}
