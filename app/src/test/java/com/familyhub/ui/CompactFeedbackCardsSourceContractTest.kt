package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactFeedbackCardsSourceContractTest {

    @Test
    fun rewards_successFeedback_usesCompactSnackbarInsteadOfLargeInfoCard() {
        val source = source("screens/rewards/RewardsScreen.kt")

        assertTrue(source.contains("SnackbarHostState"))
        assertTrue(source.contains("showSnackbar"))
        assertTrue(source.contains("infoMessage"))
        assertFalse(source.contains("InfoMessage("))
        assertFalse(source.contains("ui.components.InfoMessage"))
    }

    @Test
    fun penaltyCard_keepsCompactTopAlignedRowWithOverflowMenu() {
        val card = source("screens/penalties/PenaltyListCard.kt")

        assertTrue(card.contains("verticalAlignment = Alignment.Top"))
        assertTrue(card.contains("FhMemberAvatar("))
        assertTrue(card.contains("DropdownMenu("))
        assertTrue(card.contains("onCancel()"))
        assertFalse(card.contains("TextButton("))
    }

    @Test
    fun appreciationCard_keepsOverflowAtTopEndWithoutDoublePadding() {
        val card = source("screens/appreciations/AppreciationCard.kt")

        assertTrue(card.contains("FhMemberAvatar("))
        assertTrue(card.contains("AWESOMENESS_ENTRY_COLLAPSED_MAX_LINES"))
        assertTrue(card.contains("IconButton("))
        assertTrue(card.contains("DropdownMenu("))
        assertFalse(card.contains("Column(modifier = Modifier.padding(16.dp))"))
    }

    private fun section(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        val endIndex = source.indexOf(end)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Unable to locate source section from '$start' to '$end'"
        }
        return source.substring(startIndex, endIndex)
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/familyhub/ui/$relativePath")
            .readText(Charsets.UTF_8)
}
