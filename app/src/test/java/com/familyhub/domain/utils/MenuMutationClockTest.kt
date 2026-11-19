package com.familyhub.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuMutationClockTest {

    @Test
    fun normalProgression_usesWallClockWhenAhead() {
        val clock = DefaultMenuMutationClock(wallClock = { 1_000L })
        assertEquals(1_000L, clock.nextUpdatedAt(100L))
    }

    @Test
    fun rapidEqualWallTime_incrementsFromCurrent() {
        val clock = DefaultMenuMutationClock(wallClock = { 50L })
        assertEquals(101L, clock.nextUpdatedAt(100L))
    }

    @Test
    fun negativeCurrent_rejected() {
        val clock = DefaultMenuMutationClock(wallClock = { 1L })
        assertThrows(IllegalArgumentException::class.java) {
            clock.nextUpdatedAt(-1L)
        }
    }

    @Test
    fun negativeWallClock_doesNotProduceNegativeTimestamp() {
        val clock = DefaultMenuMutationClock(wallClock = { -100L })
        assertEquals(11L, clock.nextUpdatedAt(10L))
        assertTrue(clock.nextUpdatedAt(0L) >= 0L)
    }

    @Test
    fun longMaxValue_throwsOverflow() {
        val clock = DefaultMenuMutationClock(wallClock = { 0L })
        assertThrows(MenuTimestampOverflowException::class.java) {
            clock.nextUpdatedAt(Long.MAX_VALUE)
        }
    }
}
