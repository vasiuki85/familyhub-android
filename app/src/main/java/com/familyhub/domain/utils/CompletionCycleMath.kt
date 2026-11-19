package com.familyhub.domain.utils

import com.familyhub.domain.model.TaskTransitionInvalidException

object CompletionCycleMath {
    fun nextCycle(current: Long): Long {
        CompletionCycleValidation.requireNonNegativeTaskCycle(current)
        val next = current + 1L
        if (next < current) {
            throw TaskTransitionInvalidException("Completion cycle overflow")
        }
        return next
    }
}
