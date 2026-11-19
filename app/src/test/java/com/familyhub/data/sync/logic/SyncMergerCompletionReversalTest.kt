package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergerCompletionReversalTest {

    private val merger = SyncMerger()

    @Test
    fun mergeSnapshots_reversedCompletionWinsOverUnreversed() {
        val unreversed = completion(id = "c1", reversedAt = null, updatedAt = 100L)
        val reversed = completion(id = "c1", reversedAt = 200L, updatedAt = 200L, reversedBy = "parent")

        val local = snapshot(listOf(unreversed))
        val remote = snapshot(listOf(reversed))

        val merged = merger.mergeSnapshots(
            local = local,
            remote = remote,
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(200L, merged.taskCompletions.single().reversedAt)
    }

    @Test
    fun mergeSnapshots_newerReversedCompletionWinsAmongReversed() {
        val older = completion(id = "c1", reversedAt = 100L, updatedAt = 100L)
        val newer = completion(id = "c1", reversedAt = 300L, updatedAt = 300L, reversedBy = "parent")

        val merged = merger.mergeSnapshots(
            local = snapshot(listOf(older)),
            remote = snapshot(listOf(newer)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = false
        )

        assertEquals(300L, merged.taskCompletions.single().reversedAt)
    }

    @Test
    fun mergeSnapshots_newerUpdatedAtBreaksEqualReversedAtTie() {
        val older = completion(id = "c1", reversedAt = 300L, updatedAt = 400L, reversedBy = "parent-old")
        val newer = completion(id = "c1", reversedAt = 300L, updatedAt = 500L, reversedBy = "parent-new")

        val remoteWins = merger.mergeSnapshots(
            local = snapshot(listOf(older)),
            remote = snapshot(listOf(newer)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )
        val localWins = merger.mergeSnapshots(
            local = snapshot(listOf(newer)),
            remote = snapshot(listOf(older)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = false
        )

        assertEquals("parent-new", remoteWins.taskCompletions.single().reversedByUserId)
        assertEquals("parent-new", localWins.taskCompletions.single().reversedByUserId)
    }

    @Test
    fun mergeSnapshots_conflictingLegacyBaselines_remoteAuthoritative() {
        val localScore = ExportScore(userId = "u1", weekStartDate = 1L, score = 5, updatedAt = 50L)
        val remoteScore = ExportScore(userId = "u1", weekStartDate = 1L, score = 10, updatedAt = 200L)

        val merged = merger.mergeSnapshots(
            local = snapshot(completions = emptyList(), scores = listOf(localScore)),
            remote = snapshot(completions = emptyList(), scores = listOf(remoteScore)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(10, merged.scores.single().score)
        assertEquals(200L, merged.scores.single().updatedAt)
    }

    @Test
    fun mergeSnapshots_conflictingLegacyWallets_remoteAuthoritative() {
        val localWallet = ExportWallet(userId = "u1", balance = 5, updatedAt = 10L)
        val remoteWallet = ExportWallet(userId = "u1", balance = 20, updatedAt = 500L)

        val merged = merger.mergeSnapshots(
            local = snapshot(completions = emptyList(), wallets = listOf(localWallet)),
            remote = snapshot(completions = emptyList(), wallets = listOf(remoteWallet)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )

        assertEquals(20, merged.wallets.single().balance)
    }

    @Test
    fun mergeSnapshots_reversedWinsRegardlessOfArgumentOrder() {
        val unreversed = completion(id = "c1", reversedAt = null, updatedAt = 500L)
        val reversed = completion(id = "c1", reversedAt = 100L, updatedAt = 100L)

        val mergedRemoteFirst = merger.mergeSnapshots(
            local = snapshot(listOf(unreversed)),
            remote = snapshot(listOf(reversed)),
            currentUserId = "user",
            isMemberWriteAllowed = true
        )
        val mergedLocalFirst = merger.mergeSnapshots(
            local = snapshot(listOf(reversed)),
            remote = snapshot(listOf(unreversed)),
            currentUserId = "user",
            isMemberWriteAllowed = true
        )

        assertEquals(100L, mergedRemoteFirst.taskCompletions.single().reversedAt)
        assertEquals(100L, mergedLocalFirst.taskCompletions.single().reversedAt)
    }

    @Test
    fun mergeSnapshots_repeatedMergeIsIdempotent() {
        val reversed = completion(id = "c1", reversedAt = 200L, updatedAt = 200L)
        val first = merger.mergeSnapshots(
            local = snapshot(listOf(reversed)),
            remote = snapshot(listOf(reversed)),
            currentUserId = "user",
            isMemberWriteAllowed = true
        )
        val second = merger.mergeSnapshots(
            local = first,
            remote = snapshot(listOf(reversed)),
            currentUserId = "user",
            isMemberWriteAllowed = true
        )
        assertEquals(first.taskCompletions, second.taskCompletions)
    }

    @Test
    fun mergeSnapshots_conflictingLegacyBaselines_remoteWinsOnTie() {
        val localScore = ExportScore(userId = "u1", weekStartDate = 1L, score = 5, updatedAt = 100L)
        val remoteScore = ExportScore(userId = "u1", weekStartDate = 1L, score = 9, updatedAt = 100L)

        val mergedLocalNewer = merger.mergeSnapshots(
            local = snapshot(completions = emptyList(), scores = listOf(localScore)),
            remote = snapshot(completions = emptyList(), scores = listOf(remoteScore)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = true
        )
        val mergedRemoteNewer = merger.mergeSnapshots(
            local = snapshot(completions = emptyList(), scores = listOf(localScore)),
            remote = snapshot(completions = emptyList(), scores = listOf(remoteScore)),
            currentUserId = "user",
            isMemberWriteAllowed = true,
            hasPendingChanges = false
        )

        assertEquals(9, mergedLocalNewer.scores.single().score)
        assertEquals(9, mergedRemoteNewer.scores.single().score)
    }

    @Test
    fun mergeSnapshots_preservesReversedCompletionInExportLedger() {
        val reversed = completion(id = "c1", reversedAt = 100L, updatedAt = 100L, reversedBy = "p1")
        val merged = merger.mergeSnapshots(
            local = snapshot(listOf(reversed)),
            remote = snapshot(emptyList()),
            currentUserId = "user",
            isMemberWriteAllowed = true
        )
        assertEquals("c1", merged.taskCompletions.single().id)
        assertEquals(100L, merged.taskCompletions.single().reversedAt)
    }

    private fun completion(
        id: String,
        reversedAt: Long?,
        updatedAt: Long,
        reversedBy: String? = null
    ) = ExportTaskCompletion(
        id = id,
        taskId = "task1",
        userId = "child",
        taskType = "ROUTINE",
        completionTime = 50L,
        weekStartDate = 1L,
        basePoints = 10,
        bonusPoints = 0,
        penaltyPoints = 0,
        totalPoints = 10,
        reversedAt = reversedAt,
        reversedByUserId = reversedBy,
        updatedAt = updatedAt
    )

    private fun snapshot(
        completions: List<ExportTaskCompletion>,
        scores: List<ExportScore> = emptyList(),
        wallets: List<ExportWallet> = emptyList()
    ) = ExportData(
        taskCompletions = completions,
        scores = scores,
        wallets = wallets,
        exportDate = "2024-01-01T00:00:00"
    )
}
