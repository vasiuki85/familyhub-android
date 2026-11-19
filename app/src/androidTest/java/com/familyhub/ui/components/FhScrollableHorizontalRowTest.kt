package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.ui.theme.FamilyHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FhScrollableHorizontalRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedChip_isScrolledIntoView() {
        val labels = List(12) { index -> "Filter option $index" }
        val selectedIndex = labels.lastIndex
        val selectedLabel = labels.last()

        composeRule.setContent {
            FamilyHubTheme {
                FhScrollableHorizontalRow(
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .width(220.dp)
                        .height(56.dp),
                ) {
                    items(labels.size) { index ->
                        FilterChip(
                            selected = index == selectedIndex,
                            onClick = {},
                            label = { Text(labels[index]) },
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithText(selectedLabel).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithText(selectedLabel).assertIsDisplayed()
    }
}
