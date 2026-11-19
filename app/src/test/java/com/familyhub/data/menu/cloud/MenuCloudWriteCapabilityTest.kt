package com.familyhub.data.menu.cloud

import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCloudWriteCapabilityTest {

    private val userId = "user-1"

    @Test
    fun remoteAdmin_canUpload() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = true,
            currentUserId = userId,
            remoteSelfMember = member(userId, MemberRole.ADMIN),
            localSelfMember = member(userId, MemberRole.CHILD),
        )
        assertTrue(result.canUploadMenu)
        assertNull(result.readOnlyReason)
    }

    @Test
    fun remoteParent_canUploadRegardlessOfLocalRole() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = true,
            currentUserId = userId,
            remoteSelfMember = member(userId, MemberRole.PARENT),
            localSelfMember = member(userId, MemberRole.CHILD),
        )
        assertTrue(result.canUploadMenu)
        assertNull(result.readOnlyReason)
    }

    @Test
    fun remoteChild_readOnly() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = true,
            currentUserId = userId,
            remoteSelfMember = member(userId, MemberRole.CHILD),
            localSelfMember = member(userId, MemberRole.ADMIN),
        )
        assertFalse(result.canUploadMenu)
        assertEquals(MenuCloudReadOnlyReason.REMOTE_CHILD_ROLE, result.readOnlyReason)
    }

    @Test
    fun remotePending_readOnly() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = true,
            currentUserId = userId,
            remoteSelfMember = member(userId, MemberRole.PENDING),
            localSelfMember = member(userId, MemberRole.ADMIN),
        )
        assertFalse(result.canUploadMenu)
        assertEquals(MenuCloudReadOnlyReason.REMOTE_PENDING_ROLE, result.readOnlyReason)
    }

    @Test
    fun remoteMissingMember_readOnly() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = true,
            currentUserId = userId,
            remoteSelfMember = null,
            localSelfMember = member(userId, MemberRole.ADMIN),
        )
        assertFalse(result.canUploadMenu)
        assertEquals(MenuCloudReadOnlyReason.REMOTE_MEMBER_MISSING, result.readOnlyReason)
    }

    @Test
    fun bootstrapLocalAdmin_canUploadWhenRemoteAbsent() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = false,
            currentUserId = userId,
            remoteSelfMember = null,
            localSelfMember = member(userId, MemberRole.ADMIN),
        )
        assertTrue(result.canUploadMenu)
        assertNull(result.readOnlyReason)
    }

    @Test
    fun bootstrapLocalChild_readOnlyWhenRemoteAbsent() {
        val result = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = false,
            currentUserId = userId,
            remoteSelfMember = null,
            localSelfMember = member(userId, MemberRole.CHILD),
        )
        assertFalse(result.canUploadMenu)
        assertEquals(MenuCloudReadOnlyReason.BOOTSTRAP_REQUIRES_LOCAL_ADMIN, result.readOnlyReason)
    }

    private fun member(userId: String, role: MemberRole) = MenuCloudMemberRef(userId, role)
}
