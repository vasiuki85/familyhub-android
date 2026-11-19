package com.familyhub.ui.screens.menu

import androidx.compose.ui.unit.dp
import com.familyhub.ui.theme.FhLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyMenuLayoutPolicyTest {

    @Test
    fun weekRange_usesCompactDatesOnlyWhenTitleWidthIsConstrained() {
        assertTrue(WeeklyMenuLayoutPolicy.useCompactWeekRange(199.dp))
        assertFalse(WeeklyMenuLayoutPolicy.useCompactWeekRange(200.dp))
    }

    @Test
    fun listBottomPadding_usesSharedNonFabInset() {
        assertEquals(FhLayout.listBottomPadding(false), WeeklyMenuLayoutPolicy.listBottomPadding())
    }
}
