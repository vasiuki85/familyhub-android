package com.familyhub.domain.usecase

import com.familyhub.data.repository.RewardRepository
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.IdGenerator
import javax.inject.Inject

sealed class CreateRewardRequestResult {
    data class Success(val requestId: String) : CreateRewardRequestResult()
    object RewardNotFound : CreateRewardRequestResult()
    object RewardInactive : CreateRewardRequestResult()
    object InvalidRewardCost : CreateRewardRequestResult()
}

class CreateRewardRequestUseCase @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(
        rewardId: String,
        requesterId: String,
        note: String?
    ): CreateRewardRequestResult {
        val reward = rewardRepository.getRewardById(rewardId)
            ?: return CreateRewardRequestResult.RewardNotFound
        if (!reward.isActive) {
            return CreateRewardRequestResult.RewardInactive
        }
        if (reward.cost < 0) {
            return CreateRewardRequestResult.InvalidRewardCost
        }
        val request = RewardRequest(
            id = idGenerator.newId(),
            rewardId = reward.id,
            rewardTitle = reward.title,
            rewardCost = reward.cost,
            requesterUserId = requesterId,
            note = note?.takeIf { it.isNotBlank() },
            status = RewardRequestStatus.PENDING,
            requestedAt = System.currentTimeMillis(),
            processedAt = null,
            processedByUserId = null
        )
        rewardRepository.upsertRequest(request)
        return CreateRewardRequestResult.Success(request.id)
    }
}
