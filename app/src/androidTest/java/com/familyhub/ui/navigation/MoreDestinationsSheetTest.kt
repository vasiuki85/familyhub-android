package com.familyhub.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
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

        // The grid lazily composes only visible items: a node below the fold
        // does not exist in the semantics tree yet, so performScrollTo() on
        // the node itself cannot find it. Scroll the grid container to each
        // target instead, then assert it is displayed.
        val grid = composeRule.onNodeWithTag(MORE_DESTINATIONS_GRID_TEST_TAG)

        val title = composeRule.activity.getString(R.string.nav_more_sheet_title)
        grid.performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).assertIsDisplayed()

        val planningGroup = composeRule.activity.getString(R.string.nav_more_group_planning)
        grid.performScrollToNode(hasText(planningGroup))
        composeRule.onNodeWithText(planningGroup).assertIsDisplayed()

        val calendarLabel = composeRule.activity.getString(R.string.nav_label_calendar)
        grid.performScrollToNode(hasText(calendarLabel))
        composeRule.onNodeWithText(calendarLabel).assertIsDisplayed()

        val ratingLabel = composeRule.activity.getString(R.string.nav_label_rating)
        grid.performScrollToNode(hasText(ratingLabel))
        composeRule.onNodeWithText(ratingLabel).assertIsDisplayed()

        val settingsLabel = composeRule.activity.getString(R.string.foundation_v2_more_settings)
        grid.performScrollToNode(hasText(settingsLabel))
        composeRule.onNodeWithText(settingsLabel).assertIsDisplayed()
    }
}
