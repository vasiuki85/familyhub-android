package com.familyhub.domain.utils

import com.familyhub.domain.model.TaskTransitionInvalidException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class CompletionCycleMathTest {

    @Test
    fun nextCycle_incrementsNonNegativeValue() {
        assertEquals(1L, CompletionCycleMath.nextCycle(0L))
        assertEquals(3L, CompletionCycleMath.nextCycle(2L))
    }

    @Test
    fun nextCycle_rejectsNegativeCurrent() {
        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleMath.nextCycle(-2L)
        }
    }

    @Test
    fun nextCycle_overflowThrows() {
        assertThrows(TaskTransitionInvalidException::class.java) {
            CompletionCycleMath.nextCycle(Long.MAX_VALUE)
        }
    }
}
