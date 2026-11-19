package com.familyhub.domain.utils

import com.familyhub.domain.model.Penalty
import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberPointsStatisticsTest {
    @Test
    fun pointsEarnedFromCompletions_sumsOnlyPositiveCompletionTotals() {
        val earned = MemberPointsStatistics.pointsEarnedFromCompletions(
            listOf(
                completion(totalPoints = 10),
                completion(totalPoints = 5),
            ),
        )

        assertEquals(15, earned)
    }

    @Test
    fun pointsDeductedFromPenalties_ignoresCanceledPenalties() {
        val deducted = MemberPointsStatistics.pointsDeductedFromPenalties(
            listOf(
                penalty(amount = -30),
                penalty(amount = -20, canceled = true),
            ),
        )

        assertEquals(30, deducted)
    }

    @Test
    fun bestWeekFromEarnedScores_ignoresNegativeOrZeroWeeks() {
        val bestWeek = MemberPointsStatistics.bestWeekFromEarnedScores(
            MemberPointsStatistics.weeklyScoresFromCompletionsAndPenalties(
                completions = listOf(
                    completion(totalPoints = 5, weekStart = 1L),
                    completion(totalPoints = 12, weekStart = 2L),
                ),
                penalties = listOf(penalty(amount = -30, createdAt = 3L)),
                weekStarts = listOf(1L, 2L, 3L),
            ),
        )

        assertEquals(2L, bestWeek?.weekStart)
        assertEquals(12, bestWeek?.points)
    }

    @Test
    fun bestWeekFromEarnedScores_returnsNullWhenNoPositiveWeekExists() {
        val bestWeek = MemberPointsStatistics.bestWeekFromEarnedScores(
            MemberPointsStatistics.weeklyScoresFromCompletionsAndPenalties(
                completions = emptyList(),
                penalties = listOf(penalty(amount = -30, createdAt = 10L)),
                weekStarts = listOf(10L),
            ),
        )

        assertNull(bestWeek)
    }

    private fun completion(
        totalPoints: Int,
        weekStart: Long = 1L,
    ) = TaskCompletion(
        id = "completion-$totalPoints-$weekStart",
        taskId = "task-1",
        userId = "user-1",
        taskType = TaskType.ROUTINE,
        completionTime = weekStart,
        weekStartDate = weekStart,
        basePoints = totalPoints,
        bonusPoints = 0,
        penaltyPoints = 0,
        totalPoints = totalPoints,
    )

    private fun penalty(
        amount: Int,
        createdAt: Long = 1L,
        canceled: Boolean = false,
    ) = Penalty(
        id = "penalty-$createdAt",
        userId = "user-1",
        issuedByUserId = "parent-1",
        amount = amount,
        createdAt = createdAt,
        canceled = canceled,
    )
}
