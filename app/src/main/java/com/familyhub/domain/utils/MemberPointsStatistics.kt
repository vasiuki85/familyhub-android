package com.familyhub.domain.utils

import com.familyhub.domain.model.MemberBestWeek
import com.familyhub.domain.model.MemberWeekScore
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.model.TaskCompletion

object MemberPointsStatistics {
    fun pointsEarnedFromCompletions(completions: Collection<TaskCompletion>): Int =
        completions.sumOf { completion -> completion.totalPoints.coerceAtLeast(0) }

    fun pointsDeductedFromPenalties(penalties: Collection<Penalty>): Int =
        penalties
            .filter { penalty -> !penalty.canceled }
            .sumOf { penalty -> kotlin.math.abs(penalty.amount) }

    fun pointsEarnedForWeek(
        completions: Collection<TaskCompletion>,
        weekStart: Long,
    ): Int = pointsEarnedFromCompletions(
        completions.filter { completion -> completion.weekStartDate == weekStart },
    )

    fun pointsDeductedForWeek(
        penalties: Collection<Penalty>,
        weekStart: Long,
        weekEnd: Long,
    ): Int = pointsDeductedFromPenalties(
        penalties.filter { penalty ->
            !penalty.canceled && penalty.createdAt in weekStart..weekEnd
        },
    )

    fun weeklyEarnedScores(
        completions: Collection<TaskCompletion>,
        weekStarts: Collection<Long>,
    ): List<MemberWeekScore> {
        return weekStarts
            .distinct()
            .sorted()
            .map { weekStart ->
                MemberWeekScore(
                    weekStart = weekStart,
                    pointsEarned = pointsEarnedForWeek(completions, weekStart),
                    pointsDeducted = 0,
                )
            }
    }

    fun weeklyScoresFromCompletionsAndPenalties(
        completions: Collection<TaskCompletion>,
        penalties: Collection<Penalty>,
        weekStarts: Collection<Long>,
    ): List<MemberWeekScore> {
        return weekStarts
            .distinct()
            .sorted()
            .map { weekStart ->
                val weekEnd = weekStart + WEEK_MILLIS - 1
                MemberWeekScore(
                    weekStart = weekStart,
                    pointsEarned = pointsEarnedForWeek(completions, weekStart),
                    pointsDeducted = pointsDeductedForWeek(penalties, weekStart, weekEnd),
                )
            }
    }

    fun bestWeekFromEarnedScores(weeklyScores: Collection<MemberWeekScore>): MemberBestWeek? {
        return weeklyScores
            .filter { week -> week.pointsEarned > 0 }
            .maxByOrNull(MemberWeekScore::pointsEarned)
            ?.let { week ->
                MemberBestWeek(
                    weekStart = week.weekStart,
                    points = week.pointsEarned,
                )
            }
    }

    fun averageWeeklyPointsEarned(weeklyScores: Collection<MemberWeekScore>): Double {
        val weeksWithEarnings = weeklyScores.filter { week -> week.pointsEarned > 0 }
        if (weeksWithEarnings.isEmpty()) {
            return 0.0
        }
        return weeksWithEarnings.sumOf(MemberWeekScore::pointsEarned).toDouble() / weeksWithEarnings.size
    }

    fun recentAverageWeeklyPointsEarned(
        weeklyScores: Collection<MemberWeekScore>,
        weekLimit: Int = RECENT_WEEK_LIMIT,
    ): Double {
        val recentWeeks = weeklyScores
            .sortedBy(MemberWeekScore::weekStart)
            .takeLast(weekLimit)
        return averageWeeklyPointsEarned(recentWeeks)
    }

    private const val RECENT_WEEK_LIMIT = 8
    private const val WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L
}
