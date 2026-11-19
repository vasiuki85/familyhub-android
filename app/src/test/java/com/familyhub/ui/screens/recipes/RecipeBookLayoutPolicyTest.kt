package com.familyhub.ui.screens.recipes

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeBookLayoutPolicyTest {

    @Test
    fun narrowCard_usesCompactImageAndSpacing() {
        val compact = RecipeBookLayoutPolicy.cardLayout(320.dp)
        val regular = RecipeBookLayoutPolicy.cardLayout(400.dp)

        assertEquals(64.dp, compact.imageSize)
        assertEquals(8.dp, compact.horizontalSpacing)
        assertTrue(compact.imageSize < regular.imageSize)
        assertTrue(compact.horizontalSpacing < regular.horizontalSpacing)
    }

    @Test
    fun widthBoundary_switchesToRegularLayoutAt360Dp() {
        assertEquals(
            64.dp,
            RecipeBookLayoutPolicy.cardLayout(359.dp).imageSize,
        )
        assertEquals(
            86.dp,
            RecipeBookLayoutPolicy.cardLayout(360.dp).imageSize,
        )
    }

    @Test
    fun extraNarrowCard_reducesImageAndPaddingWithoutShrinkingActions() {
        val extraCompact = RecipeBookLayoutPolicy.cardLayout(280.dp)

        assertEquals(56.dp, extraCompact.imageSize)
        assertEquals(8.dp, extraCompact.contentPadding)
        assertEquals(8.dp, extraCompact.horizontalSpacing)
    }

    @Test
    fun extraCompactBoundary_switchesToCompactLayoutAt300Dp() {
        assertEquals(
            56.dp,
            RecipeBookLayoutPolicy.cardLayout(299.dp).imageSize,
        )
        assertEquals(
            64.dp,
            RecipeBookLayoutPolicy.cardLayout(300.dp).imageSize,
        )
    }

    @Test
    fun listWithFab_reservesMoreBottomSpaceThanReadOnlyList() {
        val editablePadding = RecipeBookLayoutPolicy.listBottomPadding(
            hasFloatingActionButton = true,
        )
        val readOnlyPadding = RecipeBookLayoutPolicy.listBottomPadding(
            hasFloatingActionButton = false,
        )

        assertEquals(88.dp, editablePadding)
        assertEquals(16.dp, readOnlyPadding)
        assertTrue(editablePadding > readOnlyPadding)
    }
}
