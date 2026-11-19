package com.familyhub.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class GetActiveBottomNavItemsTest {

    @Test
    fun emptyConfig_returnsTodayTasksShoppingFoodInOrder() {
        assertEquals(defaultRoutes(), getActiveBottomNavItems(emptyList()).map { it.route })
    }

    @Test
    fun oneOrTwoValidRoutes_returnsDefault() {
        assertEquals(defaultRoutes(), getActiveBottomNavItems(listOf(NavRoutes.HOME)).map { it.route })
        assertEquals(
            defaultRoutes(),
            getActiveBottomNavItems(listOf(NavRoutes.HOME, NavRoutes.TASKS)).map { it.route },
        )
    }

    @Test
    fun allUnknownRoutes_returnsDefault() {
        assertEquals(
            defaultRoutes(),
            getActiveBottomNavItems(listOf("unknown-a", "unknown-b")).map { it.route },
        )
    }

    @Test
    fun filterAndDedupToTwoValidRoutes_returnsDefault() {
        assertEquals(
            defaultRoutes(),
            getActiveBottomNavItems(
                listOf(NavRoutes.HOME, "unknown-route", NavRoutes.TASKS),
            ).map { it.route },
        )
        assertEquals(
            defaultRoutes(),
            getActiveBottomNavItems(
                listOf(
                    NavRoutes.HOME,
                    NavRoutes.TASKS,
                    NavRoutes.HOME,
                    NavRoutes.TASKS,
                ),
            ).map { it.route },
        )
    }

    @Test
    fun threeValidRoutes_preservesOrder() {
        val custom = listOf(NavRoutes.SETTINGS, NavRoutes.HOME, NavRoutes.RECIPES)

        assertEquals(custom, getActiveBottomNavItems(custom).map { it.route })
    }

    @Test
    fun fourValidRoutes_preservesOrder() {
        val custom = listOf(
            NavRoutes.SETTINGS,
            NavRoutes.HOME,
            NavRoutes.RECIPES,
            NavRoutes.MENU,
        )

        assertEquals(custom, getActiveBottomNavItems(custom).map { it.route })
    }

    @Test
    fun moreThanFourValidRoutes_usesFirstFour() {
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

        assertEquals(expected, getActiveBottomNavItems(input).map { it.route })
    }

    @Test
    fun unknownRoutesFiltered_threeOrMoreValidPreserved() {
        val input = listOf(
            NavRoutes.HOME,
            "unknown-route",
            NavRoutes.TASKS,
            "another-unknown",
            NavRoutes.SHOPPING,
        )
        val expected = listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.SHOPPING)

        assertEquals(expected, getActiveBottomNavItems(input).map { it.route })
    }

    @Test
    fun normalizeBottomNavRoutes_distinctFiltersAndCapsAtFour() {
        assertEquals(
            listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.SHOPPING),
            normalizeBottomNavRoutes(
                listOf(
                    NavRoutes.HOME,
                    NavRoutes.TASKS,
                    NavRoutes.HOME,
                    NavRoutes.TASKS,
                    NavRoutes.SHOPPING,
                ),
            ),
        )
        assertEquals(
            listOf(
                NavRoutes.HOME,
                NavRoutes.TASKS,
                NavRoutes.SHOPPING,
                NavRoutes.FOOD,
            ),
            normalizeBottomNavRoutes(
                listOf(
                    NavRoutes.HOME,
                    NavRoutes.TASKS,
                    NavRoutes.SHOPPING,
                    NavRoutes.FOOD,
                    NavRoutes.SETTINGS,
                    NavRoutes.RECIPES,
                ),
            ),
        )
    }

    @Test
    fun legacyDefaultRoutes_migrateToFoodDefault() {
        val legacyDefaults = listOf(
            listOf(
                NavRoutes.HOME,
                NavRoutes.TASKS,
                NavRoutes.MENU,
                NavRoutes.CALENDAR,
                NavRoutes.SETTINGS,
            ),
            listOf(
                NavRoutes.HOME,
                NavRoutes.TASKS,
                NavRoutes.SHOPPING,
                NavRoutes.RATING,
                NavRoutes.SETTINGS,
            ),
            listOf(
                NavRoutes.HOME,
                NavRoutes.TASKS,
                NavRoutes.SHOPPING,
                NavRoutes.RATING,
            ),
        )

        legacyDefaults.forEach { stored ->
            assertEquals(defaultRoutes(), resolveBottomNavRoutes(stored))
        }
    }

    @Test
    fun customLegacyMenuRoutes_arePreserved() {
        val custom = listOf(NavRoutes.SETTINGS, NavRoutes.RECIPES, NavRoutes.MENU)

        assertEquals(custom, resolveBottomNavRoutes(custom))
    }

    @Test
    fun foodDestination_isActiveForFoodMenuAndRecipes() {
        assertEquals(true, isBottomNavRouteActive(NavRoutes.FOOD, NavRoutes.FOOD))
        assertEquals(true, isBottomNavRouteActive(NavRoutes.MENU, NavRoutes.FOOD))
        assertEquals(true, isBottomNavRouteActive(NavRoutes.RECIPES, NavRoutes.FOOD))
        assertEquals(false, isBottomNavRouteActive(NavRoutes.RATING, NavRoutes.FOOD))
    }

    @Test
    fun foodWithChildDestinations_keepsOnlyCanonicalFoodTab() {
        val activeRoutes = getActiveBottomNavItems(
            listOf(
                NavRoutes.HOME,
                NavRoutes.FOOD,
                NavRoutes.MENU,
                NavRoutes.RECIPES,
                NavRoutes.CALENDAR,
            ),
        ).map { it.route }

        assertEquals(
            listOf(NavRoutes.HOME, NavRoutes.FOOD, NavRoutes.CALENDAR),
            activeRoutes,
        )
        listOf(NavRoutes.FOOD, NavRoutes.MENU, NavRoutes.RECIPES).forEach { currentRoute ->
            val selectedCount = activeRoutes.count { itemRoute ->
                isBottomNavRouteActive(currentRoute, itemRoute)
            }
            assertEquals(1, selectedCount)
        }
    }

    private fun defaultRoutes() = listOf(
        NavRoutes.HOME,
        NavRoutes.TASKS,
        NavRoutes.SHOPPING,
        NavRoutes.FOOD,
    )
}
