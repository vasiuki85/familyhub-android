package com.familyhub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single rounded icon family for top-level and overflow navigation.
 *
 * Keeping all glyphs in one family gives the bar and the More sheet consistent
 * corner radius, optical weight, and baseline alignment.
 */
object FamilyHubNavIcons {
    val Today: ImageVector = Icons.Rounded.WbSunny
    val Tasks: ImageVector = Icons.Rounded.Checklist
    val Shopping: ImageVector = Icons.Rounded.ShoppingBasket
    val Food: ImageVector = Icons.Rounded.RestaurantMenu
    val More: ImageVector = Icons.Rounded.Apps

    val Recipes: ImageVector = Icons.Rounded.MenuBook
    val Menu: ImageVector = Icons.Rounded.DateRange
    val Calendar: ImageVector = Icons.Rounded.CalendarMonth
    val Rating: ImageVector = Icons.Rounded.Leaderboard
    val Rewards: ImageVector = Icons.Rounded.CardGiftcard
    val Penalties: ImageVector = Icons.Rounded.Gavel
    val Rules: ImageVector = Icons.Rounded.Rule
    val Goals: ImageVector = Icons.Rounded.Flag
    val Stats: ImageVector = Icons.Rounded.Insights
    val Report: ImageVector = Icons.Rounded.Summarize
    val Appreciations: ImageVector = Icons.Rounded.Favorite
    val Awesomeness: ImageVector = Icons.Rounded.AutoAwesome
    val Contacts: ImageVector = Icons.Rounded.ContactPage
    val Settings: ImageVector = Icons.Rounded.Settings
}
