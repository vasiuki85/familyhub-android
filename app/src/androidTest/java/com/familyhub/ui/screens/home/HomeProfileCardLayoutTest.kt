package com.familyhub.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.ui.theme.FamilyHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(AndroidJUnit4::class)
class HomeProfileCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userProfileCard_isAtLeastFifteenPercentShorterThanPreviousBudget() {
        var cardHeightPx = 0
        val member = FamilyMember(
            id = "parent",
            displayName = "Alex",
            roleLabel = "login",
            avatarColor = "#000000",
            role = MemberRole.PARENT,
        )

        composeRule.setContent {
            FamilyHubTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    UserProfileCard(
                        member = member,
                        familyMembers = listOf(member, member.copy(id = "child", displayName = "Sam")),
                        weeklyScore = -10,
                        onEditClick = {},
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
            "Profile card height ${heightDp}dp exceeds compact budget ${PROFILE_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= PROFILE_CARD_MAX_HEIGHT_DP,
        )
    }

    companion object {
        private const val PHONE_WIDTH_DP = 360f
        private const val PROFILE_CARD_MAX_HEIGHT_DP = 150f
    }
}
