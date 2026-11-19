package com.familyhub.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileUiMapperTest {

    @Test
    fun tileCounts_areNotLoadedUntilRepositoriesEmit() {
        val initial = HomeTileCounts()
        assertFalse(HomeTileUiMapper.shoppingCountLoaded(initial))
        assertFalse(HomeTileUiMapper.foodCountLoaded(initial))
    }

    @Test
    fun tileCounts_markLoadedAfterFirstEmission() {
        val loaded = HomeTileCounts(shoppingItemsRemaining = 0, todayMealsCount = 0)
        assertTrue(HomeTileUiMapper.shoppingCountLoaded(loaded))
        assertTrue(HomeTileUiMapper.foodCountLoaded(loaded))
    }
}
