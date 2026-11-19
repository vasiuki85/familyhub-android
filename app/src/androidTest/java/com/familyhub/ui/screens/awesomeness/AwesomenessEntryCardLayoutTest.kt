package com.familyhub.ui.screens.awesomeness

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
import com.familyhub.domain.model.AwesomenessEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AwesomenessEntryCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortEntry_meetsMinimumHeightWithoutExcessPadding() {
        val entry = sampleEntry("I helped my sister today.")
        var cardHeightPx = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    AwesomenessEntryCard(
                        entry = entry,
                        onEdit = {},
                        onDelete = {},
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
            "Short awesomeness card ${heightDp}dp is below minimum ${AWESOMENESS_ENTRY_MIN_HEIGHT_DP}dp",
            heightDp >= AWESOMENESS_ENTRY_MIN_HEIGHT_DP,
        )
        assertTrue(
            "Short awesomeness card ${heightDp}dp exceeds budget ${AWESOMENESS_SHORT_ENTRY_MAX_HEIGHT_DP}dp",
            heightDp <= AWESOMENESS_SHORT_ENTRY_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun longEntry_staysWithinBudgetAtLargeFontScale() {
        val entry = sampleEntry(
            "I stayed calm during a difficult conversation, finished my homework without reminders, " +
                "and helped prepare dinner while keeping a positive attitude throughout the evening.",
        )
        var cardHeightPx = 0

        composeRule.setContent {
            val density = Density(
                density = LocalDensity.current.density,
                fontScale = 1.5f,
            )
            CompositionLocalProvider(LocalDensity provides density) {
                MaterialTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        AwesomenessEntryCard(
                            entry = entry,
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
            "Long awesomeness card ${heightDp}dp exceeds budget ${AWESOMENESS_LONG_ENTRY_MAX_HEIGHT_DP}dp",
            heightDp <= AWESOMENESS_LONG_ENTRY_MAX_HEIGHT_DP,
        )
    }

    private fun sampleEntry(text: String): AwesomenessEntry = AwesomenessEntry(
        id = "entry-1",
        text = text,
        createdAt = 1L,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
        const val AWESOMENESS_SHORT_ENTRY_MAX_HEIGHT_DP = 120
        const val AWESOMENESS_LONG_ENTRY_MAX_HEIGHT_DP = 220
    }
}
