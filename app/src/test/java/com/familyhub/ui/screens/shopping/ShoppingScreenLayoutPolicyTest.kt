package com.familyhub.ui.screens.shopping

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingScreenLayoutPolicyTest {

    @Test
    fun listBottomPadding_reservesOnlyTheSpaceRequiredByFab() {
        assertEquals(
            88.dp,
            ShoppingScreenLayoutPolicy.listBottomPadding(
                showsFloatingActionButton = true,
            ),
        )
        assertEquals(
            16.dp,
            ShoppingScreenLayoutPolicy.listBottomPadding(
                showsFloatingActionButton = false,
            ),
        )
    }
}
