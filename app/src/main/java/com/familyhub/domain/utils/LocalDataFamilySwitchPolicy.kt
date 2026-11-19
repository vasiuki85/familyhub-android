package com.familyhub.domain.utils

import com.familyhub.domain.model.FamilyBindingMode
import com.familyhub.domain.model.LocalDataFamilyAction

object LocalDataFamilySwitchPolicy {

    fun resolveLocalDataFamilyAction(
        localDataFamilyId: String?,
        storedAccountId: String?,
        targetFamilyId: String,
        mode: FamilyBindingMode
    ): LocalDataFamilyAction {
        require(targetFamilyId.isNotBlank()) { "targetFamilyId must not be blank" }

        val owner = localDataFamilyId?.takeIf { it.isNotBlank() }
        val stored = storedAccountId?.takeIf { it.isNotBlank() }

        if (owner == targetFamilyId) {
            return LocalDataFamilyAction.KEEP
        }

        return when (mode) {
            FamilyBindingMode.RESUME_STORED -> when {
                owner != null -> LocalDataFamilyAction.REFUSE
                stored == targetFamilyId -> LocalDataFamilyAction.ADOPT
                else -> LocalDataFamilyAction.REFUSE
            }
            FamilyBindingMode.CREATE_NEW -> when {
                owner != null -> LocalDataFamilyAction.WIPE
                stored == targetFamilyId -> LocalDataFamilyAction.ADOPT
                stored == null -> LocalDataFamilyAction.ADOPT
                else -> LocalDataFamilyAction.WIPE
            }
            FamilyBindingMode.JOIN_EXISTING -> when {
                owner != null -> LocalDataFamilyAction.WIPE
                stored == targetFamilyId -> LocalDataFamilyAction.ADOPT
                else -> LocalDataFamilyAction.WIPE
            }
        }
    }

    fun canEnableSync(
        localDataFamilyId: String?,
        accountId: String,
        storedAccountId: String?
    ): Boolean {
        require(accountId.isNotBlank()) { "accountId must not be blank" }

        val owner = localDataFamilyId?.takeIf { it.isNotBlank() }
        if (owner != null) {
            return owner == accountId
        }

        val stored = storedAccountId?.takeIf { it.isNotBlank() } ?: return false
        return stored == accountId
    }

    sealed class SyncExportGuardResult {
        data object Allow : SyncExportGuardResult()
        data class LegacyAdopt(val familyId: String) : SyncExportGuardResult()
        data object Block : SyncExportGuardResult()
    }

    fun resolveSyncExportGuard(
        localDataFamilyId: String?,
        accountId: String
    ): SyncExportGuardResult {
        val owner = localDataFamilyId?.takeIf { it.isNotBlank() }
        return when {
            owner == accountId -> SyncExportGuardResult.Allow
            owner == null -> SyncExportGuardResult.LegacyAdopt(accountId)
            else -> SyncExportGuardResult.Block
        }
    }
}
