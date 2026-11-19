package com.familyhub.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarVisibilityPolicyTest {

    @Test
    fun unresolvedAndAuthenticationRoutes_hideBottomBar() {
        assertFalse(BottomBarVisibilityPolicy.shouldShow(null))
        assertFalse(BottomBarVisibilityPolicy.shouldShow(NavRoutes.AUTH))
    }

    @Test
    fun detailRoutes_hideBottomBar() {
        assertFalse(BottomBarVisibilityPolicy.shouldShow(NavRoutes.EDIT_PROFILE_ROUTE))
        assertFalse(BottomBarVisibilityPolicy.shouldShow(NavRoutes.CALENDAR_SYNC))
    }

    @Test
    fun topLevelRoutes_showBottomBar() {
        assertTrue(BottomBarVisibilityPolicy.shouldShow(NavRoutes.HOME))
        assertTrue(BottomBarVisibilityPolicy.shouldShow(NavRoutes.TASKS))
        assertTrue(BottomBarVisibilityPolicy.shouldShow(NavRoutes.SETTINGS_ROUTE))
    }
}
