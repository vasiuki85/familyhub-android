package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingProjectionTest {

    private val userId = "child"
    private val penaltyCreatedAt = 1_704_067_200_000L // 2024-01-01 UTC-ish, stable week anchor
    private val weekStart = WeekUtils.getWeekStartMillis(penaltyCreatedAt)

    @Test
    fun concurrentCompletionsDifferentTasks_sumToThirty() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            completions = listOf(completion("c-a", taskId = "task-a", points = 10, updatedAt = 100L))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            completions = listOf(completion("c-b", taskId = "task-b", points = 20, updatedAt = 200L))
        )
        val mergedCompletions = local.taskCompletions + remote.taskCompletions

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = remote,
            taskCompletions = mergedCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(30, projected.scores.single().score)
        assertEquals(30, projected.wallets.single().balance)
    }

    @Test
    fun futureUpdatedAt_doesNotEraseOtherSideContribution() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = Long.MAX_VALUE)),
            completions = listOf(
                completion("c-a", taskId = "task-a", points = 10, updatedAt = Long.MAX_VALUE)
            )
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 50L)),
            completions = listOf(
                completion("c-b", taskId = "task-b", points = 20, updatedAt = 50L)
            )
        )
        val mergedCompletions = local.taskCompletions + remote.taskCompletions

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = remote,
            taskCompletions = mergedCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(30, projected.scores.single().score)
        assertEquals(30, projected.wallets.single().balance)
    }

    @Test
    fun reversedCompletion_removesOnlyItsContribution() {
        val active = completion("c-active", points = 20, updatedAt = 200L)
        val reversed = completion("c-reversed", points = 10, updatedAt = 100L, reversedAt = 150L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            completions = listOf(active, reversed)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(20, projected.scores.single().score)
        assertEquals(20, projected.wallets.single().balance)
    }

    @Test
    fun activePenalty_appliedOnceToScoreAndWallet() {
        val penalty = penalty(id = "p1", amount = -15, createdAt = penaltyCreatedAt)
        val completion = completion("c1", points = 30, updatedAt = 100L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 15, updatedAt = 200L)),
            wallets = listOf(ExportWallet(userId, 15, updatedAt = 200L)),
            completions = listOf(completion),
            penalties = listOf(penalty)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = local.penalties,
            rewardRequests = emptyList()
        )

        assertEquals(15, projected.scores.single().score)
        assertEquals(15, projected.wallets.single().balance)
    }

    @Test
    fun canceledPenalty_doesNotAffectScoreOrWallet() {
        val canceled = penalty(id = "p1", amount = -15, createdAt = penaltyCreatedAt, canceled = true, canceledAt = 300L)
        val completion = completion("c1", points = 30, updatedAt = 100L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 30, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 30, updatedAt = 100L)),
            completions = listOf(completion),
            penalties = listOf(canceled)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(30, projected.scores.single().score)
        assertEquals(30, projected.wallets.single().balance)
    }

    @Test
    fun cancellationTimestampWinsOverOlderActiveContributionForMaterializedUpdatedAt() {
        val completion = completion("c1", points = 30, updatedAt = 100L)
        val activePenalty = penalty(id = "p1", amount = -15, createdAt = penaltyCreatedAt)
        val cancellationTime = penaltyCreatedAt + 300L
        val canceledPenalty = activePenalty.copy(canceled = true, canceledAt = cancellationTime)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 30, updatedAt = cancellationTime)),
            wallets = listOf(ExportWallet(userId, 30, updatedAt = cancellationTime)),
            completions = listOf(completion),
            penalties = listOf(canceledPenalty)
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 15, updatedAt = 100L)),
            wallets = listOf(ExportWallet(userId, 15, updatedAt = 100L)),
            completions = listOf(completion),
            penalties = listOf(activePenalty)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = remote,
            taskCompletions = listOf(completion),
            penalties = listOf(canceledPenalty),
            rewardRequests = emptyList()
        )

        assertEquals(30, projected.scores.single().score)
        assertEquals(cancellationTime, projected.scores.single().updatedAt)
        assertEquals(30, projected.wallets.single().balance)
        assertEquals(cancellationTime, projected.wallets.single().updatedAt)
    }

    @Test
    fun approvedRewardRequest_reducesWalletOnce() {
        val completion = completion("c1", points = 100, updatedAt = 100L)
        val approved = rewardRequest(
            id = "r1",
            cost = 40,
            status = RewardRequestStatus.APPROVED,
            processedAt = 200L
        )
        val local = snapshot(
            wallets = listOf(ExportWallet(userId, 60, updatedAt = 200L)),
            completions = listOf(completion),
            rewardRequests = listOf(approved)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = local.rewardRequests
        )

        assertEquals(60, projected.wallets.single().balance)
    }

    @Test
    fun pendingAndDeclinedRewardRequests_doNotChangeWallet() {
        val completion = completion("c1", points = 100, updatedAt = 100L)
        val pending = rewardRequest(id = "r-pending", cost = 40, status = RewardRequestStatus.PENDING)
        val declined = rewardRequest(id = "r-declined", cost = 25, status = RewardRequestStatus.DECLINED, processedAt = 150L)
        val local = snapshot(
            wallets = listOf(ExportWallet(userId, 100, updatedAt = 100L)),
            completions = listOf(completion),
            rewardRequests = listOf(pending, declined)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = local.rewardRequests
        )

        assertEquals(100, projected.wallets.single().balance)
    }

    @Test
    fun duplicateCompletionId_countedOnce() {
        val completion = completion("c1", points = 10, updatedAt = 100L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            completions = listOf(completion)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = listOf(completion, completion),
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(10, projected.scores.single().score)
    }

    @Test
    fun repeatProjection_isIdempotent() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            completions = listOf(completion("c1", points = 10, updatedAt = 100L))
        )
        val first = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )
        val second = AccountingProjection.projectMaterializedCaches(
            local = local.copy(scores = first.scores, wallets = first.wallets),
            remote = snapshot(),
            taskCompletions = local.taskCompletions,
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(first, second)
    }

    @Test
    fun localRemotePermutation_withEqualBaseline_producesSameResult() {
        val completionA = completion("c-a", taskId = "task-a", points = 10, updatedAt = 100L)
        val completionB = completion("c-b", taskId = "task-b", points = 20, updatedAt = 200L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 10, updatedAt = 100L)),
            completions = listOf(completionA)
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 20, updatedAt = 200L)),
            completions = listOf(completionB)
        )
        val merged = listOf(completionA, completionB)

        val forward = AccountingProjection.projectMaterializedCaches(local, remote, merged, emptyList(), emptyList())
        val swapped = AccountingProjection.projectMaterializedCaches(remote, local, merged, emptyList(), emptyList())

        assertEquals(forward, swapped)
    }

    @Test
    fun legacyAggregate100_plusConcurrentTenAndTwenty_equals130() {
        val completionA = completion("c-a", taskId = "task-a", points = 10, updatedAt = 100L)
        val completionB = completion("c-b", taskId = "task-b", points = 20, updatedAt = 200L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 110, updatedAt = 100L)),
            completions = listOf(completionA)
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 120, updatedAt = 200L)),
            completions = listOf(completionB)
        )
        val merged = listOf(completionA, completionB)

        val projected = AccountingProjection.projectMaterializedCaches(local, remote, merged, emptyList(), emptyList())

        assertEquals(130, projected.scores.single().score)
    }

    @Test
    fun legacyAggregate100_plusConcurrentTenAndTwenty_walletUsesSameBaseline() {
        val completionA = completion("c-a", taskId = "task-a", points = 10, updatedAt = 100L)
        val completionB = completion("c-b", taskId = "task-b", points = 20, updatedAt = 200L)
        val local = snapshot(
            wallets = listOf(ExportWallet(userId, 110, updatedAt = 100L)),
            completions = listOf(completionA)
        )
        val remote = snapshot(
            wallets = listOf(ExportWallet(userId, 120, updatedAt = 200L)),
            completions = listOf(completionB)
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local,
            remote,
            listOf(completionA, completionB),
            emptyList(),
            emptyList()
        )

        assertEquals(130, projected.wallets.single().balance)
    }

    @Test
    fun remoteOnlyLegacyScore_isNotZeroed() {
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 100, updatedAt = 500L)),
            wallets = listOf(ExportWallet(userId, 100, updatedAt = 500L))
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = snapshot(),
            remote = remote,
            taskCompletions = emptyList(),
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(100, projected.scores.single().score)
        assertEquals(100, projected.wallets.single().balance)
        assertEquals(500L, projected.scores.single().updatedAt)
    }

    @Test
    fun conflictingBaselines_remoteIsAuthoritative() {
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 50, updatedAt = 100L))
        )
        val remote = snapshot(
            scores = listOf(ExportScore(userId, weekStart, 80, updatedAt = 200L))
        )

        val projected = AccountingProjection.projectMaterializedCaches(
            local = local,
            remote = remote,
            taskCompletions = emptyList(),
            penalties = emptyList(),
            rewardRequests = emptyList()
        )

        assertEquals(80, projected.scores.single().score)
    }

    @Test
    fun selectBaseline_equalBaselines_preserved() {
        assertEquals(100L, AccountingProjection.selectBaseline(100L, 100L))
    }

    @Test
    fun selectBaseline_onlyLocal_usesLocal() {
        assertEquals(42L, AccountingProjection.selectBaseline(42L, null))
    }

    @Test
    fun selectBaseline_onlyRemote_usesRemote() {
        assertEquals(77L, AccountingProjection.selectBaseline(null, 77L))
    }

    @Test
    fun overflow_throwsInsteadOfSilentlyWrapping() {
        val huge = completion("c1", points = Int.MAX_VALUE, updatedAt = 1L)
        val local = snapshot(
            scores = listOf(ExportScore(userId, weekStart, Int.MAX_VALUE, updatedAt = 1L)),
            completions = listOf(huge)
        )

        assertThrows(ArithmeticException::class.java) {
            AccountingProjection.projectMaterializedCaches(
                local = local,
                remote = snapshot(),
                taskCompletions = listOf(huge, huge.copy(id = "c2", taskId = "task-2")),
                penalties = emptyList(),
                rewardRequests = emptyList()
            )
        }
    }

    @Test
    fun resolvePenalty_canceledBeatsUncanceled_bothOrders() {
        val uncanceled = penalty(id = "p1", amount = -10, createdAt = 500L, canceled = false)
        val canceled = penalty(id = "p1", amount = -10, createdAt = 100L, canceled = true, canceledAt = 600L)

        assertTrue(AccountingProjection.resolvePenaltyMonotonic(canceled, uncanceled, localNewer = false).canceled)
        assertTrue(AccountingProjection.resolvePenaltyMonotonic(uncanceled, canceled, localNewer = true).canceled)
    }

    @Test
    fun resolvePenalty_amongCanceled_higherCanceledAtWins() {
        val older = penalty(id = "p1", amount = -10, createdAt = 100L, canceled = true, canceledAt = 200L)
        val newer = penalty(id = "p1", amount = -10, createdAt = 100L, canceled = true, canceledAt = 500L)

        assertEquals(500L, AccountingProjection.resolvePenaltyMonotonic(older, newer, localNewer = true).canceledAt)
        assertEquals(500L, AccountingProjection.resolvePenaltyMonotonic(newer, older, localNewer = false).canceledAt)
    }

    @Test
    fun resolvePenalty_equalCanceledAt_usesCreatedAtBeforeLocalNewer() {
        val older = penalty(id = "p1", amount = -10, createdAt = 100L, canceled = true, canceledAt = 500L)
        val newer = penalty(id = "p1", amount = -10, createdAt = 200L, canceled = true, canceledAt = 500L)

        assertEquals(200L, AccountingProjection.resolvePenaltyMonotonic(older, newer, localNewer = true).createdAt)
        assertEquals(200L, AccountingProjection.resolvePenaltyMonotonic(newer, older, localNewer = false).createdAt)
    }

    @Test
    fun resolvePenalty_canceledPenaltyDoesNotResurrect() {
        val uncanceled = penalty(id = "p1", amount = -10, createdAt = 900L, canceled = false)
        val canceled = penalty(id = "p1", amount = -10, createdAt = 100L, canceled = true, canceledAt = 600L)

        val mergedLocalFirst = AccountingProjection.resolvePenaltyMonotonic(canceled, uncanceled, localNewer = false)
        val mergedRemoteFirst = AccountingProjection.resolvePenaltyMonotonic(uncanceled, canceled, localNewer = true)

        assertTrue(mergedLocalFirst.canceled)
        assertTrue(mergedRemoteFirst.canceled)
    }

    private fun completion(
        id: String,
        taskId: String = "task1",
        points: Int = 10,
        updatedAt: Long = 100L,
        reversedAt: Long? = null
    ) = ExportTaskCompletion(
        id = id,
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = weekStart,
        basePoints = points,
        bonusPoints = 0,
        penaltyPoints = 0,
        totalPoints = points,
        reversedAt = reversedAt,
        updatedAt = updatedAt
    )

    private fun penalty(
        id: String,
        amount: Int,
        createdAt: Long,
        canceled: Boolean = false,
        canceledAt: Long? = null
    ) = ExportPenalty(
        id = id,
        userId = userId,
        issuedByUserId = "parent",
        amount = amount,
        createdAt = createdAt,
        canceled = canceled,
        canceledAt = canceledAt
    )

    private fun rewardRequest(
        id: String,
        cost: Int,
        status: RewardRequestStatus,
        processedAt: Long? = null
    ) = ExportRewardRequest(
        id = id,
        rewardId = "reward",
        rewardTitle = "Treat",
        rewardCost = cost,
        requesterUserId = userId,
        status = status.name,
        requestedAt = 100L,
        processedAt = processedAt
    )

    private fun snapshot(
        scores: List<ExportScore> = emptyList(),
        wallets: List<ExportWallet> = emptyList(),
        completions: List<ExportTaskCompletion> = emptyList(),
        penalties: List<ExportPenalty> = emptyList(),
        rewardRequests: List<ExportRewardRequest> = emptyList()
    ) = ExportData(
        scores = scores,
        wallets = wallets,
        taskCompletions = completions,
        penalties = penalties,
        rewardRequests = rewardRequests,
        exportDate = "2024-01-01T00:00:00"
    )
}
