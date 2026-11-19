package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.domain.model.TaskTransitionInvalidException
import com.familyhub.domain.utils.WeekUtils
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncMergerCycleValidationTest {

    private val merger = SyncMerger()
    private val userId = "child"
    private val taskId = "task-1"
    private val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)

    @Test
    fun mergeSnapshots_rejectsNegativeLocalTaskCycle() {
        val local = snapshot(tasks = listOf(task(cycle = -1L)))
        val remote = snapshot()

        assertThrows(TaskTransitionInvalidException::class.java) {
            merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsNegativeRemoteTaskCycle() {
        val local = snapshot()
        val remote = snapshot(tasks = listOf(task(cycle = -1L)))

        assertThrows(TaskTransitionInvalidException::class.java) {
            merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsNegativeLocalCompletionCycle() {
        val local = snapshot(
            completions = listOf(completion(cycle = -1L))
        )
        val remote = snapshot()

        assertThrows(TaskTransitionInvalidException::class.java) {
            merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_rejectsNegativeRemoteCompletionCycle() {
        val local = snapshot()
        val remote = snapshot(
            completions = listOf(completion(cycle = -1L))
        )

        assertThrows(TaskTransitionInvalidException::class.java) {
            merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        }
    }

    @Test
    fun mergeSnapshots_validatesLocalCyclesWhenRemoteIsNull() {
        val local = snapshot(tasks = listOf(task(cycle = -1L)))

        assertThrows(TaskTransitionInvalidException::class.java) {
            merger.mergeSnapshots(local, remote = null, userId, isMemberWriteAllowed = true)
        }
    }

    private fun snapshot(
        tasks: List<ExportTask> = listOf(task(cycle = 0L)),
        completions: List<ExportTaskCompletion> = emptyList()
    ) = ExportData(
        tasks = tasks,
        taskCompletions = completions
    )

    private fun task(cycle: Long) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = userId,
        status = "TODO",
        completionCycle = cycle
    )

    private fun completion(cycle: Long) = ExportTaskCompletion(
        id = "completion-1",
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = 1L,
        weekStartDate = weekStart,
        basePoints = 10,
        totalPoints = 10,
        updatedAt = 1L,
        completionCycle = cycle
    )
}
