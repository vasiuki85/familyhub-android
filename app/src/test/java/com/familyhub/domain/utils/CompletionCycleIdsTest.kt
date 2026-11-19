package com.familyhub.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class CompletionCycleIdsTest {

    @Test
    fun deterministicId_isStableForSameInputs() {
        val first = CompletionCycleIds.deterministicId("task-1", 0L)
        val second = CompletionCycleIds.deterministicId("task-1", 0L)
        assertEquals(first, second)
    }

    @Test
    fun deterministicId_differsForDifferentCycles() {
        val cycle0 = CompletionCycleIds.deterministicId("task-1", 0L)
        val cycle1 = CompletionCycleIds.deterministicId("task-1", 1L)
        assertNotEquals(cycle0, cycle1)
    }

    @Test
    fun deterministicId_avoidsPrefixCollision() {
        val short = CompletionCycleIds.deterministicId("ab", 12L)
        val long = CompletionCycleIds.deterministicId("ab:12", 0L)
        assertNotEquals(short, long)
    }

    @Test
    fun deterministicId_rejectsNegativeCycle() {
        assertThrows(com.familyhub.domain.model.TaskTransitionInvalidException::class.java) {
            CompletionCycleIds.deterministicId("task-1", -1L)
        }
    }
}
