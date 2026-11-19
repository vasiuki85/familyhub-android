package com.familyhub.data.sync

import com.familyhub.domain.model.SyncSettings
import com.familyhub.domain.model.SyncState

object SyncStateReducer {
    fun applyHydratedSettings(
        current: SyncState,
        settings: SyncSettings,
    ): SyncState = current.copy(
        isEnabled = settings.isEnabled,
        accountId = settings.accountId,
        deviceId = settings.deviceId,
        lastSyncAt = settings.lastSyncAt,
        hasPendingChanges = settings.hasPendingChanges,
        lastError = settings.lastError,
        localDataFamilyId = settings.localDataFamilyId,
        restoreInProgress = settings.restoreInProgress,
        restoreBarrierFingerprint = settings.restoreBarrierFingerprint,
        isHydrated = true,
    )
}
