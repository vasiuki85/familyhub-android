package com.familyhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.familyhub.ui.screens.home.HomeScreen
import com.familyhub.ui.screens.rating.RatingScreen
import com.familyhub.ui.screens.rewards.RewardsScreen
import com.familyhub.ui.screens.settings.SettingsInitialSyncAction
import com.familyhub.ui.screens.settings.SettingsScreen
import com.familyhub.ui.screens.shopping.ShoppingScreen
import com.familyhub.ui.screens.tasks.TasksScreen
import com.familyhub.ui.screens.calendar.CalendarScreen
import com.familyhub.ui.screens.rules.RulesScreen
import com.familyhub.ui.screens.report.WeeklyReportScreen
import com.familyhub.ui.screens.appreciations.AppreciationsScreen
import com.familyhub.ui.screens.stats.MemberStatisticsScreen
import com.familyhub.ui.screens.goals.FamilyGoalsScreen
import com.familyhub.ui.screens.goals.GoalDetailScreen
import com.familyhub.ui.screens.recipes.RecipeBookScreen
import com.familyhub.ui.screens.menu.WeeklyMenuScreen
import com.familyhub.ui.screens.profile.EditProfileScreen
import com.familyhub.ui.screens.calendar.CalendarSyncScreen
import com.familyhub.ui.screens.awesomeness.AwesomenessScreen
import com.familyhub.ui.screens.contacts.ContactsScreen
import com.familyhub.ui.screens.food.FoodScreen
import com.familyhub.ui.screens.penalties.PenaltiesScreen
import com.familyhub.ui.navigation.FoodSectionTab
import com.familyhub.ui.navigation.navigateFoodSectionTab

import com.familyhub.ui.screens.auth.AuthScreen

@Composable
fun FamilyHubNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { PeerTabEnterTransition },
        exitTransition = { PeerTabExitTransition },
        popEnterTransition = { PeerTabEnterTransition },
        popExitTransition = { PeerTabExitTransition },
    ) {
        composable(NavRoutes.AUTH) { 
            AuthScreen(navController = navController) 
        }
        composable(NavRoutes.HOME) { 
            HomeScreen(navController = navController) 
        }
        composable(NavRoutes.TASKS) { TasksScreen() }
        composable(NavRoutes.SHOPPING) { ShoppingScreen() }
        composable(NavRoutes.FOOD) {
            FoodScreen(
                onOpenWeeklyMenu = {
                    navController.navigate(NavRoutes.MENU) { launchSingleTop = true }
                },
                onOpenRecipes = {
                    navController.navigate(NavRoutes.RECIPES) { launchSingleTop = true }
                },
            )
        }
        composable(NavRoutes.RECIPES) {
            RecipeBookScreen(
                onNavigateToWeeklyMenu = {
                    navController.navigateFoodSectionTab(
                        target = FoodSectionTab.WEEKLY_MENU,
                        current = FoodSectionTab.RECIPES,
                    )
                },
            )
        }
        composable(NavRoutes.MENU) {
            WeeklyMenuScreen(
                onNavigateToRecipes = {
                    navController.navigateFoodSectionTab(
                        target = FoodSectionTab.RECIPES,
                        current = FoodSectionTab.WEEKLY_MENU,
                    )
                },
            )
        }
        composable(NavRoutes.CALENDAR) { CalendarScreen(navController = navController) }
        composable(NavRoutes.RATING) { RatingScreen() }
        composable(NavRoutes.REWARDS) { RewardsScreen() }
        composable(NavRoutes.RULES) { RulesScreen() }
        composable(NavRoutes.GOALS) { FamilyGoalsScreen(navController = navController) }
        composable(
            route = NavRoutes.GOAL_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(NavRoutes.GOAL_DETAIL_ARG_GOAL_ID) {
                    type = NavType.StringType
                },
            ),
            enterTransition = { DetailEnterTransition },
            exitTransition = { DetailExitTransition },
            popEnterTransition = { DetailPopEnterTransition },
            popExitTransition = { DetailPopExitTransition },
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments?.getString(NavRoutes.GOAL_DETAIL_ARG_GOAL_ID)
            GoalDetailScreen(
                navController = navController,
                goalId = goalId,
            )
        }
        composable(NavRoutes.STATS) { MemberStatisticsScreen() }
        composable(NavRoutes.REPORT) { WeeklyReportScreen() }
        composable(NavRoutes.APPRECIATIONS) { AppreciationsScreen() }
        composable(
            route = NavRoutes.SETTINGS_ROUTE,
            arguments = listOf(
                navArgument(NavRoutes.SETTINGS_ARG_JOIN_FAMILY_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(NavRoutes.SETTINGS_ARG_SYNC_ACTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "familyhub://join?familyId={${NavRoutes.SETTINGS_ARG_JOIN_FAMILY_ID}}" }
            )
        ) { backStackEntry ->
            val joinId = backStackEntry.arguments?.getString(NavRoutes.SETTINGS_ARG_JOIN_FAMILY_ID)
                ?.takeIf { it.isNotBlank() }
            val syncAction = SettingsInitialSyncAction.fromRouteValue(
                backStackEntry.arguments?.getString(NavRoutes.SETTINGS_ARG_SYNC_ACTION)
                    ?.takeIf { it.isNotBlank() }
            )
            SettingsScreen(
                joinFamilyId = joinId,
                initialSyncAction = syncAction,
            )
        }
        composable(
            route = NavRoutes.EDIT_PROFILE_ROUTE,
            arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
            enterTransition = { DetailEnterTransition },
            exitTransition = { DetailExitTransition },
            popEnterTransition = { DetailPopEnterTransition },
            popExitTransition = { DetailPopExitTransition },
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getString("memberId")
            EditProfileScreen(
                navController = navController,
                memberId = memberId
            )
        }
        composable(
            route = NavRoutes.CALENDAR_SYNC,
            enterTransition = { DetailEnterTransition },
            exitTransition = { DetailExitTransition },
            popEnterTransition = { DetailPopEnterTransition },
            popExitTransition = { DetailPopExitTransition },
        ) {
            CalendarSyncScreen(navController = navController)
        }
        composable(NavRoutes.AWESOMENESS) {
            AwesomenessScreen()
        }
        composable(NavRoutes.CONTACTS) {
            ContactsScreen()
        }
        composable(NavRoutes.PENALTIES) {
            PenaltiesScreen()
        }
    }
}
