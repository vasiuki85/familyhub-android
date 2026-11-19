package com.familyhub.ui.screens.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingSearchFilterReducerTest {

    @Test
    fun apply_clearsSearchAndResetsFilterAtomically() {
        val state = ShoppingUiState(
            searchQuery = "milk",
            activeFilter = ShoppingFilter.NotBought,
        )

        val result = ShoppingSearchFilterReducer.apply(state)

        assertTrue(result.searchQuery.isEmpty())
        assertEquals(ShoppingFilter.All, result.activeFilter)
    }
}
