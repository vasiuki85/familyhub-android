package com.familyhub.data.sync

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportTask
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryFamilyClearTest {

    @Test
    fun buildClearedFamilySnapshot_keepsEmptyMembersWhenKeepUserMissing() {
        val local = ExportData(
            members = listOf(
                exportMember("admin-1", MemberRole.ADMIN),
                exportMember("child-1", MemberRole.CHILD)
            ),
            tasks = listOf(ExportTask(id = "task-1", title = "Wash dishes")),
            rewards = listOf(ExportReward(id = "reward-1", title = "Ice cream", cost = 10))
        )

        val cleared = SyncRepository.buildClearedFamilySnapshot(local, "missing-user")

        assertTrue(cleared.members.isEmpty())
        assertTrue(cleared.tasks.isEmpty())
        assertTrue(cleared.rewards.isEmpty())
    }

    @Test
    fun buildClearedFamilySnapshot_demotesOwnAdminToPending() {
        val local = ExportData(
            members = listOf(
                exportMember("self-1", MemberRole.ADMIN),
                exportMember("child-1", MemberRole.CHILD)
            )
        )

        val cleared = SyncRepository.buildClearedFamilySnapshot(local, "self-1")

        assertEquals(1, cleared.members.size)
        assertEquals("self-1", cleared.members.single().id)
        assertEquals(MemberRole.PENDING.name, cleared.members.single().role)
    }

    @Test
    fun buildClearedFamilySnapshot_keepsOnlyPersonalGoalsForKeepUser() {
        val local = ExportData(
            members = listOf(exportMember("self-1", MemberRole.ADMIN)),
            familyGoals = listOf(
                ExportFamilyGoal(
                    id = "goal-personal",
                    title = "My goal",
                    createdByUserId = "self-1",
                    personal = true
                ),
                ExportFamilyGoal(
                    id = "goal-family",
                    title = "Family goal",
                    createdByUserId = "other-1",
                    personal = false
                ),
                ExportFamilyGoal(
                    id = "goal-other-personal",
                    title = "Other personal",
                    createdByUserId = "other-1",
                    personal = true
                )
            ),
            familyGoalStages = listOf(
                ExportFamilyGoalStage(id = "stage-1", goalId = "goal-personal", title = "Stage"),
                ExportFamilyGoalStage(id = "stage-2", goalId = "goal-family", title = "Family stage")
            ),
            familyGoalSteps = listOf(
                ExportFamilyGoalStep(id = "step-1", goalId = "goal-personal", stageId = "stage-1", title = "Step"),
                ExportFamilyGoalStep(id = "step-2", goalId = "goal-family", stageId = "stage-2", title = "Family step")
            )
        )

        val cleared = SyncRepository.buildClearedFamilySnapshot(local, "self-1")

        assertEquals(listOf("goal-personal"), cleared.familyGoals.map { it.id })
        assertEquals(listOf("stage-1"), cleared.familyGoalStages.map { it.id })
        assertEquals(listOf("step-1"), cleared.familyGoalSteps.map { it.id })
        assertFalse(cleared.familyGoals.any { it.id == "goal-family" })
    }

    private fun exportMember(id: String, role: MemberRole) = ExportMember(
        id = id,
        displayName = id,
        roleLabel = role.name,
        avatarColor = "#2196F3",
        role = role.name
    )
}
