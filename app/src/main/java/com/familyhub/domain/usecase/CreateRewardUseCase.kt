package com.familyhub.domain.usecase

import com.familyhub.data.repository.RewardRepository
import com.familyhub.domain.model.AccountingTransitionInvalidException
import com.familyhub.domain.model.Reward
import com.familyhub.domain.utils.IdGenerator
import javax.inject.Inject

class CreateRewardUseCase @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(
        title: String,
        description: String?,
        cost: Int,
        createdByUserId: String
    ): Reward {
        if (cost < 0) {
            throw AccountingTransitionInvalidException("Reward cost must be >= 0")
        }
        val reward = Reward(
            id = idGenerator.newId(),
            title = title,
            description = description?.takeIf { it.isNotBlank() },
            cost = cost,
            createdByUserId = createdByUserId,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        rewardRepository.upsertReward(reward)
        return reward
    }
}
