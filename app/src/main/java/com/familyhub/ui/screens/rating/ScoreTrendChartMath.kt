package com.familyhub.ui.screens.rating

/**
 * Pure helpers for the weekly score trend chart.
 */
internal object ScoreTrendChartMath {
    const val MIN_POINTS_FOR_CHART = 2

    fun hasEnoughPointsForChart(pointCount: Int): Boolean = pointCount >= MIN_POINTS_FOR_CHART

    private const val MAX_AXIS_LABELS = 5

    data class ChartRange(val min: Int, val max: Int)

    fun chartRange(points: List<ScorePoint>): ChartRange {
        if (points.isEmpty()) return ChartRange(0, 1)

        var min = points.minOf { it.score }
        var max = points.maxOf { it.score }

        if (min > 0) min = 0
        if (max < 0) max = 0
        if (min == max) {
            if (min == 0) {
                max = 1
            } else if (min > 0) {
                min = 0
            } else {
                max = 0
            }
        }
        return ChartRange(min, max)
    }

    fun scoreToRatio(score: Int, range: ChartRange): Float {
        val span = (range.max - range.min).toFloat()
        if (span <= 0f) return 0.5f
        return ((score - range.min) / span).coerceIn(0f, 1f)
    }

    fun zeroLineRatio(range: ChartRange): Float? {
        if (range.min > 0 || range.max < 0) return null
        return scoreToRatio(0, range)
    }

    fun labelIndices(pointCount: Int, maxLabels: Int = MAX_AXIS_LABELS): List<Int> {
        if (pointCount <= 0) return emptyList()
        if (pointCount <= maxLabels) return (0 until pointCount).toList()

        val lastIndex = pointCount - 1
        val step = lastIndex.toFloat() / (maxLabels - 1)
        return (0 until maxLabels)
            .map { index -> (index * step).toInt().coerceIn(0, lastIndex) }
            .distinct()
    }
}
