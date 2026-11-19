package com.familyhub.data.repository

import com.familyhub.domain.model.AccountingOverflowException
import com.familyhub.domain.model.AccountingTransitionInvalidException

internal object AccountingIntMath {
    fun normalizePenaltyMagnitude(rawAmount: Int): Int {
        if (rawAmount == 0) {
            throw AccountingTransitionInvalidException("Penalty amount must not be zero")
        }
        if (rawAmount == Int.MIN_VALUE) {
            throw AccountingOverflowException("Penalty magnitude out of range")
        }
        val magnitude = kotlin.math.abs(rawAmount.toLong())
        if (magnitude > Int.MAX_VALUE) {
            throw AccountingOverflowException("Penalty magnitude out of range")
        }
        return negateExact(magnitude.toInt())
    }

    fun negateExact(value: Int): Int {
        if (value == Int.MIN_VALUE) {
            throw AccountingOverflowException("Cannot negate Int.MIN_VALUE")
        }
        return toIntExact(-value.toLong())
    }

    fun requireActivePenaltyAmount(amount: Int) {
        if (amount >= 0) {
            throw AccountingTransitionInvalidException("Corrupt penalty amount: $amount")
        }
        if (amount == Int.MIN_VALUE) {
            throw AccountingTransitionInvalidException("Corrupt penalty amount: Int.MIN_VALUE")
        }
    }

    fun penaltyRefundDelta(amount: Int): Int {
        requireActivePenaltyAmount(amount)
        return negateExact(amount)
    }

    fun applyDelta(current: Int?, delta: Int): Int {
        val total = (current?.toLong() ?: 0L) + delta.toLong()
        return toIntExact(total)
    }

    fun toIntExact(value: Long): Int {
        if (value > Int.MAX_VALUE || value < Int.MIN_VALUE) {
            throw AccountingOverflowException("Accounting value out of Int range: $value")
        }
        return value.toInt()
    }

    fun requireNonNegativeRewardCost(cost: Int) {
        if (cost < 0) {
            throw AccountingTransitionInvalidException("Reward cost must be >= 0")
        }
    }
}
