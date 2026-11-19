package com.familyhub.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsInitialSyncActionPolicyTest {

    @Test
    fun resolve_returnsNullBeforeHydrationWithoutConsuming() {
        val action = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.CREATE,
            alreadyConsumed = false,
            accountId = null,
            isHydrated = false,
        )

        assertNull(action)
    }

    @Test
    fun resolve_returnsCreateOnceWhenHydratedAndUnbound() {
        val action = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.CREATE,
            alreadyConsumed = false,
            accountId = null,
            isHydrated = true,
        )

        assertEquals(SettingsInitialSyncAction.CREATE, action)
    }

    @Test
    fun resolve_afterHydrationBoundActionDoesNotOpen() {
        val action = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.JOIN,
            alreadyConsumed = false,
            accountId = "family-a",
            isHydrated = true,
        )

        assertNull(action)
    }

    @Test
    fun resolve_returnsNullWhenAlreadyConsumed() {
        val action = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.JOIN,
            alreadyConsumed = true,
            accountId = null,
            isHydrated = true,
        )

        assertNull(action)
    }

    @Test
    fun resolve_hydrationTransitionOpensUnboundActionOnce() {
        val beforeHydration = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.JOIN,
            alreadyConsumed = false,
            accountId = null,
            isHydrated = false,
        )
        val afterHydration = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.JOIN,
            alreadyConsumed = false,
            accountId = null,
            isHydrated = true,
        )
        val afterConsumption = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.JOIN,
            alreadyConsumed = true,
            accountId = null,
            isHydrated = true,
        )

        assertNull(beforeHydration)
        assertEquals(SettingsInitialSyncAction.JOIN, afterHydration)
        assertNull(afterConsumption)
    }

    @Test
    fun resolve_hydrationTransitionWithBoundAccountNeverOpens() {
        val beforeHydration = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.CREATE,
            alreadyConsumed = false,
            accountId = "family-a",
            isHydrated = false,
        )
        val afterHydration = SettingsInitialSyncActionPolicy.resolve(
            initialAction = SettingsInitialSyncAction.CREATE,
            alreadyConsumed = false,
            accountId = "family-a",
            isHydrated = true,
        )

        assertNull(beforeHydration)
        assertNull(afterHydration)
    }
}
