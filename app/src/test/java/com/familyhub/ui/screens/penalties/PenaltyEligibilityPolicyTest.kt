package com.familyhub.ui.screens.penalties

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenaltyEligibilityPolicyTest {

    @Test
    fun canManage_onlyAdminAndParent() {
        assertTrue(PenaltyEligibilityPolicy.canManage(MemberRole.ADMIN))
        assertTrue(PenaltyEligibilityPolicy.canManage(MemberRole.PARENT))
        assertFalse(PenaltyEligibilityPolicy.canManage(MemberRole.CHILD))
        assertFalse(PenaltyEligibilityPolicy.canManage(MemberRole.PENDING))
        assertFalse(PenaltyEligibilityPolicy.canManage(null))
    }

    @Test
    fun eligibleTargets_excludesPendingBlankAndDuplicateIds() {
        val child = member("child", MemberRole.CHILD)
        val parent = member("parent", MemberRole.PARENT)

        assertEquals(
            listOf(child, parent),
            PenaltyEligibilityPolicy.eligibleTargets(
                listOf(
                    child,
                    member("pending", MemberRole.PENDING),
                    member("", MemberRole.CHILD),
                    child.copy(displayName = "Duplicate"),
                    parent,
                ),
            ),
        )
    }

    @Test
    fun validate_failsClosedForMissingAndPendingTargets() {
        assertEquals(
            PenaltyEligibilityResult.TargetMissing,
            PenaltyEligibilityPolicy.validate(MemberRole.ADMIN, null),
        )
        assertEquals(
            PenaltyEligibilityResult.TargetPending,
            PenaltyEligibilityPolicy.validate(
                MemberRole.PARENT,
                member("pending", MemberRole.PENDING),
            ),
        )
    }

    @Test
    fun validate_actorDenialTakesPriority() {
        assertEquals(
            PenaltyEligibilityResult.ActorNotAllowed,
            PenaltyEligibilityPolicy.validate(
                MemberRole.CHILD,
                member("child", MemberRole.CHILD),
            ),
        )
        assertEquals(
            PenaltyEligibilityResult.Eligible,
            PenaltyEligibilityPolicy.validate(
                MemberRole.ADMIN,
                member("child", MemberRole.CHILD),
            ),
        )
    }

    private fun member(id: String, role: MemberRole): FamilyMember = FamilyMember(
        id = id,
        displayName = id,
        roleLabel = role.name,
        avatarColor = "#000000",
        role = role,
    )
}
