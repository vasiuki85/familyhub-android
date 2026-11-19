package com.familyhub.domain.utils

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportPenalty

class PenaltyValidationException(message: String) : IllegalArgumentException(message)

object PenaltySnapshotValidation {
    private const val MIN_VALID_AMOUNT = Int.MIN_VALUE + 1
    private const val MAX_VALID_AMOUNT = -1

    fun validateExportSnapshotPenalties(data: ExportData) {
        data.penalties.forEach(::validatePenalty)
    }

    fun validatePenalty(penalty: ExportPenalty) {
        validateAmount(penalty.amount)
        if (!penalty.canceled && penalty.canceledAt != null) {
            throw PenaltyValidationException("Active penalty cannot have canceledAt")
        }
    }

    fun validateAmount(amount: Int) {
        if (amount !in MIN_VALID_AMOUNT..MAX_VALID_AMOUNT) {
            throw PenaltyValidationException(
                "Penalty amount must be in [-2147483647, -1], got $amount"
            )
        }
    }
}
