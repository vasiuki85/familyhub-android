package com.familyhub.domain.utils

import com.familyhub.domain.model.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEligibilityTest {
    @Test
    fun newlyUnlockedTypes_grantsFirstTaskAfterFirstCompletion() {
        val unlocked = AchievementEligibility.newlyUnlockedTypes(
            snapshot = snapshot(totalCompletions = 1),
            existingTypes = emptySet(),
        )

        assertEquals(listOf(AchievementType.FIRST_TASK, AchievementType.PERFECT_WEEK), unlocked)
    }

    @Test
    fun newlyUnlockedTypes_doesNotGrantPerfectWeekWithoutCompletions() {
        val unlocked = AchievementEligibility.newlyUnlockedTypes(
            snapshot = snapshot(
                totalCompletions = 0,
                currentWeekCompletionCount = 0,
                currentWeekLateCompletionCount = 0,
                hasAnyPerfectWeek = false,
            ),
            existingTypes = emptySet(),
        )

        assertTrue(unlocked.isEmpty())
    }

    @Test
    fun typesToRevoke_removesFirstTaskWhenNoCompletionsRemain() {
        val revoked = AchievementEligibility.typesToRevoke(
            snapshot = snapshot(totalCompletions = 0, hasAnyPerfectWeek = false),
            existingTypes = setOf(AchievementType.FIRST_TASK, AchievementType.PERFECT_WEEK),
        )

        assertEquals(
            listOf(AchievementType.FIRST_TASK, AchievementType.PERFECT_WEEK),
            revoked,
        )
    }

    private fun snapshot(
        totalCompletions: Int,
        currentWeekCompletionCount: Int = totalCompletions,
        currentWeekLateCompletionCount: Int = 0,
        hasAnyPerfectWeek: Boolean = currentWeekCompletionCount > 0 && currentWeekLateCompletionCount == 0,
    ) = AchievementEligibilitySnapshot(
        totalCompletions = totalCompletions,
        currentWeekCompletionCount = currentWeekCompletionCount,
        currentWeekLateCompletionCount = currentWeekLateCompletionCount,
        hasAnyPerfectWeek = hasAnyPerfectWeek,
    )
}
