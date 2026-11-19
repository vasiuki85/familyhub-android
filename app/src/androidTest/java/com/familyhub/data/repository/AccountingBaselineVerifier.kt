package com.familyhub.data.repository

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportRewardRequest
import com.familyhub.data.export.ExportScore
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.export.ExportWallet
import com.familyhub.data.export.toExport
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.sync.logic.AccountingProjection
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.utils.WeekUtils
import kotlinx.coroutines.flow.first

/**
 * Test-only helper for FH2-12A baseline invariant checks in instrumented tests.
 */
object AccountingBaselineVerifier {
    suspend fun snapshot(database: FamilyHubDatabase): ExportData {
        val completions = database.taskCompletionDao().getAllCompletions().first()
            .map { it.toDomain().toExportSnapshot() }
        val penalties = database.penaltyDao().getAll().first()
            .map { it.toDomain().toExport() }
        val rewardRequests = database.rewardRequestDao().getAll().first()
            .map { it.toDomain().toExportSnapshot() }
        val scores = database.userScoreDao().getScoresFromWeek(0L).first().map {
            ExportScore(it.userId, it.weekStartDate, it.score, it.updatedAt)
        }
        val wallets = database.userWalletDao().getAll().first().map {
            ExportWallet(it.userId, it.balance, it.updatedAt)
        }
        return ExportData(
            scores = scores,
            wallets = wallets,
            taskCompletions = completions,
            penalties = penalties,
            rewardRequests = rewardRequests,
            exportDate = ""
        )
    }

    fun scoreBaseline(snapshot: ExportData, userId: String, weekStart: Long): Long {
        val score = snapshot.scores.find { it.userId == userId && it.weekStartDate == weekStart }?.score ?: 0
        val activeCompletions = snapshot.taskCompletions.filter { it.reversedAt == null }
        val activePenalties = snapshot.penalties.filter { !it.canceled }
        return AccountingProjection.scoreBaselineFromSnapshot(
            materializedScore = score,
            userId = userId,
            weekStartDate = weekStart,
            completions = activeCompletions,
            penalties = activePenalties
        )
    }

    fun walletBaseline(snapshot: ExportData, userId: String): Long {
        val wallet = snapshot.wallets.find { it.userId == userId }?.balance ?: 0
        val activeCompletions = snapshot.taskCompletions.filter { it.reversedAt == null }
        val activePenalties = snapshot.penalties.filter { !it.canceled }
        val approved = snapshot.rewardRequests.filter { it.status == RewardRequestStatus.APPROVED.name }
        return AccountingProjection.walletBaselineFromSnapshot(
            materializedWallet = wallet,
            userId = userId,
            completions = activeCompletions,
            penalties = activePenalties,
            approvedRequests = approved
        )
    }

    fun penaltyWeekStart(createdAt: Long): Long = WeekUtils.getWeekStartMillis(createdAt)
}

private fun TaskCompletion.toExportSnapshot(): ExportTaskCompletion = ExportTaskCompletion(
    id = id,
    taskId = taskId,
    userId = userId,
    taskType = taskType.name,
    completionTime = completionTime,
    weekStartDate = weekStartDate,
    basePoints = basePoints,
    bonusPoints = bonusPoints,
    penaltyPoints = penaltyPoints,
    totalPoints = totalPoints,
    reversedAt = reversedAt,
    reversedByUserId = reversedByUserId,
    updatedAt = updatedAt,
    generatedRepeatTaskId = generatedRepeatTaskId,
    completionCycle = completionCycle
)

private fun com.familyhub.domain.model.RewardRequest.toExportSnapshot(): ExportRewardRequest = ExportRewardRequest(
    id = id,
    rewardId = rewardId,
    rewardTitle = rewardTitle,
    rewardCost = rewardCost,
    requesterUserId = requesterUserId,
    note = note,
    status = status.name,
    requestedAt = requestedAt,
    processedAt = processedAt,
    processedByUserId = processedByUserId
)
