package com.familyhub.ui.screens.rewards

import kotlin.math.max

data class RewardProgressUiModel(
    val progress: Float,
    val remainingPoints: Int,
    val progressBalance: Int,
    val canRequest: Boolean,
)

object RewardProgressUiMapper {
    fun map(
        currentBalance: Int,
        rewardCost: Int,
        isActive: Boolean,
    ): RewardProgressUiModel {
        val safeCost = max(0, rewardCost)
        val progressBalance = max(0, currentBalance)
        val progress = when {
            safeCost == 0 -> 1f
            else -> (progressBalance.toFloat() / safeCost.toFloat()).coerceIn(0f, 1f)
        }
        val remainingPoints = if (safeCost == 0) {
            0
        } else {
            (safeCost - currentBalance).coerceAtLeast(0)
        }
        return RewardProgressUiModel(
            progress = progress,
            remainingPoints = remainingPoints,
            progressBalance = progressBalance,
            canRequest = isActive && (safeCost == 0 || currentBalance >= safeCost),
        )
    }
}
