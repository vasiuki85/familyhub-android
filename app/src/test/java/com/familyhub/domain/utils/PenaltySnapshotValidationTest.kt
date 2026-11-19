package com.familyhub.domain.utils

import com.familyhub.data.export.ExportPenalty
import org.junit.Assert.assertThrows
import org.junit.Test

class PenaltySnapshotValidationTest {

    @Test
    fun validateAmount_acceptsMinusOne() {
        PenaltySnapshotValidation.validateAmount(-1)
    }

    @Test
    fun validateAmount_acceptsMinValidNegative() {
        PenaltySnapshotValidation.validateAmount(-2147483647)
    }

    @Test
    fun validateAmount_rejectsZero() {
        assertThrows(PenaltyValidationException::class.java) {
            PenaltySnapshotValidation.validateAmount(0)
        }
    }

    @Test
    fun validateAmount_rejectsPositive() {
        assertThrows(PenaltyValidationException::class.java) {
            PenaltySnapshotValidation.validateAmount(10)
        }
    }

    @Test
    fun validateAmount_rejectsIntMinValue() {
        assertThrows(PenaltyValidationException::class.java) {
            PenaltySnapshotValidation.validateAmount(Int.MIN_VALUE)
        }
    }

    @Test
    fun validatePenalty_rejectsActiveWithCanceledAt() {
        assertThrows(PenaltyValidationException::class.java) {
            PenaltySnapshotValidation.validatePenalty(
                penalty(canceled = false, canceledAt = 1L)
            )
        }
    }

    @Test
    fun validatePenalty_allowsLegacyCanceledWithoutCanceledAt() {
        PenaltySnapshotValidation.validatePenalty(
            penalty(canceled = true, canceledAt = null)
        )
    }

    private fun penalty(
        canceled: Boolean,
        canceledAt: Long?,
        amount: Int = -10
    ) = ExportPenalty(
        id = "penalty-1",
        userId = "child",
        issuedByUserId = "parent",
        amount = amount,
        createdAt = 1L,
        canceled = canceled,
        canceledAt = canceledAt
    )
}
