package com.familyhub.data.sync

import com.familyhub.domain.model.SyncSettings
import com.familyhub.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateReducerTest {

    @Test
    fun applyHydratedSettings_preservesIsSyncingAndSetsHydrated() {
        val current = SyncState(isSyncing = true, isHydrated = false)
        val settings = SyncSettings(
            isEnabled = true,
            accountId = "family-a",
            deviceId = "device-1",
            lastSyncAt = 1_700_000_000_000L,
            hasPendingChanges = true,
            lastError = "stale",
            localDataFamilyId = "family-a",
        )

        val result = SyncStateReducer.applyHydratedSettings(current, settings)

        assertTrue(result.isHydrated)
        assertTrue(result.isSyncing)
        assertTrue(result.isEnabled)
        assertEquals("family-a", result.accountId)
        assertEquals("device-1", result.deviceId)
        assertEquals(1_700_000_000_000L, result.lastSyncAt)
        assertTrue(result.hasPendingChanges)
        assertEquals("stale", result.lastError)
        assertEquals("family-a", result.localDataFamilyId)
        assertEquals(false, result.restoreInProgress)
        assertEquals(null, result.restoreBarrierFingerprint)
    }
}
