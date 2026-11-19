package com.familyhub.data.sync.logic

import androidx.annotation.VisibleForTesting
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.WeekUtils

/**
 * Rebuilds materialized score/wallet caches from merged source-ledgers plus legacy baselines.
 *
 * score(user, week) = scoreBaseline + active completion points + active penalty amounts
 * wallet(user) = walletBaseline + active completion points + active penalty amounts - approved reward costs
 */
object AccountingProjection {

    data class ProjectedAccounting(
        val scores: List<ExportScore>,
        val wallets: List<ExportWallet>
    )

    fun projectMaterializedCaches(
        local: ExportData,
        remote: ExportData,
        taskCompletions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>,
        rewardRequests: List<ExportRewardRequest>
    ): ProjectedAccounting {
        val localActiveCompletions = local.taskCompletions.filter { it.reversedAt == null }
        val remoteActiveCompletions = remote.taskCompletions.filter { it.reversedAt == null }
        val localActivePenalties = local.penalties.filter { !it.canceled }
        val remoteActivePenalties = remote.penalties.filter { !it.canceled }
        val localApprovedRequests = local.rewardRequests.filter { it.status == RewardRequestStatus.APPROVED.name }
        val remoteApprovedRequests = remote.rewardRequests.filter { it.status == RewardRequestStatus.APPROVED.name }

        val mergedActiveCompletions = CompletionCycleMerge.activeCompletionsForProjection(taskCompletions)
        val mergedActivePenalties = penalties.filter { !it.canceled }.distinctBy { it.id }
        val mergedApprovedRequests = rewardRequests
            .filter { it.status == RewardRequestStatus.APPROVED.name }
            .distinctBy { it.id }

        val scores = projectScores(
            localScores = local.scores,
            remoteScores = remote.scores,
            localActiveCompletions = localActiveCompletions,
            remoteActiveCompletions = remoteActiveCompletions,
            localActivePenalties = localActivePenalties,
            remoteActivePenalties = remoteActivePenalties,
            mergedActiveCompletions = mergedActiveCompletions,
            mergedActivePenalties = mergedActivePenalties,
            mergedCompletions = taskCompletions,
            mergedPenalties = penalties
        )
        val wallets = projectWallets(
            localWallets = local.wallets,
            remoteWallets = remote.wallets,
            localActiveCompletions = localActiveCompletions,
            remoteActiveCompletions = remoteActiveCompletions,
            localActivePenalties = localActivePenalties,
            remoteActivePenalties = remoteActivePenalties,
            localApprovedRequests = localApprovedRequests,
            remoteApprovedRequests = remoteApprovedRequests,
            mergedActiveCompletions = mergedActiveCompletions,
            mergedActivePenalties = mergedActivePenalties,
            mergedApprovedRequests = mergedApprovedRequests,
            mergedCompletions = taskCompletions,
            mergedPenalties = penalties,
            mergedRewardRequests = rewardRequests
        )
        return ProjectedAccounting(scores = scores, wallets = wallets)
    }

    @VisibleForTesting
    fun resolvePenaltyMonotonic(
        local: ExportPenalty,
        remote: ExportPenalty,
        localNewer: Boolean
    ): ExportPenalty {
        return when {
            local.canceled && !remote.canceled -> local
            remote.canceled && !local.canceled -> remote
            local.canceled && remote.canceled -> {
                val localTs = local.canceledAt ?: 0L
                val remoteTs = remote.canceledAt ?: 0L
                when {
                    localTs > remoteTs -> local
                    remoteTs > localTs -> remote
                    local.createdAt > remote.createdAt -> local
                    remote.createdAt > local.createdAt -> remote
                    localNewer -> local
                    else -> remote
                }
            }
            local.createdAt > remote.createdAt -> local
            remote.createdAt > local.createdAt -> remote
            localNewer -> local
            else -> remote
        }
    }

    @VisibleForTesting
    fun scoreBaselineFromSnapshot(
        materializedScore: Int,
        userId: String,
        weekStartDate: Long,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>
    ): Long {
        val completionSum = completions
            .filter { it.userId == userId && it.weekStartDate == weekStartDate }
            .sumOf { it.totalPoints.toLong() }
        val penaltySum = penalties
            .filter { it.userId == userId && penaltyWeekStart(it) == weekStartDate }
            .sumOf { it.amount.toLong() }
        return materializedScore.toLong() - completionSum - penaltySum
    }

    @VisibleForTesting
    fun walletBaselineFromSnapshot(
        materializedWallet: Int,
        userId: String,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>,
        approvedRequests: List<ExportRewardRequest>
    ): Long {
        val completionSum = completions
            .filter { it.userId == userId }
            .sumOf { it.totalPoints.toLong() }
        val penaltySum = penalties
            .filter { it.userId == userId }
            .sumOf { it.amount.toLong() }
        val approvedSpend = approvedRequests
            .filter { it.requesterUserId == userId }
            .sumOf { it.rewardCost.toLong() }
        return materializedWallet.toLong() - completionSum - penaltySum + approvedSpend
    }

    @VisibleForTesting
    fun selectBaseline(localBaseline: Long?, remoteBaseline: Long?): Long {
        return when {
            localBaseline == null && remoteBaseline == null -> 0L
            localBaseline == null -> requireNotNull(remoteBaseline)
            remoteBaseline == null -> localBaseline
            localBaseline == remoteBaseline -> localBaseline
            else -> remoteBaseline
        }
    }

    private fun projectScores(
        localScores: List<ExportScore>,
        remoteScores: List<ExportScore>,
        localActiveCompletions: List<ExportTaskCompletion>,
        remoteActiveCompletions: List<ExportTaskCompletion>,
        localActivePenalties: List<ExportPenalty>,
        remoteActivePenalties: List<ExportPenalty>,
        mergedActiveCompletions: List<ExportTaskCompletion>,
        mergedActivePenalties: List<ExportPenalty>,
        mergedCompletions: List<ExportTaskCompletion>,
        mergedPenalties: List<ExportPenalty>
    ): List<ExportScore> {
        val keys = buildScoreKeys(localScores, remoteScores, mergedActiveCompletions, mergedActivePenalties)
        return keys.map { (userId, weekStart) ->
            val localScore = localScores.find { it.userId == userId && it.weekStartDate == weekStart }
            val remoteScore = remoteScores.find { it.userId == userId && it.weekStartDate == weekStart }
            val localBaseline = localScore?.let {
                scoreBaselineFromSnapshot(it.score, userId, weekStart, localActiveCompletions, localActivePenalties)
            }
            val remoteBaseline = remoteScore?.let {
                scoreBaselineFromSnapshot(it.score, userId, weekStart, remoteActiveCompletions, remoteActivePenalties)
            }
            val baseline = selectBaseline(localBaseline, remoteBaseline)
            val completionSum = mergedActiveCompletions
                .filter { it.userId == userId && it.weekStartDate == weekStart }
                .sumOf { it.totalPoints.toLong() }
            val penaltySum = mergedActivePenalties
                .filter { it.userId == userId && penaltyWeekStart(it) == weekStart }
                .sumOf { it.amount.toLong() }
            val total = baseline + completionSum + penaltySum
            val updatedAt = scoreUpdatedAt(
                userId = userId,
                weekStart = weekStart,
                completions = mergedCompletions,
                penalties = mergedPenalties,
                localScore = localScore,
                remoteScore = remoteScore
            )
            ExportScore(
                userId = userId,
                weekStartDate = weekStart,
                score = toIntExact(total),
                updatedAt = updatedAt
            )
        }.sortedWith(compareBy(ExportScore::userId, ExportScore::weekStartDate))
    }

    private fun projectWallets(
        localWallets: List<ExportWallet>,
        remoteWallets: List<ExportWallet>,
        localActiveCompletions: List<ExportTaskCompletion>,
        remoteActiveCompletions: List<ExportTaskCompletion>,
        localActivePenalties: List<ExportPenalty>,
        remoteActivePenalties: List<ExportPenalty>,
        localApprovedRequests: List<ExportRewardRequest>,
        remoteApprovedRequests: List<ExportRewardRequest>,
        mergedActiveCompletions: List<ExportTaskCompletion>,
        mergedActivePenalties: List<ExportPenalty>,
        mergedApprovedRequests: List<ExportRewardRequest>,
        mergedCompletions: List<ExportTaskCompletion>,
        mergedPenalties: List<ExportPenalty>,
        mergedRewardRequests: List<ExportRewardRequest>
    ): List<ExportWallet> {
        val userIds = buildWalletUserIds(
            localWallets,
            remoteWallets,
            mergedActiveCompletions,
            mergedActivePenalties,
            mergedApprovedRequests
        )
        return userIds.map { userId ->
            val localWallet = localWallets.find { it.userId == userId }
            val remoteWallet = remoteWallets.find { it.userId == userId }
            val localBaseline = localWallet?.let {
                walletBaselineFromSnapshot(
                    it.balance,
                    userId,
                    localActiveCompletions,
                    localActivePenalties,
                    localApprovedRequests
                )
            }
            val remoteBaseline = remoteWallet?.let {
                walletBaselineFromSnapshot(
                    it.balance,
                    userId,
                    remoteActiveCompletions,
                    remoteActivePenalties,
                    remoteApprovedRequests
                )
            }
            val baseline = selectBaseline(localBaseline, remoteBaseline)
            val completionSum = mergedActiveCompletions.filter { it.userId == userId }.sumOf { it.totalPoints.toLong() }
            val penaltySum = mergedActivePenalties.filter { it.userId == userId }.sumOf { it.amount.toLong() }
            val approvedSpend = mergedApprovedRequests
                .filter { it.requesterUserId == userId }
                .sumOf { it.rewardCost.toLong() }
            val total = baseline + completionSum + penaltySum - approvedSpend
            val updatedAt = walletUpdatedAt(
                userId = userId,
                completions = mergedCompletions,
                penalties = mergedPenalties,
                rewardRequests = mergedRewardRequests,
                localWallet = localWallet,
                remoteWallet = remoteWallet
            )
            ExportWallet(
                userId = userId,
                balance = toIntExact(total),
                updatedAt = updatedAt
            )
        }.sortedBy(ExportWallet::userId)
    }

    private fun buildScoreKeys(
        localScores: List<ExportScore>,
        remoteScores: List<ExportScore>,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>
    ): Set<Pair<String, Long>> {
        val keys = mutableSetOf<Pair<String, Long>>()
        localScores.forEach { keys += it.userId to it.weekStartDate }
        remoteScores.forEach { keys += it.userId to it.weekStartDate }
        completions.forEach { keys += it.userId to it.weekStartDate }
        penalties.forEach { keys += it.userId to penaltyWeekStart(it) }
        return keys
    }

    private fun buildWalletUserIds(
        localWallets: List<ExportWallet>,
        remoteWallets: List<ExportWallet>,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>,
        approvedRequests: List<ExportRewardRequest>
    ): Set<String> {
        return buildSet {
            localWallets.forEach { add(it.userId) }
            remoteWallets.forEach { add(it.userId) }
            completions.forEach { add(it.userId) }
            penalties.forEach { add(it.userId) }
            approvedRequests.forEach { add(it.requesterUserId) }
        }
    }

    private fun scoreUpdatedAt(
        userId: String,
        weekStart: Long,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>,
        localScore: ExportScore?,
        remoteScore: ExportScore?
    ): Long {
        val fromCompletions = completions
            .filter { it.userId == userId && it.weekStartDate == weekStart }
            .maxOfOrNull { it.updatedAt } ?: 0L
        val fromPenalties = penalties
            .filter { it.userId == userId && penaltyWeekStart(it) == weekStart }
            .maxOfOrNull(::penaltyUpdatedAt) ?: 0L
        val fromEvents = maxOf(fromCompletions, fromPenalties)
        if (fromEvents > 0L) return fromEvents
        return maxOf(localScore?.updatedAt ?: 0L, remoteScore?.updatedAt ?: 0L)
    }

    private fun walletUpdatedAt(
        userId: String,
        completions: List<ExportTaskCompletion>,
        penalties: List<ExportPenalty>,
        rewardRequests: List<ExportRewardRequest>,
        localWallet: ExportWallet?,
        remoteWallet: ExportWallet?
    ): Long {
        val fromCompletions = completions.filter { it.userId == userId }.maxOfOrNull { it.updatedAt } ?: 0L
        val fromPenalties = penalties.filter { it.userId == userId }.maxOfOrNull(::penaltyUpdatedAt) ?: 0L
        val fromRewards = rewardRequests
            .filter { it.requesterUserId == userId && it.processedAt != null }
            .maxOfOrNull { it.processedAt ?: it.requestedAt } ?: 0L
        val fromEvents = maxOf(fromCompletions, fromPenalties, fromRewards)
        if (fromEvents > 0L) return fromEvents
        return maxOf(localWallet?.updatedAt ?: 0L, remoteWallet?.updatedAt ?: 0L)
    }

    private fun penaltyWeekStart(penalty: ExportPenalty): Long =
        WeekUtils.getWeekStartMillis(penalty.createdAt)

    private fun penaltyUpdatedAt(penalty: ExportPenalty): Long =
        maxOf(penalty.createdAt, penalty.canceledAt ?: 0L)

    private fun toIntExact(value: Long): Int {
        if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
            throw ArithmeticException("Accounting value out of Int range: $value")
        }
        return value.toInt()
    }
}
