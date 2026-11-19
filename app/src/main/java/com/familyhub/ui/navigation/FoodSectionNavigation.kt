package com.familyhub.ui.navigation

import androidx.navigation.NavController

enum class FoodSectionTab(val route: String) {
    WEEKLY_MENU(NavRoutes.MENU),
    RECIPES(NavRoutes.RECIPES),
    ;

    companion object {
        fun fromRoute(route: String?): FoodSectionTab? = entries.firstOrNull { it.route == route }
    }
}

fun NavController.navigateFoodSectionTab(
    target: FoodSectionTab,
    current: FoodSectionTab,
) {
    if (target == current) return
    navigate(target.route) {
        popUpTo(current.route) { inclusive = true }
        launchSingleTop = true
    }
}
