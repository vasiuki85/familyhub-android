package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingItemRowCategoryStripeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun categorisedItem_exposesCategoryStripeTestTag() {
        val category = ShoppingCategory(
            id = "cat-1",
            listId = "list-1",
            title = "Produce",
            colorHex = "#4CAF50",
            iconName = "leaf",
            sortOrder = 0,
        )
        val item = ShoppingItem(
            id = "item-1",
            listId = "list-1",
            title = "Apples",
            createdByUserId = "user-1",
            isBought = false,
            createdAt = 1L,
            categoryId = category.id,
        )

        composeRule.setContent {
            MaterialTheme {
                ShoppingItemRow(
                    item = item,
                    category = category,
                    creator = null,
                    showCategoryInMeta = false,
                    onStatusChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(SHOPPING_CATEGORY_STRIPE_TEST_TAG).assertExists()
    }

    @Test
    fun uncategorisedItem_hidesCategoryStripeTestTag() {
        val item = ShoppingItem(
            id = "item-2",
            listId = "list-1",
            title = "Bread",
            createdByUserId = "user-1",
            isBought = false,
            createdAt = 1L,
        )

        composeRule.setContent {
            MaterialTheme {
                ShoppingItemRow(
                    item = item,
                    category = null,
                    creator = null,
                    showCategoryInMeta = false,
                    onStatusChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(SHOPPING_CATEGORY_STRIPE_TEST_TAG).assertDoesNotExist()
    }
}
