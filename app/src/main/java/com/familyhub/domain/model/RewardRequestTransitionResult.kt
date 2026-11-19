package com.familyhub.domain.model

sealed class RewardApprovalResult {
    data object Success : RewardApprovalResult()
    data object NotEnoughPoints : RewardApprovalResult()
    data object AlreadyProcessed : RewardApprovalResult()
    data object NotFound : RewardApprovalResult()
    data object InvalidRewardCost : RewardApprovalResult()
}

sealed class RewardDeclineResult {
    data object Success : RewardDeclineResult()
    data object AlreadyProcessed : RewardDeclineResult()
    data object NotFound : RewardDeclineResult()
}
