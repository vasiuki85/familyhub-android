package com.familyhub.ui.screens.settings

import com.familyhub.R
import com.familyhub.data.sync.SyncFamilyInvariantException
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSyncErrorMapperTest {
    @Test
    fun multipleAdmins_usesReviewedLocalizedResource() {
        assertEquals(
            R.string.settings_error_multiple_admins,
            SettingsSyncErrorMapper.messageRes(
                SyncFamilyInvariantException.MultipleAdmins()
            ),
        )
    }

    @Test
    fun missingAdmin_usesReviewedLocalizedResource() {
        assertEquals(
            R.string.settings_error_no_admin,
            SettingsSyncErrorMapper.messageRes(
                SyncFamilyInvariantException.MissingAdmin()
            ),
        )
    }

    @Test
    fun invalidAdminCount_usesReviewedLocalizedResource() {
        assertEquals(
            R.string.settings_error_invalid_admin_count,
            SettingsSyncErrorMapper.messageRes(
                SyncFamilyInvariantException.InvalidAdminCount()
            ),
        )
    }

    @Test
    fun unknownFailure_neverReturnsRawThrowableText() {
        val raw = IllegalStateException("Multiple admins detected")

        assertEquals(
            R.string.settings_sync_safe_error,
            SettingsSyncErrorMapper.messageRes(raw),
        )
    }
}
