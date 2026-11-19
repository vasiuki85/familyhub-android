package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.ui.theme.FamilyHubTheme
import com.familyhub.ui.theme.FhTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FhPillTabRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedTabLabel_isVisible_forTaskPalette() {
        val firstTab = "My tasks"
        val secondTab = "All tasks"

        composeRule.setContent {
            FamilyHubTheme {
                FhPillTabRow(
                    selectedTabIndex = 0,
                    onTabSelected = {},
                    tabLabels = listOf(firstTab, secondTab),
                    palette = FhTheme.modules.task,
                )
            }
        }

        composeRule.onNodeWithText(firstTab).assertIsDisplayed()
    }

    @Test
    fun selectedTabLabel_isVisible_forRewardPalette() {
        val catalogTab = "Catalog"
        val pendingTab = "Pending"

        composeRule.setContent {
            FamilyHubTheme {
                FhPillTabRow(
                    selectedTabIndex = 0,
                    onTabSelected = {},
                    tabLabels = listOf(catalogTab, pendingTab),
                    palette = FhTheme.modules.reward,
                )
            }
        }

        composeRule.onNodeWithText(catalogTab).assertIsDisplayed()
    }
}
