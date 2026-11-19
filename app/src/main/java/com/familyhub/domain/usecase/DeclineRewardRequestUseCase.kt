package com.familyhub.domain.usecase

import com.familyhub.data.repository.RewardRequestTransitionCoordinator
import com.familyhub.domain.model.RewardDeclineResult
import javax.inject.Inject

class DeclineRewardRequestUseCase @Inject constructor(
    private val coordinator: RewardRequestTransitionCoordinator
) {
    suspend operator fun invoke(
        requestId: String,
        actorUserId: String
    ): RewardDeclineResult = coordinator.decline(requestId, actorUserId)
}
