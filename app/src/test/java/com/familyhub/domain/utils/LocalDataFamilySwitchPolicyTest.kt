package com.familyhub.domain.utils

import com.familyhub.domain.model.FamilyBindingMode
import com.familyhub.domain.model.LocalDataFamilyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataFamilySwitchPolicyTest {

    @Test
    fun ownerA_targetBJoin_wipes() {
        assertEquals(
            LocalDataFamilyAction.WIPE,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = "family-a",
                storedAccountId = "family-a",
                targetFamilyId = "family-b",
                mode = FamilyBindingMode.JOIN_EXISTING
            )
        )
    }

    @Test
    fun ownerA_targetBCreate_wipes() {
        assertEquals(
            LocalDataFamilyAction.WIPE,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = "family-a",
                storedAccountId = "family-a",
                targetFamilyId = "family-b",
                mode = FamilyBindingMode.CREATE_NEW
            )
        )
    }

    @Test
    fun ownerA_targetA_keeps() {
        assertEquals(
            LocalDataFamilyAction.KEEP,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = "family-a",
                storedAccountId = "family-a",
                targetFamilyId = "family-a",
                mode = FamilyBindingMode.JOIN_EXISTING
            )
        )
    }

    @Test
    fun ownerNull_storedA_targetA_legacyAdopts() {
        assertEquals(
            LocalDataFamilyAction.ADOPT,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = null,
                storedAccountId = "family-a",
                targetFamilyId = "family-a",
                mode = FamilyBindingMode.RESUME_STORED
            )
        )
    }

    @Test
    fun ownerNull_joinB_wipes() {
        assertEquals(
            LocalDataFamilyAction.WIPE,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = null,
                storedAccountId = null,
                targetFamilyId = "family-b",
                mode = FamilyBindingMode.JOIN_EXISTING
            )
        )
    }

    @Test
    fun ownerNull_createB_adopts() {
        assertEquals(
            LocalDataFamilyAction.ADOPT,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = null,
                storedAccountId = null,
                targetFamilyId = "family-b",
                mode = FamilyBindingMode.CREATE_NEW
            )
        )
    }

    @Test
    fun resumeStored_ownerMismatch_refuses() {
        assertEquals(
            LocalDataFamilyAction.REFUSE,
            LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
                localDataFamilyId = "family-a",
                storedAccountId = "family-a",
                targetFamilyId = "family-b",
                mode = FamilyBindingMode.RESUME_STORED
            )
        )
    }

    @Test
    fun canEnableSync_rejectsOwnerMismatchWithoutPrepare() {
        assertFalse(
            LocalDataFamilySwitchPolicy.canEnableSync(
                localDataFamilyId = "family-a",
                accountId = "family-b",
                storedAccountId = "family-b"
            )
        )
    }

    @Test
    fun canEnableSync_rejectsNullOwnerAndNullStored() {
        assertFalse(
            LocalDataFamilySwitchPolicy.canEnableSync(
                localDataFamilyId = null,
                accountId = "family-b",
                storedAccountId = null
            )
        )
    }

    @Test
    fun canEnableSync_allowsLegacyStoredMatch() {
        assertTrue(
            LocalDataFamilySwitchPolicy.canEnableSync(
                localDataFamilyId = null,
                accountId = "family-a",
                storedAccountId = "family-a"
            )
        )
    }

    @Test
    fun canEnableSync_rejectsLegacyStoredMismatch() {
        assertFalse(
            LocalDataFamilySwitchPolicy.canEnableSync(
                localDataFamilyId = null,
                accountId = "family-b",
                storedAccountId = "family-a"
            )
        )
    }

    @Test
    fun syncExportGuard_blocksMismatchBeforeExport() {
        assertEquals(
            LocalDataFamilySwitchPolicy.SyncExportGuardResult.Block,
            LocalDataFamilySwitchPolicy.resolveSyncExportGuard("family-a", "family-b")
        )
    }

    @Test
    fun syncExportGuard_legacyAdoptsWhenOwnerMissing() {
        assertEquals(
            LocalDataFamilySwitchPolicy.SyncExportGuardResult.LegacyAdopt("family-a"),
            LocalDataFamilySwitchPolicy.resolveSyncExportGuard(null, "family-a")
        )
    }

    @Test
    fun syncExportGuard_allowsMatchingOwner() {
        assertEquals(
            LocalDataFamilySwitchPolicy.SyncExportGuardResult.Allow,
            LocalDataFamilySwitchPolicy.resolveSyncExportGuard("family-a", "family-a")
        )
    }
}
