package com.familyhub.data.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBindingPolicyTest {

    @Test
    fun restoreWithFamilyId_disablesCloudAndSetsOwner() {
        val outcome = RestoreBindingPolicy.afterSuccessfulRestore("family-a")
        assertFalse(outcome.syncEnabled)
        assertTrue(outcome.clearAccountId)
        assertTrue(outcome.clearDeviceId)
        assertTrue(outcome.clearLastSyncAt)
        assertTrue(outcome.clearLastError)
        assertTrue(outcome.clearTombstones)
        assertFalse(outcome.hasPendingChanges)
        assertEquals("family-a", outcome.localDataFamilyId)
        assertTrue(outcome.preserveGoogleCalendarId)
        assertFalse(outcome.allowAutomaticUpload)
    }

    @Test
    fun restoreLocalOnly_clearsOwner() {
        val outcome = RestoreBindingPolicy.afterSuccessfulRestore(null)
        assertNull(outcome.localDataFamilyId)
        assertFalse(outcome.syncEnabled)
        assertFalse(outcome.allowAutomaticUpload)
    }

    @Test
    fun sameFamilyBackup_stillClearsCloudBinding() {
        val outcome = RestoreBindingPolicy.afterSuccessfulRestore("family-current")
        assertTrue(outcome.clearAccountId)
        assertFalse(outcome.syncEnabled)
    }
}
