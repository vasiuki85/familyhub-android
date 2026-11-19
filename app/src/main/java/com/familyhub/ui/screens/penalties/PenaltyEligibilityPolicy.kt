package com.familyhub.ui.screens.penalties

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole

sealed interface PenaltyEligibilityResult {
    data object Eligible : PenaltyEligibilityResult
    data object ActorNotAllowed : PenaltyEligibilityResult
    data object TargetMissing : PenaltyEligibilityResult
    data object TargetPending : PenaltyEligibilityResult
}

object PenaltyEligibilityPolicy {

    fun canManage(role: MemberRole?): Boolean =
        role == MemberRole.ADMIN || role == MemberRole.PARENT

    fun eligibleTargets(members: List<FamilyMember>): List<FamilyMember> =
        members
            .asSequence()
            .filter { it.id.isNotBlank() && it.role != MemberRole.PENDING }
            .distinctBy { it.id }
            .toList()

    fun validate(
        actorRole: MemberRole?,
        target: FamilyMember?,
    ): PenaltyEligibilityResult = when {
        !canManage(actorRole) -> PenaltyEligibilityResult.ActorNotAllowed
        target == null || target.id.isBlank() -> PenaltyEligibilityResult.TargetMissing
        target.role == MemberRole.PENDING -> PenaltyEligibilityResult.TargetPending
        else -> PenaltyEligibilityResult.Eligible
    }
}
