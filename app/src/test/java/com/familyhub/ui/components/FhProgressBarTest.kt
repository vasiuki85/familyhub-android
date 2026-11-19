package com.familyhub.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FhProgressBarTest {

    @Test
    fun clampDeterminateProgress_atZero() {
        assertEquals(0f, clampDeterminateProgress(0f))
    }

    @Test
    fun clampDeterminateProgress_atOne() {
        assertEquals(1f, clampDeterminateProgress(1f))
    }

    @Test
    fun clampDeterminateProgress_belowZero_clampsToZero() {
        assertEquals(0f, clampDeterminateProgress(-0.25f))
    }

    @Test
    fun clampDeterminateProgress_aboveOne_clampsToOne() {
        assertEquals(1f, clampDeterminateProgress(1.75f))
    }

    @Test
    fun clampDeterminateProgress_midRange_isPreserved() {
        assertEquals(0.42f, clampDeterminateProgress(0.42f))
    }
}
