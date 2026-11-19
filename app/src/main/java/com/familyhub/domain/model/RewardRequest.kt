package com.familyhub.domain.model

/**
 * Заявка пользователя на получение награды.
 */
data class RewardRequest(
    val id: String,
    val rewardId: String,
    val rewardTitle: String,
    val rewardCost: Int,
    val requesterUserId: String,
    val note: String?,
    val status: RewardRequestStatus,
    val requestedAt: Long,
    val processedAt: Long?,
    val processedByUserId: String?
)
