package com.familyhub.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreDestinationsPolicyTest {

    @Test
    fun settings_isNeverDuplicatedInGrid() {
        val routes = getMoreDestinationItems(
            listOf(NavRoutes.HOME, NavRoutes.TASKS, NavRoutes.SHOPPING, NavRoutes.FOOD),
        ).map { it.route }

        assertFalse(NavRoutes.SETTINGS in routes)
    }

    @Test
    fun activeBarRoutes_areExcludedAndOtherSectionsRemainReachable() {
        val barRoutes = listOf(
            NavRoutes.HOME,
            NavRoutes.TASKS,
            NavRoutes.SHOPPING,
            NavRoutes.FOOD,
        )
        val routes = getMoreDestinationItems(barRoutes).map { it.route }

        assertTrue(routes.none { it in barRoutes })
        assertTrue(NavRoutes.CALENDAR in routes)
        assertTrue(NavRoutes.RATING in routes)
        assertTrue(NavRoutes.REWARDS in routes)
    }

    @Test
    fun destinationTones_groupSectionsByMeaning() {
        assertTrue(moreDestinationTone(NavRoutes.RULES) == MoreDestinationTone.TASK)
        assertTrue(moreDestinationTone(NavRoutes.CALENDAR) == MoreDestinationTone.SHOPPING)
        assertTrue(moreDestinationTone(NavRoutes.RECIPES) == MoreDestinationTone.FOOD)
        assertTrue(moreDestinationTone(NavRoutes.REWARDS) == MoreDestinationTone.REWARD)
        assertTrue(moreDestinationTone(NavRoutes.PENALTIES) == MoreDestinationTone.ATTENTION)
    }
}
