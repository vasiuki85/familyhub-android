package com.familyhub.ui.screens.shopping

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingTopBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun titleSelectorShowsSelectedListAndSelectsAnother() {
        val firstList = shoppingList(
            id = "list1",
            title = "Еженедельные покупки",
            colorHex = "#4CAF50",
        )
        val secondList = shoppingList(
            id = "list2",
            title = "Для праздника",
            colorHex = "#315F93",
        )
        val state = ShoppingUiState(
            isLoading = false,
            lists = listOf(firstList, secondList),
            selectedListId = firstList.id,
        )
        var selectedListId: String? = null

        composeRule.setContent {
            MaterialTheme {
                ShoppingListTitleContent(
                    state = state,
                    onSelectList = { selectedListId = it },
                )
            }
        }

        composeRule.onNodeWithText(firstList.title).performClick()
        composeRule.onNodeWithText(secondList.title).performClick()
        composeRule.runOnIdle {
            assertEquals(secondList.id, selectedListId)
        }
    }

    @Test
    fun progressSummaryShowsCompactCountsWithoutHeading() {
        composeRule.setContent {
            MaterialTheme {
                ShoppingProgressSummary(
                    boughtCount = 0,
                    totalCount = 14,
                )
            }
        }

        val progressTitle = composeRule.activity.getString(R.string.shopping_module_progress_title)
        val remaining = composeRule.activity.getString(R.string.shopping_module_progress_remaining, 14)
        val progress = composeRule.activity.getString(R.string.shopping_progress, 0, 14)

        composeRule.onNodeWithText(progressTitle).assertDoesNotExist()
        composeRule.onNodeWithText(remaining).assertExists()
        composeRule.onNodeWithText(progress).assertExists()
    }

    @Test
    fun progressSummary_atZeroProgress_reportsEmptyBarSemantics() {
        composeRule.setContent {
            MaterialTheme {
                ShoppingProgressSummary(
                    boughtCount = 0,
                    totalCount = 14,
                )
            }
        }

        composeRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(current = 0f, range = 0f..1f)),
        ).assertExists()
    }

    @Test
    fun filterSectionHighlightsActiveFilter() {
        val category = ShoppingCategory(
            id = "cat1",
            listId = "list1",
            title = "Овощи",
            colorHex = "#8BC34A",
            iconName = "vegetables",
            sortOrder = 0,
        )
        val state = ShoppingUiState(
            isLoading = false,
            activeFilter = ShoppingFilter.NotBought,
            categories = listOf(category),
        )

        composeRule.setContent {
            MaterialTheme {
                FilterSection(
                    state = state,
                    onFilterSelected = {},
                )
            }
        }

        val allLabel = composeRule.activity.getString(R.string.shopping_filter_all)
        val notBoughtLabel =
            composeRule.activity.getString(R.string.shopping_filter_not_bought)

        composeRule.onNodeWithText(notBoughtLabel).assertIsSelected()
        composeRule.onNodeWithText(allLabel).assertIsNotSelected()
        composeRule.onNodeWithText(category.title).assertExists()
    }

    @Test
    fun filterSectionInvokesCallbackOnChipClick() {
        val state = ShoppingUiState(
            isLoading = false,
            activeFilter = ShoppingFilter.All,
        )
        var selected: ShoppingFilter = ShoppingFilter.All

        composeRule.setContent {
            MaterialTheme {
                FilterSection(
                    state = state,
                    onFilterSelected = { selected = it },
                )
            }
        }

        val notBoughtLabel =
            composeRule.activity.getString(R.string.shopping_filter_not_bought)
        composeRule.onNodeWithText(notBoughtLabel).performClick()
        composeRule.runOnIdle {
            assertTrue(selected is ShoppingFilter.NotBought)
        }
    }

    @Test
    fun quickSuggestionsRowIsDistinctFromFilterSection() {
        val suggestions = listOf("Хлеб", "Молоко")
        val quickAddHeading = composeRule.activity.getString(R.string.shopping_quick_add_heading)
        val quickAddRowCd = composeRule.activity.getString(R.string.shopping_quick_add_row_cd)

        composeRule.setContent {
            MaterialTheme {
                QuickSuggestionsRow(
                    suggestions = suggestions,
                    onSuggestion = {},
                )
            }
        }

        composeRule.onNodeWithText(quickAddHeading).assertExists()
        composeRule.onNodeWithContentDescription(quickAddRowCd).assertExists()
        composeRule.onNodeWithText(suggestions.first()).assertExists()
    }

    private fun shoppingList(
        id: String,
        title: String,
        colorHex: String,
    ): ShoppingList = ShoppingList(
        id = id,
        title = title,
        colorHex = colorHex,
        iconName = "cart",
        createdAt = 0L,
    )
}
