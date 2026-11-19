package com.familyhub.ui.screens.goals

import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyGoalsFindGoalTest {

    @Test
    fun findGoal_returnsMatchingItem() {
        val goal = FamilyGoal(
            id = "goal-42",
            title = "Test",
            description = null,
            targetPoints = 0,
            currentPoints = 0,
            deadlineAt = null,
            status = FamilyGoalStatus.ACTIVE,
            createdByUserId = "user",
            createdAt = 1L,
            updatedAt = 1L,
            isPersonal = false,
        )
        val item = FamilyGoalListItem(
            goal = goal,
            stages = emptyList(),
            creatorName = "Author",
            deadlineLabel = null,
            progressPercent = 0f,
            completedSteps = 0,
            totalSteps = 0,
            remainingSteps = 0,
            nextStepTitle = null,
        )
        val state = FamilyGoalsUiState(goals = listOf(item))

        assertEquals(item, state.findGoal("goal-42"))
        assertNull(state.findGoal("missing"))
    }
}
