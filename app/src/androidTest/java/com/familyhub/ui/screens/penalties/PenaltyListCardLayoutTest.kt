package com.familyhub.ui.screens.penalties

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Penalty
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenaltyListCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactPenaltyCard_meetsHeightBudgetAtPhoneWidth() {
        val penalty = samplePenalty(
            comment = "Rule not completed",
        )
        val member = sampleMember()
        var cardHeightPx = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    PenaltyListCard(
                        penalty = penalty,
                        member = member,
                        issuerName = "Parent",
                        onCancel = {},
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            cardHeightPx = coordinates.size.height
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cardHeightPx / composeRule.density.density
        assertTrue(
            "Penalty card height ${heightDp}dp exceeds budget ${PENALTY_LIST_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= PENALTY_LIST_CARD_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun cancelOverflowAction_requiresConfirmationBeforeCanceling() {
        val penalty = samplePenalty(comment = "Late bedtime")
        val member = sampleMember()

        composeRule.setContent {
            var showCancelConfirmation by remember { mutableStateOf(false) }
            MaterialTheme {
                PenaltyListCard(
                    penalty = penalty,
                    member = member,
                    issuerName = "Parent",
                    onCancel = { showCancelConfirmation = true },
                )
                if (showCancelConfirmation) {
                    androidx.compose.material3.Text(
                        text = "cancel-confirmation",
                        modifier = Modifier.testTag(CANCEL_CONFIRM_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.secondary_actions_cd),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.penalties_cancel),
        ).performClick()

        composeRule.onNodeWithTag(CANCEL_CONFIRM_TAG).assertExists()
    }

    private fun samplePenalty(comment: String): Penalty = Penalty(
        id = "penalty-1",
        userId = "child-1",
        issuedByUserId = "parent-1",
        amount = -20,
        comment = comment,
        createdAt = 1_725_000_000_000L,
        canceled = false,
    )

    private fun sampleMember(): FamilyMember = FamilyMember(
        id = "child-1",
        displayName = "Ivan",
        roleLabel = "Child",
        avatarColor = "#FF0000",
        role = MemberRole.CHILD,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
        const val CANCEL_CONFIRM_TAG = "penalty_cancel_confirmation"
    }
}
