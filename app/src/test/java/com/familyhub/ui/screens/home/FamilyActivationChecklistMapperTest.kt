package com.familyhub.ui.screens.home

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyActivationChecklistMapperTest {
    @Test fun ineligibleStates_areHidden() {
        assertFalse(map(hydrated = false, role = MemberRole.ADMIN).visible)
        assertFalse(map(role = MemberRole.PENDING).visible)
        assertFalse(map(role = MemberRole.CHILD).visible)
        assertFalse(map(accountId = null).visible)
    }

    @Test fun adminWithoutMember_routesToSettings() {
        val model = map()
        assertTrue(model.visible)
        assertFalse(model.memberStepComplete)
        assertEquals(FamilyActivationAction.SETTINGS, model.action)
    }

    @Test fun pendingMember_staysOnSettingsUntilApproval() {
        val model = map(members = listOf(member(MemberRole.PENDING)))
        assertTrue(model.visible)
        assertTrue(model.hasPendingMember)
        assertEquals(FamilyActivationAction.SETTINGS, model.action)
    }

    @Test fun approvedMemberWithoutTask_routesToTasks() {
        val model = map(members = listOf(member(MemberRole.CHILD)))

        assertTrue(model.visible)
        assertTrue(model.memberStepComplete)
        assertFalse(model.taskStepComplete)
        assertEquals(FamilyActivationAction.TASKS, model.action)
    }

    @Test fun memberAndTaskComplete_hidesChecklist() {
        val model = map(members = listOf(member(MemberRole.PARENT)), hasTask = true)
        assertFalse(model.visible)
    }

    private fun map(
        hydrated: Boolean = true,
        accountId: String? = "family",
        role: MemberRole = MemberRole.ADMIN,
        members: List<FamilyMember> = emptyList(),
        hasTask: Boolean = false,
    ) = FamilyActivationChecklistMapper.map(
        isHydrated = hydrated,
        accountId = accountId,
        currentUserId = "me",
        currentUserRole = role,
        members = members,
        hasAnyTask = hasTask,
    )

    private fun member(role: MemberRole) = FamilyMember(
        id = "other", displayName = "Other", roleLabel = "", avatarColor = "", role = role,
    )
}
