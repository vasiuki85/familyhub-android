package com.familyhub.domain.utils

import com.familyhub.domain.model.MemberRole

internal object SyncJoinPolicy {
    fun shouldTreatMissingMemberAsRemoval(currentRole: MemberRole?): Boolean =
        currentRole != null && currentRole != MemberRole.PENDING

    fun canReadFamilySnapshot(currentRole: MemberRole?, remoteSelfRole: MemberRole?): Boolean =
        currentRole != MemberRole.PENDING || (remoteSelfRole != null && remoteSelfRole != MemberRole.PENDING)

    fun resolveLocalRole(
        existingRole: MemberRole?,
        isCreatingFamily: Boolean,
        isJoining: Boolean
    ): MemberRole = when {
        isCreatingFamily -> MemberRole.ADMIN
        isJoining -> MemberRole.PENDING
        else -> existingRole ?: MemberRole.PENDING
    }

    fun <T> resolveMutableSnapshotValue(local: T, remote: T, hasPendingChanges: Boolean): T =
        if (hasPendingChanges) local else remote
}
