package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.domain.utils.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerAccountingProjectionTest {

    private val merger = SyncMerger()
    private val userId = "child"
    private val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)

    @Test
    fun mergeSnapshots_concurrentCompletions_sumScoreAndWalletToThirty() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 10, updatedAt = 100L)),
            completions = listOf(completion("c-a", "task-a", 10, updatedAt = 100L))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            wallets = listOf(ExportWallet(userId, 20, updatedAt = 200L)),
            completions = listOf(completion("c-b", "task-b", 20, updatedAt = 200L))
        )

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = userId,
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(30, merged.scores.single().score)
        assertEquals(30, merged.wallets.single().balance)
        assertEquals(2, merged.taskCompletions.size)
    }

    @Test
    fun mergeSnapshots_clockSkew_futureUpdatedAt_stillSumsContributions() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = Long.MAX_VALUE)),
            wallets = listOf(ExportWallet(userId, 10, updatedAt = Long.MAX_VALUE)),
            completions = listOf(completion("c-a", "task-a", 10, updatedAt = Long.MAX_VALUE))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 50L)),
            wallets = listOf(ExportWallet(userId, 20, updatedAt = 50L)),
            completions = listOf(completion("c-b", "task-b", 20, updatedAt = 50L))
        )

        val merged = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)

        assertEquals(30, merged.scores.single().score)
        assertEquals(30, merged.wallets.single().balance)
    }

    @Test
    fun mergeSnapshots_repeatedMerge_isIdempotentForAccounting() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 10, updatedAt = 100L)),
            completions = listOf(completion("c-a", "task-a", 10, updatedAt = 100L))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            wallets = listOf(ExportWallet(userId, 20, updatedAt = 200L)),
            completions = listOf(completion("c-b", "task-b", 20, updatedAt = 200L))
        )

        val first = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        val second = merger.mergeSnapshots(first, first, userId, isMemberWriteAllowed = true)

        assertEquals(first.scores, second.scores)
        assertEquals(first.wallets, second.wallets)
        assertEquals(first.taskCompletions, second.taskCompletions)
    }

    @Test
    fun mergeSnapshots_canceledPenalty_mergeOrderIndependent() {
        val uncanceled = penalty("p1", canceled = false, createdAt = 1_704_067_200_000L)
        val canceled = penalty("p1", canceled = true, createdAt = 1_704_067_200_000L, canceledAt = 1_704_240_000_000L)
        val completion = completion("c1", "task-1", 30, updatedAt = 100L)
        val baseCompletions = listOf(completion)
        val localCanceled = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 30, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 30, updatedAt = 100L)),
            completions = baseCompletions,
            penalties = listOf(canceled)
        )
        val remoteUncanceled = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 15, updatedAt = 200L)),
            wallets = listOf(ExportWallet(userId, 15, updatedAt = 200L)),
            completions = baseCompletions,
            penalties = listOf(uncanceled)
        )
        val mergedLocalFirst = merger.mergeSnapshots(localCanceled, remoteUncanceled, userId, isMemberWriteAllowed = true)
        val mergedRemoteFirst = merger.mergeSnapshots(remoteUncanceled, localCanceled, userId, isMemberWriteAllowed = true)

        assertTrue(mergedLocalFirst.penalties.single().canceled)
        assertTrue(mergedRemoteFirst.penalties.single().canceled)
        assertEquals(30, mergedLocalFirst.scores.single().score)
        assertEquals(30, mergedRemoteFirst.scores.single().score)
    }

    @Test
    fun mergeSnapshots_remoteOnlyLegacy_notZeroed() {
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 100, updatedAt = 500L)),
            wallets = listOf(ExportWallet(userId, 100, updatedAt = 500L))
        )

        val merged = merger.mergeSnapshots(snapshot(), remote, userId, isMemberWriteAllowed = true)

        assertEquals(100, merged.scores.single().score)
        assertEquals(100, merged.wallets.single().balance)
    }

    @Test
    fun mergeSnapshots_accountingRepeatMerge_isIdempotentForDeltaUpload() {
        val base = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 10, updatedAt = 100L)),
            completions = listOf(completion("c-a", "task-a", 10, updatedAt = 100L))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            wallets = listOf(ExportWallet(userId, 20, updatedAt = 200L)),
            completions = listOf(completion("c-b", "task-b", 20, updatedAt = 200L))
        )
        val merged = merger.mergeSnapshots(base, remote, userId, isMemberWriteAllowed = true)
        val repeat = merger.mergeSnapshots(merged, merged, userId, isMemberWriteAllowed = true)

        assertEquals(merged.scores, repeat.scores)
        assertEquals(merged.wallets, repeat.wallets)
    }

    private fun completion(
        id: String,
        taskId: String,
        points: Int,
        updatedAt: Long
    ) = ExportTaskCompletion(
        id = id,
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = weekStart,
        basePoints = points,
        totalPoints = points,
        updatedAt = updatedAt
    )

    private fun penalty(
        id: String,
        canceled: Boolean,
        createdAt: Long,
        canceledAt: Long? = null
    ) = ExportPenalty(
        id = id,
        userId = userId,
        issuedByUserId = "parent",
        amount = -15,
        createdAt = createdAt,
        canceled = canceled,
        canceledAt = canceledAt
    )

    private fun snapshot(
        scores: List<ExportScore> = emptyList(),
        wallets: List<ExportWallet> = emptyList(),
        completions: List<ExportTaskCompletion> = emptyList(),
        penalties: List<ExportPenalty> = emptyList()
    ) = ExportData(
        scores = scores,
        wallets = wallets,
        taskCompletions = completions,
        penalties = penalties,
        exportDate = "2024-01-01T00:00:00"
    )
}
