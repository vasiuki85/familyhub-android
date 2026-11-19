package com.familyhub.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FhLayoutTest {

    @Test
    fun listBottomPadding_reservesFabClearanceWhenFabPresent() {
        assertEquals(FhLayout.fabListBottomPadding, FhLayout.listBottomPadding(true))
        assertEquals(88.dp, FhLayout.listBottomPadding(true))
    }

    @Test
    fun listBottomPadding_usesModestInsetWithoutFab() {
        assertEquals(FhSpacing.lg, FhLayout.listBottomPadding(false))
        assertEquals(16.dp, FhLayout.listBottomPadding(false))
    }

    @Test
    fun fabListBottomPadding_doesNotIncludeBottomNavigationBarHeight() {
        assertEquals(88.dp, FhLayout.fabListBottomPadding)
    }
}
