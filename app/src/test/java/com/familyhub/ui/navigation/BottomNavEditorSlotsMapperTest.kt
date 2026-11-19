package com.familyhub.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavEditorSlotsMapperTest {

    @Test
    fun emptyConfig_returnsTodayTasksShoppingFoodSlots() {
        assertEquals(defaultSlots(), BottomNavEditorSlotsMapper.map(emptyList()))
    }

    @Test
    fun invalidOrUnknownConfig_returnsDefaultFourSlots() {
        assertEquals(defaultSlots(), BottomNavEditorSlotsMapper.map(listOf(NavRoutes.HOME)))
        assertEquals(
            defaultSlots(),
            BottomNavEditorSlotsMapper.map(listOf("unknown-a", "unknown-b")),
        )
    }

    @Test
    fun customThreeRoutes_returnsThreeRoutesAndOneEmptySlot() {
        val custom = listOf(NavRoutes.SETTINGS, NavRoutes.HOME, NavRoutes.RECIPES)

        val slots = BottomNavEditorSlotsMapper.map(custom)

        assertEquals(custom[0], slots[0])
        assertEquals(custom[1], slots[1])
        assertEquals(custom[2], slots[2])
        assertNull(slots[3])
    }

    @Test
    fun customFourRoutes_preservesUserOrder() {
        val custom = listOf(
            NavRoutes.SETTINGS,
            NavRoutes.HOME,
            NavRoutes.RECIPES,
            NavRoutes.MENU,
        )

        assertEquals(custom, BottomNavEditorSlotsMapper.map(custom))
    }

    @Test
    fun moreThanFourRoutes_usesFirstFourSlots() {
        val input = listOf(
            NavRoutes.SETTINGS,
            NavRoutes.HOME,
            NavRoutes.RECIPES,
            NavRoutes.MENU,
            NavRoutes.CALENDAR,
            NavRoutes.RATING,
            NavRoutes.SHOPPING,
        )
        val expected = input.take(MAX_BOTTOM_NAV_ITEMS)

        assertEquals(expected, BottomNavEditorSlotsMapper.map(input))
    }

    @Test
    fun legacyDefaultEditorConfig_usesNewFoodDefault() {
        val legacy = listOf(
            NavRoutes.HOME,
            NavRoutes.TASKS,
            NavRoutes.SHOPPING,
            NavRoutes.RATING,
        )

        assertEquals(defaultSlots(), BottomNavEditorSlotsMapper.map(legacy))
    }

    private fun defaultSlots() = listOf(
        NavRoutes.HOME,
        NavRoutes.TASKS,
        NavRoutes.SHOPPING,
        NavRoutes.FOOD,
    )
}
