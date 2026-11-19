package com.familyhub.domain.utils

import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncJoinPolicyTest {
    @Test
    fun `missing pending member is ambiguous and must not erase local data`() {
        assertFalse(SyncJoinPolicy.shouldTreatMissingMemberAsRemoval(MemberRole.PENDING))
        assertTrue(SyncJoinPolicy.shouldTreatMissingMemberAsRemoval(MemberRole.CHILD))
    }

    @Test
    fun `pending member waits for own remote role before reading family`() {
        assertFalse(SyncJoinPolicy.canReadFamilySnapshot(MemberRole.PENDING, null))
        assertFalse(SyncJoinPolicy.canReadFamilySnapshot(MemberRole.PENDING, MemberRole.PENDING))
        assertTrue(SyncJoinPolicy.canReadFamilySnapshot(MemberRole.PENDING, MemberRole.CHILD))
    }

    @Test
    fun `local role follows create join and resume intent`() {
        assertEquals(MemberRole.ADMIN, SyncJoinPolicy.resolveLocalRole(MemberRole.CHILD, true, false))
        assertEquals(MemberRole.PENDING, SyncJoinPolicy.resolveLocalRole(MemberRole.ADMIN, false, true))
        assertEquals(MemberRole.CHILD, SyncJoinPolicy.resolveLocalRole(MemberRole.CHILD, false, false))
        assertEquals(MemberRole.PENDING, SyncJoinPolicy.resolveLocalRole(null, false, false))
    }

    @Test
    fun `mutable values can decrease when local changes are pending`() {
        assertEquals(20, SyncJoinPolicy.resolveMutableSnapshotValue(20, 100, hasPendingChanges = true))
        assertEquals(20, SyncJoinPolicy.resolveMutableSnapshotValue(100, 20, hasPendingChanges = false))
    }
}
