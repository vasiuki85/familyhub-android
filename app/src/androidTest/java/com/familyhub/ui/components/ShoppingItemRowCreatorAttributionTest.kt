package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.ShoppingItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingItemRowCreatorAttributionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun creatorAttribution_visibleEvenWhenOnlyCreatorInList() {
        val creator = FamilyMember(
            id = "user-1",
            displayName = "Ivan",
            roleLabel = "ivan.login",
            avatarColor = "#4CAF50",
            role = MemberRole.CHILD,
        )
        val item = ShoppingItem(
            id = "item-1",
            listId = "list-1",
            title = "Milk",
            createdByUserId = creator.id,
            isBought = false,
            createdAt = 1L,
            quantity = 1.0,
        )

        composeRule.setContent {
            MaterialTheme {
                ShoppingItemRow(
                    item = item,
                    category = null,
                    creator = creator,
                    showCategoryInMeta = false,
                    onStatusChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(SHOPPING_CREATOR_ATTRIBUTION_TEST_TAG).assertExists()
        composeRule.onNodeWithText("Ivan").assertExists()
    }
}
