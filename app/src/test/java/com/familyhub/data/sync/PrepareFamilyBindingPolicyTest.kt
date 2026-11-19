package com.familyhub.data.sync

import com.familyhub.data.datastore.normalizeFamilyScopedDeletedEntries
import com.familyhub.data.datastore.readDeletedDocumentIdsForFamily
import com.familyhub.domain.model.FamilyBindingMode
import com.familyhub.domain.model.LocalDataFamilyAction
import com.familyhub.domain.utils.LocalDataFamilySwitchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareFamilyBindingPolicyTest {

    @Test
    fun removeMemberAfterMigration_clearsRawBeforeAccountSwitch() {
        val storedAccount = "family-a"
        val rawEntries = setOf("member-1")
        val normalized = normalizeFamilyScopedDeletedEntries(rawEntries, storedAccount)
        val afterRemove = normalized.filterNot {
            readDeletedDocumentIdsForFamily(setOf(it), storedAccount).contains("member-1")
        }.toSet()

        assertTrue(afterRemove.isEmpty())
        val afterSwitch = normalizeFamilyScopedDeletedEntries(afterRemove, storedAccount)
        assertTrue(readDeletedDocumentIdsForFamily(afterSwitch, "family-b").isEmpty())
    }

    @Test
    fun storedA_operationForB_doesNotAssignLegacyToB() {
        val action = LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
            localDataFamilyId = null,
            storedAccountId = "family-a",
            targetFamilyId = "family-b",
            mode = FamilyBindingMode.JOIN_EXISTING
        )
        assertEquals(LocalDataFamilyAction.WIPE, action)
    }

    @Test
    fun resumeSameOwner_keepsWithoutWipe() {
        val action = LocalDataFamilySwitchPolicy.resolveLocalDataFamilyAction(
            localDataFamilyId = "family-a",
            storedAccountId = "family-a",
            targetFamilyId = "family-a",
            mode = FamilyBindingMode.RESUME_STORED
        )
        assertEquals(LocalDataFamilyAction.KEEP, action)
    }
}
