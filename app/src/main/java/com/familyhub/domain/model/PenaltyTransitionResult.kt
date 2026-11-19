package com.familyhub.domain.model

sealed class PenaltyCreateResult {
    data class Created(val penalty: Penalty) : PenaltyCreateResult()
    data class IdempotentNoOp(val penalty: Penalty) : PenaltyCreateResult()
}

sealed class PenaltyCancelResult {
    data object Canceled : PenaltyCancelResult()
    data object IdempotentNoOp : PenaltyCancelResult()
    data object NotFound : PenaltyCancelResult()
}
