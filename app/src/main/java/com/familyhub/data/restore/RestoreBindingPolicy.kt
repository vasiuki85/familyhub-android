package com.familyhub.data.restore

/**
 * Pure family-binding matrix after a successful authenticated restore.
 * Cloud binding is always cleared; Room owner follows [sourceFamilyId].
 */
object RestoreBindingPolicy {
    data class Outcome(
        val syncEnabled: Boolean,
        val clearAccountId: Boolean,
        val clearDeviceId: Boolean,
        val clearLastSyncAt: Boolean,
        val clearLastError: Boolean,
        val clearTombstones: Boolean,
        val hasPendingChanges: Boolean,
        val localDataFamilyId: String?,
        val preserveGoogleCalendarId: Boolean,
        val allowAutomaticUpload: Boolean
    )

    fun afterSuccessfulRestore(sourceFamilyId: String?): Outcome =
        Outcome(
            syncEnabled = false,
            clearAccountId = true,
            clearDeviceId = true,
            clearLastSyncAt = true,
            clearLastError = true,
            clearTombstones = true,
            hasPendingChanges = false,
            localDataFamilyId = sourceFamilyId,
            preserveGoogleCalendarId = true,
            allowAutomaticUpload = false
        )
}
