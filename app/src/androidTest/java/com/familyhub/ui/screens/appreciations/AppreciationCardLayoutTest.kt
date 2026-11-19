package com.familyhub.ui.screens.appreciations

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.Appreciation
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppreciationCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longAppreciationCard_staysWithinBudgetAtLargeFontScale() {
        val item = AppreciationListItem(
            appreciation = Appreciation(
                id = "app-1",
                senderId = "sender-1",
                recipientId = "recipient-1",
                message = "Спасибо за помощь с уроками, за терпение и за то, что всегда находишь время " +
                    "объяснить сложную тему простыми словами, даже когда день был очень напряжённым.",
                createdAt = 1L,
            ),
            sender = sampleMember("sender-1", "Иван"),
            recipient = sampleMember("recipient-1", "Мама"),
            senderName = "Иван",
            recipientName = "Мама",
            createdAtText = "1 янв., 12:00",
        )
        var cardHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        AppreciationCard(
                            item = item,
                            onDelete = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                cardHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cardHeightPx / composeRule.density.density
        assertTrue(
            "Appreciation card height ${heightDp}dp exceeds budget ${APPRECIATION_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= APPRECIATION_CARD_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun unbrokenAppreciationText_staysWithinBudgetAndExpands() {
        val unbrokenMessage = "благодарность".repeat(40)
        val item = AppreciationListItem(
            appreciation = Appreciation(
                id = "app-2",
                senderId = "sender-1",
                recipientId = "recipient-1",
                message = unbrokenMessage,
                createdAt = 1L,
            ),
            sender = sampleMember("sender-1", "Иван"),
            recipient = sampleMember("recipient-1", "Мама"),
            senderName = "Иван",
            recipientName = "Мама",
            createdAtText = "1 янв., 12:00",
        )
        var collapsedHeightPx = 0
        val showMoreLabel = composeRule.activity.getString(R.string.awesomeness_show_more)
        val showLessLabel = composeRule.activity.getString(R.string.awesomeness_show_less)

        composeRule.setContent {
            LargeFontScale {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        AppreciationCard(
                            item = item,
                            onDelete = null,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                collapsedHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val collapsedHeightDp = collapsedHeightPx / composeRule.density.density
        assertTrue(
            "Collapsed appreciation card height ${collapsedHeightDp}dp exceeds budget",
            collapsedHeightDp <= APPRECIATION_CARD_MAX_HEIGHT_DP,
        )

        composeRule.onNodeWithText(showMoreLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(showLessLabel).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun LargeFontScale(content: @androidx.compose.runtime.Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        CompositionLocalProvider(LocalDensity provides density, content = content)
    }

    private fun sampleMember(id: String, name: String): FamilyMember = FamilyMember(
        id = id,
        displayName = name,
        roleLabel = "Child",
        avatarColor = "#FF0000",
        role = MemberRole.CHILD,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
