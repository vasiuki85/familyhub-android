package com.familyhub.data.repository

import com.familyhub.domain.model.AccountingOverflowException
import com.familyhub.domain.model.AccountingTransitionInvalidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingIntMathTest {

    @Test
    fun applyDelta_addsCorrectly() {
        assertEquals(150, AccountingIntMath.applyDelta(100, 50))
        assertEquals(50, AccountingIntMath.applyDelta(100, -50))
    }

    @Test
    fun applyDelta_overflowThrows() {
        assertThrows(AccountingOverflowException::class.java) {
            AccountingIntMath.applyDelta(Int.MAX_VALUE, 1)
        }
    }

    @Test
    fun normalizePenaltyMagnitude_positiveInputBecomesNegative() {
        assertEquals(-20, AccountingIntMath.normalizePenaltyMagnitude(20))
    }

    @Test
    fun normalizePenaltyMagnitude_zeroRejected() {
        assertThrows(AccountingTransitionInvalidException::class.java) {
            AccountingIntMath.normalizePenaltyMagnitude(0)
        }
    }

    @Test
    fun normalizePenaltyMagnitude_intMinValueRejected() {
        assertThrows(AccountingOverflowException::class.java) {
            AccountingIntMath.normalizePenaltyMagnitude(Int.MIN_VALUE)
        }
    }

    @Test
    fun negateExact_intMinValueRejected() {
        assertThrows(AccountingOverflowException::class.java) {
            AccountingIntMath.negateExact(Int.MIN_VALUE)
        }
    }

    @Test
    fun penaltyRefundDelta_refundsNegativeAmount() {
        assertEquals(20, AccountingIntMath.penaltyRefundDelta(-20))
    }

    @Test
    fun penaltyRefundDelta_positiveAmountRejected() {
        assertThrows(AccountingTransitionInvalidException::class.java) {
            AccountingIntMath.penaltyRefundDelta(10)
        }
    }

    @Test
    fun penaltyRefundDelta_intMinValueRejected() {
        assertThrows(AccountingTransitionInvalidException::class.java) {
            AccountingIntMath.penaltyRefundDelta(Int.MIN_VALUE)
        }
    }
}
