package com.familyhub.domain.usecase

import com.familyhub.data.repository.RewardRequestTransitionCoordinator
import com.familyhub.domain.model.RewardApprovalResult
import javax.inject.Inject

class ApproveRewardRequestUseCase @Inject constructor(
    private val coordinator: RewardRequestTransitionCoordinator
) {
    suspend operator fun invoke(
        requestId: String,
        actorUserId: String
    ): RewardApprovalResult = coordinator.approve(requestId, actorUserId)
}
