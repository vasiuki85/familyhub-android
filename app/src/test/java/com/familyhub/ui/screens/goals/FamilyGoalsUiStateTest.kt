package com.familyhub.ui.screens.goals

import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGoalsUiStateTest {

    private val ownGoal = FamilyGoal(
        id = "goal-own",
        title = "Own",
        description = null,
        targetPoints = 1,
        currentPoints = 0,
        deadlineAt = null,
        status = FamilyGoalStatus.ACTIVE,
        createdByUserId = "me",
        createdAt = 1L,
        updatedAt = 1L,
        isPersonal = false,
    )

    @Test
    fun child_canCreateAndManageOwnGoalOnly() {
        val state = FamilyGoalsUiState(
            currentUserId = "me",
            currentUserRole = MemberRole.CHILD,
        )

        assertTrue(state.canCreateGoals)
        assertTrue(state.canManageGoal(ownGoal))
        assertFalse(state.canManageGoal(ownGoal.copy(createdByUserId = "other")))
    }

    @Test
    fun adminAndParent_canManageAnotherUsersGoal() {
        val anotherGoal = ownGoal.copy(createdByUserId = "other")

        assertTrue(
            FamilyGoalsUiState(currentUserRole = MemberRole.ADMIN)
                .canManageGoal(anotherGoal)
        )
        assertTrue(
            FamilyGoalsUiState(currentUserRole = MemberRole.PARENT)
                .canManageGoal(anotherGoal)
        )
    }

    @Test
    fun pending_cannotCreateOrManageGoals() {
        val state = FamilyGoalsUiState(
            currentUserId = "me",
            currentUserRole = MemberRole.PENDING,
        )

        assertFalse(state.canCreateGoals)
        assertFalse(state.canManageGoal(ownGoal))
    }
}
