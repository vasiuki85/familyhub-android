package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskHistory
import com.familyhub.data.export.ExportWeeklyMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerVisibilityTest {

    private val merger = SyncMerger()
    private val currentUserId = "user-1"

    @Test
    fun mergeSnapshots_preservesBackupOnlyArraysFromLocalWhenRemoteExists() {
        val local = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl-1", name = "Weekday", createdAt = 1L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(
                    id = "te-1",
                    templateId = "tpl-1",
                    dayOfWeek = 1,
                    mealType = "LUNCH",
                    servings = 2
                )
            ),
            weeklyMenus = listOf(ExportWeeklyMenu(id = "menu-1", startDateMillis = 1000L)),
            menuEntries = listOf(ExportMenuEntry(id = "entry-1", menuId = "menu-1", dayOfWeek = 1)),
            taskHistory = listOf(
                ExportTaskHistory(
                    id = "hist-1",
                    taskId = "task-1",
                    changedByUserId = currentUserId,
                    changeType = "created",
                    changeDescription = "Created",
                    timestamp = 1L
                )
            ),
            dailyAssignments = listOf(
                ExportDailyAssignment(id = "assign-1", weekStartMillis = 1000L, dayOfWeek = 1)
            )
        )
        val remote = ExportData(version = 1L)

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true
        )

        assertEquals(local.menuTemplates, merged.menuTemplates)
        assertEquals(local.menuTemplateEntries, merged.menuTemplateEntries)
        assertEquals(local.weeklyMenus, merged.weeklyMenus)
        assertEquals(local.menuEntries, merged.menuEntries)
        assertEquals(local.taskHistory, merged.taskHistory)
        assertEquals(local.dailyAssignments, merged.dailyAssignments)
    }

    @Test
    fun mergeSnapshots_preservesLocalMenuTemplatesWhenRemoteEmpty_andRepeatedMergeIsIdempotent() {
        val local = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl-1", name = "Weekday", createdAt = 1L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(
                    id = "te-1",
                    templateId = "tpl-1",
                    dayOfWeek = 1,
                    mealType = "LUNCH",
                    servings = 2
                )
            )
        )
        val remote = ExportData(version = 1L)

        val mergedOnce = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true
        )
        val mergedTwice = merger.mergeSnapshots(
            local = mergedOnce,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true
        )

        assertEquals(local.menuTemplates, mergedOnce.menuTemplates)
        assertEquals(local.menuTemplateEntries, mergedOnce.menuTemplateEntries)
        assertEquals(
            merger.normalizedForComparison(mergedOnce),
            merger.normalizedForComparison(mergedTwice)
        )
    }

    @Test
    fun filterLocalSnapshotForMerge_excludesForeignPrivateTask() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(id = "public", title = "Public", private = false),
                ExportTask(
                    id = "foreign-private",
                    title = "Secret",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = "other"
                ),
                ExportTask(
                    id = "own-private",
                    title = "Mine",
                    private = true,
                    createdByUserId = currentUserId,
                    assignedToUserId = currentUserId
                ),
                ExportTask(
                    id = "assigned-private",
                    title = "Assigned",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = currentUserId
                )
            )
        )

        val filtered = filterLocalSnapshotForMerge(local, currentUserId)

        assertEquals(
            setOf("public", "own-private", "assigned-private"),
            filtered.tasks.map { it.id }.toSet()
        )
    }

    @Test
    fun mergeSnapshots_doesNotReturnForeignPrivateTaskInMergedResult() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "foreign-private",
                    title = "Secret",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = "other"
                )
            )
        )
        val remote = ExportData(
            tasks = listOf(
                ExportTask(id = "public", title = "Public", private = false)
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertFalse(merged.tasks.any { it.id == "foreign-private" })
        assertTrue(merged.tasks.any { it.id == "public" })
    }

    @Test
    fun mergeSnapshots_whenRemoteNull_filtersForeignPrivateTasks() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "foreign-private",
                    title = "Secret",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = "other"
                ),
                ExportTask(id = "public", title = "Public", private = false)
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = null,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true
        )

        assertFalse(merged.tasks.any { it.id == "foreign-private" })
        assertTrue(merged.tasks.any { it.id == "public" })
    }

    @Test
    fun mergeSnapshots_keepsOwnPrivateLocalOnlyWhenRemoteEmpty() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "own-private",
                    title = "Mine",
                    private = true,
                    createdByUserId = currentUserId,
                    assignedToUserId = currentUserId
                )
            )
        )
        val remote = ExportData()

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertTrue(merged.tasks.any { it.id == "own-private" })
    }

    @Test
    fun mergeSnapshots_dropsStaleAssignedPrivateWhenAbsentFromRemote() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "assigned-private",
                    title = "Assigned",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = currentUserId
                )
            )
        )
        val remote = ExportData()

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )

        assertFalse(merged.tasks.any { it.id == "assigned-private" })
    }

    @Test
    fun mergeSnapshots_mergesAssignedPrivateWhenPresentInRemote() {
        val local = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "assigned-private",
                    title = "Local title",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = currentUserId,
                    status = "TODO"
                )
            )
        )
        val remote = ExportData(
            tasks = listOf(
                ExportTask(
                    id = "assigned-private",
                    title = "Remote title",
                    private = true,
                    createdByUserId = "other",
                    assignedToUserId = currentUserId,
                    status = "WAITING_FOR_APPROVAL",
                    requiresApproval = true
                )
            )
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = currentUserId,
            isMemberWriteAllowed = false,
            hasPendingChanges = false
        )

        assertEquals(1, merged.tasks.size)
        assertEquals("assigned-private", merged.tasks.single().id)
        assertEquals("WAITING_FOR_APPROVAL", merged.tasks.single().status)
    }

    @Test
    fun filterLocalSnapshotForMerge_excludesForeignPersonalGoalAndChildren() {
        val local = ExportData(
            familyGoals = listOf(
                ExportFamilyGoal(id = "shared", title = "Shared", personal = false),
                ExportFamilyGoal(
                    id = "own-personal",
                    title = "Mine",
                    personal = true,
                    createdByUserId = currentUserId
                ),
                ExportFamilyGoal(
                    id = "foreign-personal",
                    title = "Hidden",
                    personal = true,
                    createdByUserId = "other"
                )
            ),
            familyGoalStages = listOf(
                ExportFamilyGoalStage(id = "stage-shared", goalId = "shared", title = "S1"),
                ExportFamilyGoalStage(id = "stage-own", goalId = "own-personal", title = "S2"),
                ExportFamilyGoalStage(id = "stage-foreign", goalId = "foreign-personal", title = "S3")
            ),
            familyGoalSteps = listOf(
                ExportFamilyGoalStep(id = "step-shared", goalId = "shared", stageId = "stage-shared", title = "Step"),
                ExportFamilyGoalStep(id = "step-own", goalId = "own-personal", stageId = "stage-own", title = "Step"),
                ExportFamilyGoalStep(id = "step-foreign", goalId = "foreign-personal", stageId = "stage-foreign", title = "Step")
            )
        )

        val filtered = filterLocalSnapshotForMerge(local, currentUserId)

        assertEquals(setOf("shared", "own-personal"), filtered.familyGoals.map { it.id }.toSet())
        assertEquals(setOf("stage-shared", "stage-own"), filtered.familyGoalStages.map { it.id }.toSet())
        assertEquals(setOf("step-shared", "step-own"), filtered.familyGoalSteps.map { it.id }.toSet())
    }
}
