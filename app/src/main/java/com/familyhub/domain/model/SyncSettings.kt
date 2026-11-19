package com.familyhub.domain.model

data class SyncSettings(
    val isEnabled: Boolean = false,
    val accountId: String? = null,
    val deviceId: String? = null,
    val lastSyncAt: Long? = null,
    val hasPendingChanges: Boolean = false,
    val lastError: String? = null,
    val googleCalendarId: String? = null,
    val localDataFamilyId: String? = null,
    val restoreInProgress: Boolean = false,
    val restoreBarrierFingerprint: String? = null,
    val restoreBarrierSourceFamilyId: String? = null,
    /**
     * After encrypted restore without the authenticated UID in the snapshot,
     * startup must not silently upsert a local member until the user opts in.
     */
    val suppressAuthMemberBootstrap: Boolean = false
)

data class SyncState(
    val isEnabled: Boolean = false,
    val accountId: String? = null,
    val deviceId: String? = null,
    val lastSyncAt: Long? = null,
    val hasPendingChanges: Boolean = false,
    val lastError: String? = null,
    val isSyncing: Boolean = false,
    val localDataFamilyId: String? = null,
    val isHydrated: Boolean = false,
    val restoreInProgress: Boolean = false,
    val restoreBarrierFingerprint: String? = null,
)
