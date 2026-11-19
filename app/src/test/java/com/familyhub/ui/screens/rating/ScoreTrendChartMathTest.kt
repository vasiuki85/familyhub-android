package com.familyhub.ui.screens.rating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTrendChartMathTest {
    @Test
    fun hasEnoughPointsForChart_requiresAtLeastTwoPoints() {
        assertFalse(ScoreTrendChartMath.hasEnoughPointsForChart(0))
        assertFalse(ScoreTrendChartMath.hasEnoughPointsForChart(1))
        assertTrue(ScoreTrendChartMath.hasEnoughPointsForChart(2))
    }

    @Test
    fun chartRange_negativeScores_includeZeroAndMax() {
        val range = ScoreTrendChartMath.chartRange(
            listOf(
                ScorePoint(1L, -5),
                ScorePoint(2L, 10),
                ScorePoint(3L, -2),
            ),
        )

        assertEquals(-5, range.min)
        assertEquals(10, range.max)
    }

    @Test
    fun chartRange_allPositive_startsAtZero() {
        val range = ScoreTrendChartMath.chartRange(
            listOf(ScorePoint(1L, 3), ScorePoint(2L, 8)),
        )

        assertEquals(0, range.min)
        assertEquals(8, range.max)
    }

    @Test
    fun chartRange_allNegative_endsAtZero() {
        val range = ScoreTrendChartMath.chartRange(
            listOf(ScorePoint(1L, -8), ScorePoint(2L, -2)),
        )

        assertEquals(-8, range.min)
        assertEquals(0, range.max)
    }

    @Test
    fun zeroLineRatio_presentWhenRangeCrossesZero() {
        val range = ScoreTrendChartMath.chartRange(
            listOf(ScorePoint(1L, -4), ScorePoint(2L, 6)),
        )

        assertEquals(0.4f, ScoreTrendChartMath.zeroLineRatio(range)!!, 0.001f)
    }

    @Test
    fun zeroLineRatio_presentWhenRangeIncludesZero() {
        val positiveOnly = ScoreTrendChartMath.chartRange(
            listOf(ScorePoint(1L, 2), ScorePoint(2L, 6)),
        )

        assertEquals(0f, ScoreTrendChartMath.zeroLineRatio(positiveOnly)!!, 0.001f)
    }

    @Test
    fun labelIndices_limitsToFiveLabels() {
        assertEquals(
            listOf(0, 3, 6, 9, 12),
            ScoreTrendChartMath.labelIndices(pointCount = 13),
        )
    }
}
