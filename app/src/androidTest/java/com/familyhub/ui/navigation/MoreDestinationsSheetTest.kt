package com.familyhub.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.ui.theme.FamilyHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreDestinationsSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val barRoutes = listOf(
        NavRoutes.HOME,
        NavRoutes.TASKS,
        NavRoutes.SHOPPING,
        NavRoutes.FOOD,
    )

    @Test
    fun sheet_rendersGroupedDestinationsWithoutCrashing() {
        val navController = NavHostController(composeRule.activity.applicationContext)

        composeRule.setContent {
            FamilyHubTheme {
                MoreDestinationsSheet(
                    navController = navController,
                    barRoutes = barRoutes,
                    currentRoute = NavRoutes.HOME,
                    onDismiss = {},
                )
            }
        }

        composeRule.waitForIdle()

        // The sheet is a scrollable grid; on short viewports the lower items
        // sit below the fold, so scroll each target into view before asserting.
        val title = composeRule.activity.getString(R.string.nav_more_sheet_title)
        composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()

        val planningGroup = composeRule.activity.getString(R.string.nav_more_group_planning)
        composeRule.onNodeWithText(planningGroup).performScrollTo().assertIsDisplayed()

        val calendarLabel = composeRule.activity.getString(R.string.nav_label_calendar)
        composeRule.onNodeWithText(calendarLabel).performScrollTo().assertIsDisplayed()

        val ratingLabel = composeRule.activity.getString(R.string.nav_label_rating)
        composeRule.onNodeWithText(ratingLabel).performScrollTo().assertIsDisplayed()

        val settingsLabel = composeRule.activity.getString(R.string.foundation_v2_more_settings)
        composeRule.onNodeWithText(settingsLabel).performScrollTo().assertIsDisplayed()
    }
}
