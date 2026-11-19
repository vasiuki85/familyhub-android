package com.familyhub.ui.screens.food

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodScreenLayoutPolicyTest {

    @Test
    fun sectionHeader_stacksOnlyWhenActionsWouldBeConstrained() {
        assertTrue(FoodScreenLayoutPolicy.stackSectionHeader(319.dp))
        assertFalse(FoodScreenLayoutPolicy.stackSectionHeader(320.dp))
    }

    @Test
    fun existingCompactCardsRemainResponsiveAtPhoneWidths() {
        assertTrue(FoodScreenLayoutPolicy.stackEmptyCard(359.dp))
        assertFalse(FoodScreenLayoutPolicy.stackEmptyCard(360.dp))

        val compact = FoodScreenLayoutPolicy.introLayout(359.dp)
        val regular = FoodScreenLayoutPolicy.introLayout(360.dp)

        assertFalse(compact.showAccentIcon)
        assertTrue(regular.showAccentIcon)
    }
}
