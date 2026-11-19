package com.familyhub.ui.screens.rules

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.HouseRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HouseRuleCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactRuleCard_staysWithinBudgetAtLargeFontScale() {
        val item = HouseRuleListItem(
            rule = HouseRule(
                id = "rule-1",
                title = "Соблюдать тишину после десяти вечера в общих помещениях",
                description = "Громкая музыка, крики и активные игры переносятся в личные комнаты или на улицу.",
                createdByUserId = "parent-1",
                createdAt = 1L,
                updatedAt = 1L,
                category = "home",
            ),
            authorName = "Мама",
        )
        var cardHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        HouseRuleCard(
                            item = item,
                            canEdit = true,
                            onEdit = {},
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
            "Rule card height ${heightDp}dp exceeds budget ${HOUSE_RULE_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= HOUSE_RULE_CARD_MAX_HEIGHT_DP,
        )
    }

    @androidx.compose.runtime.Composable
    private fun LargeFontScale(content: @androidx.compose.runtime.Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        CompositionLocalProvider(LocalDensity provides density, content = content)
    }

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
