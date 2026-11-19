package com.familyhub.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FhScrollableHorizontalRowSourceContractTest {

    @Test
    fun scrollableRow_appliesFadeAndAutoScrollToSelected() {
        val source = File("src/main/java/com/familyhub/ui/components/FhScrollableHorizontalRow.kt")
            .readText(Charsets.UTF_8)

        assertTrue(source.contains("fhHorizontalScrollFadeOverlay"))
        assertTrue(source.contains("animateScrollToItem"))
        assertTrue(source.contains("canScrollForward"))
    }

    @Test
    fun pillTabRow_usesSharedScrollableRow() {
        val source = File("src/main/java/com/familyhub/ui/components/FhPillTabRow.kt")
            .readText(Charsets.UTF_8)

        assertTrue(source.contains("FhScrollableHorizontalRow("))
        assertTrue(source.contains("selectedIndex = selectedTabIndex"))
    }
}
