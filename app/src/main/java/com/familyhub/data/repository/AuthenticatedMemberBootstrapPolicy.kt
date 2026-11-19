package com.familyhub.data.repository

import com.familyhub.domain.model.MemberRole

/**
 * Pure policy for authenticated-member bootstrap after sign-in or on startup.
 */
object AuthenticatedMemberBootstrapPolicy {
    sealed class Action {
        data object NoOp : Action()
        data object ClearStaleSuppression : Action()
        data class CreateMember(val role: MemberRole) : Action()
    }

    fun resolve(
        authenticatedUid: String?,
        membersContainUid: Boolean,
        memberCount: Int,
        accountId: String?,
        localDataFamilyId: String?,
        suppressAuthMemberBootstrap: Boolean,
        restoreInProgress: Boolean
    ): Action {
        if (authenticatedUid.isNullOrBlank()) return Action.NoOp
        if (membersContainUid) {
            return if (
                suppressAuthMemberBootstrap &&
                !restoreInProgress
            ) {
                Action.ClearStaleSuppression
            } else {
                Action.NoOp
            }
        }
        if (restoreInProgress) return Action.NoOp
        if (suppressAuthMemberBootstrap) return Action.NoOp
        return Action.CreateMember(roleForMissingUid(memberCount, accountId, localDataFamilyId))
    }

    internal fun roleForMissingUid(
        memberCount: Int,
        accountId: String?,
        localDataFamilyId: String?
    ): MemberRole {
        if (memberCount > 0) return MemberRole.PENDING
        val cloudBound = !accountId.isNullOrBlank() || !localDataFamilyId.isNullOrBlank()
        return if (cloudBound) MemberRole.PENDING else MemberRole.ADMIN
    }
}
