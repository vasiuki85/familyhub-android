package com.familyhub.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavNavigationTest {
    @Test
    fun shouldPopToStartDestination_onlyForHomeRoute() {
        assertTrue(shouldPopToStartDestination(NavRoutes.HOME, NavRoutes.HOME))
        assertFalse(shouldPopToStartDestination(NavRoutes.TASKS, NavRoutes.HOME))
        assertFalse(shouldPopToStartDestination(NavRoutes.HOME, null))
    }
}
