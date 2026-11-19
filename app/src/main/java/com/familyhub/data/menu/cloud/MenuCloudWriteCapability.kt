package com.familyhub.data.menu.cloud

import com.familyhub.domain.model.MemberRole

/**
 * Pure cloud menu write capability.
 *
 * Independent of bulk member-write permission: PARENT may upload menu even when
 * member roster edits are restricted.
 */
object MenuCloudWriteCapability {

    fun evaluate(
        remoteFamilyPresent: Boolean,
        currentUserId: String,
        remoteSelfMember: MenuCloudMemberRef?,
        localSelfMember: MenuCloudMemberRef?,
    ): MenuCloudWriteCapabilityResult {
        if (currentUserId.isBlank()) {
            return readOnly(MenuCloudReadOnlyReason.REMOTE_MEMBER_MISSING)
        }
        if (remoteFamilyPresent) {
            val remote = remoteSelfMember?.takeIf { it.userId == currentUserId }
                ?: return readOnly(MenuCloudReadOnlyReason.REMOTE_MEMBER_MISSING)
            return when (remote.role) {
                MemberRole.ADMIN, MemberRole.PARENT -> MenuCloudWriteCapabilityResult(
                    canUploadMenu = true,
                    readOnlyReason = null,
                )
                MemberRole.CHILD -> readOnly(MenuCloudReadOnlyReason.REMOTE_CHILD_ROLE)
                MemberRole.PENDING -> readOnly(MenuCloudReadOnlyReason.REMOTE_PENDING_ROLE)
            }
        }
        val local = localSelfMember?.takeIf { it.userId == currentUserId }
        return if (local?.role == MemberRole.ADMIN) {
            MenuCloudWriteCapabilityResult(canUploadMenu = true, readOnlyReason = null)
        } else {
            readOnly(MenuCloudReadOnlyReason.BOOTSTRAP_REQUIRES_LOCAL_ADMIN)
        }
    }

    private fun readOnly(reason: MenuCloudReadOnlyReason) = MenuCloudWriteCapabilityResult(
        canUploadMenu = false,
        readOnlyReason = reason,
    )
}
