package com.familyhub.ui.screens.settings

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.data.sync.SyncFamilyInvariantException

/**
 * Maps sync failures to reviewed resources without rendering backend or exception text.
 */
internal object SettingsSyncErrorMapper {
    @StringRes
    fun messageRes(throwable: Throwable?): Int {
        return when (throwable) {
            is SyncFamilyInvariantException.MultipleAdmins ->
                R.string.settings_error_multiple_admins
            is SyncFamilyInvariantException.MissingAdmin ->
                R.string.settings_error_no_admin
            is SyncFamilyInvariantException.InvalidAdminCount ->
                R.string.settings_error_invalid_admin_count
            else -> R.string.settings_sync_safe_error
        }
    }
}
