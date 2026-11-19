package com.familyhub.ui.screens.rewards

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.Reward
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardCatalogCardRoleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun memberView_hidesManageControlsUntilParentOpensEditFlow() {
        val reward = sampleReward()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    RewardCatalogCard(
                        reward = reward,
                        canManage = false,
                        canRequest = true,
                        currentBalance = 5,
                        creatorName = "Parent",
                        onRequest = {},
                        onToggleActive = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(REWARD_CREATOR_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(REWARD_ACTIVE_SWITCH_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.secondary_actions_cd),
        ).assertDoesNotExist()
    }

    @Test
    fun parentManageDialog_showsCreatorAndActiveSwitch() {
        val reward = sampleReward()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    RewardCatalogCard(
                        reward = reward,
                        canManage = true,
                        canRequest = true,
                        currentBalance = 5,
                        creatorName = "Parent",
                        onRequest = {},
                        onToggleActive = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.secondary_actions_cd),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.rewards_edit_action),
        ).performClick()

        composeRule.onNodeWithTag(REWARD_CREATOR_TAG).assertExists()
        composeRule.onNodeWithTag(REWARD_ACTIVE_SWITCH_TAG).assertExists()
    }

    @Test
    fun negativeBalance_rendersThirtyPointsRemainingOnCard() {
        val reward = sampleReward(cost = 20)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    RewardCatalogCard(
                        reward = reward,
                        canManage = false,
                        canRequest = true,
                        currentBalance = -10,
                        creatorName = "Parent",
                        onRequest = {},
                        onToggleActive = {},
                    )
                }
            }
        }

        val expected = composeRule.activity.getString(
            R.string.rewards_progress_remaining,
            30,
        )
        composeRule.onNodeWithText(expected).assertExists()
        composeRule.onNodeWithTag(REWARD_REMAINING_POINTS_TAG).assertExists()
    }

    private fun sampleReward(cost: Int = 20): Reward = Reward(
        id = "reward-1",
        title = "Movie night",
        description = null,
        cost = cost,
        createdByUserId = "parent-1",
        isActive = true,
        createdAt = 1L,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
