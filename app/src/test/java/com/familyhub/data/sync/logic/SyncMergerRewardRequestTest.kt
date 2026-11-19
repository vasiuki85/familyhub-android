package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportWallet
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergerRewardRequestTest {

    private val merger = SyncMerger()
    private val userId = "child"
    private val taskId = "task-1"
    private val weekStart = WeekUtils.getWeekStartMillis(1_704_067_200_000L)

    @Test
    fun mergeSnapshots_approvedDeclinedConflict_walletSameBothDirections() {
        val completion = completion(points = 100, updatedAt = 100L)
        val approved = rewardRequest(
            id = "req-1",
            status = RewardRequestStatus.APPROVED,
            processedAt = 100L,
            cost = 30
        )
        val declined = rewardRequest(
            id = "req-1",
            status = RewardRequestStatus.DECLINED,
            processedAt = 100L,
            cost = 30
        )
        val local = snapshot(
            completions = listOf(completion),
            rewardRequests = listOf(approved),
            wallet = 70,
            walletUpdatedAt = 100L
        )
        val remote = snapshot(
            completions = listOf(completion),
            rewardRequests = listOf(declined),
            wallet = 100,
            walletUpdatedAt = 100L
        )

        val localFirst = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        val remoteFirst = merger.mergeSnapshots(remote, local, userId, isMemberWriteAllowed = true)

        assertEquals(localFirst.rewardRequests, remoteFirst.rewardRequests)
        assertEquals(RewardRequestStatus.APPROVED.name, localFirst.rewardRequests.single().status)
        assertEquals(localFirst.wallets.single().balance, remoteFirst.wallets.single().balance)
        assertEquals(70, localFirst.wallets.single().balance)
    }

    @Test
    fun mergeSnapshots_rewardRequestListSameBothDirections() {
        val local = snapshot(
            rewardRequests = listOf(
                rewardRequest(id = "req-a", status = RewardRequestStatus.PENDING, requestedAt = 100L),
                rewardRequest(id = "req-b", status = RewardRequestStatus.PENDING, requestedAt = 200L)
            )
        )
        val remote = snapshot(
            rewardRequests = listOf(
                rewardRequest(id = "req-c", status = RewardRequestStatus.PENDING, requestedAt = 300L),
                rewardRequest(id = "req-d", status = RewardRequestStatus.PENDING, requestedAt = 400L)
            )
        )

        val localFirst = merger.mergeSnapshots(local, remote, userId, isMemberWriteAllowed = true)
        val remoteFirst = merger.mergeSnapshots(remote, local, userId, isMemberWriteAllowed = true)

        assertEquals(localFirst.rewardRequests, remoteFirst.rewardRequests)
        assertEquals(listOf("req-a", "req-b", "req-c", "req-d"), localFirst.rewardRequests.map { it.id })
    }

    private fun snapshot(
        completions: List<ExportTaskCompletion> = emptyList(),
        rewardRequests: List<ExportRewardRequest> = emptyList(),
        wallet: Int = 0,
        walletUpdatedAt: Long = 0L
    ) = ExportData(
        tasks = emptyList(),
        taskCompletions = completions,
        rewardRequests = rewardRequests,
        scores = listOf(ExportScore(userId, weekStart, wallet, updatedAt = walletUpdatedAt)),
        wallets = listOf(ExportWallet(userId, wallet, updatedAt = walletUpdatedAt))
    )

    private fun completion(points: Int, updatedAt: Long) = ExportTaskCompletion(
        id = "completion-1",
        taskId = taskId,
        userId = userId,
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = weekStart,
        basePoints = points,
        totalPoints = points,
        updatedAt = updatedAt,
        completionCycle = 0L
    )

    private fun rewardRequest(
        id: String,
        status: RewardRequestStatus,
        requestedAt: Long = 1L,
        processedAt: Long? = null,
        cost: Int = 50
    ) = ExportRewardRequest(
        id = id,
        rewardId = "reward-active",
        rewardTitle = "Ice Cream",
        rewardCost = cost,
        requesterUserId = userId,
        status = status.name,
        requestedAt = requestedAt,
        processedAt = processedAt,
        processedByUserId = if (processedAt != null) "parent" else null
    )
}
