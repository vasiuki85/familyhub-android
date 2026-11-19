package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.domain.utils.CompletionCycleIds
import com.familyhub.domain.utils.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergerCompletionCycleTest {

    private val merger = SyncMerger()
    private val userId = "child"
    private val taskId = "task-1"
    private val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)

    @Test
    fun mergeSnapshots_dualDeviceSameCycle_awardsOnceAfterProjection() {
        val localCompletion = completion("uuid-local", cycle = 0L, updatedAt = 100L)
        val remoteCompletion = completion("uuid-remote", cycle = 0L, updatedAt = 200L)
        val local = snapshot(completions = listOf(localCompletion), score = 10, wallet = 10, updatedAt = 100L)
        val remote = snapshot(completions = listOf(remoteCompletion), score = 10, wallet = 10, updatedAt = 200L)

        val mergedLocalFirst = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        val mergedRemoteFirst = merger.mergeSnapshots(remote, local, userId, isMemberWriteAllowed = true)

        val canonicalId = CompletionCycleIds.deterministicId(taskId, 0L)
        assertEquals(1, mergedLocalFirst.taskCompletions.size)
        assertEquals(canonicalId, mergedLocalFirst.taskCompletions.single().id)
        assertEquals(canonicalId, mergedRemoteFirst.taskCompletions.single().id)
        assertEquals(10, mergedLocalFirst.scores.single().score)
        assertEquals(10, mergedRemoteFirst.scores.single().score)
        assertEquals(10, mergedLocalFirst.wallets.single().balance)
        assertEquals(10, mergedRemoteFirst.wallets.single().balance)
    }

    @Test
    fun mergeSnapshots_doneCycle0VersusTodoCycle1_todoWins() {
        val local = snapshot(
            tasks = listOf(task(status = "DONE", cycle = 0L)),
            completions = listOf(completion("c0", cycle = 0L, updatedAt = 100L))
        )
        val remote = snapshot(
            tasks = listOf(task(status = "TODO", cycle = 1L)),
            completions = emptyList()
        )

        val merged = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)

        assertEquals("TODO", merged.tasks.single().status)
        assertEquals(1L, merged.tasks.single().completionCycle)
    }

    private fun snapshot(
        tasks: List<ExportTask> = listOf(task(status = "TODO", cycle = 0L)),
        completions: List<ExportTaskCompletion> = emptyList(),
        score: Int = 0,
        wallet: Int = 0,
        updatedAt: Long = 0L
    ) = ExportData(
        tasks = tasks,
        taskCompletions = completions,
        scores = listOf(ExportScore(userId, weekStart, score, updatedAt = updatedAt)),
        wallets = listOf(ExportWallet(userId, wallet, updatedAt = updatedAt))
    )

    private fun task(status: String, cycle: Long) = ExportTask(
        id = taskId,
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = userId,
        status = status,
        completionCycle = cycle
    )

    private fun completion(id: String, cycle: Long, updatedAt: Long) = ExportTaskCompletion(
        id = id,
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = weekStart,
        basePoints = 10,
        totalPoints = 10,
        updatedAt = updatedAt,
        completionCycle = cycle
    )
}
