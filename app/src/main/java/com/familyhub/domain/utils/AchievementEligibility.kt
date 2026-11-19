package com.familyhub.domain.utils

import com.familyhub.domain.model.AchievementType

data class AchievementEligibilitySnapshot(
    val totalCompletions: Int,
    val currentWeekCompletionCount: Int,
    val currentWeekLateCompletionCount: Int,
    val hasAnyPerfectWeek: Boolean,
)

object AchievementEligibility {
    fun newlyUnlockedTypes(
        snapshot: AchievementEligibilitySnapshot,
        existingTypes: Set<AchievementType>,
    ): List<AchievementType> {
        val unlocked = mutableListOf<AchievementType>()
        if (snapshot.totalCompletions >= 1 && AchievementType.FIRST_TASK !in existingTypes) {
            unlocked += AchievementType.FIRST_TASK
        }
        if (snapshot.totalCompletions >= 10 && AchievementType.TEN_TASKS !in existingTypes) {
            unlocked += AchievementType.TEN_TASKS
        }
        if (
            isPerfectWeek(
                completionCount = snapshot.currentWeekCompletionCount,
                lateCompletionCount = snapshot.currentWeekLateCompletionCount,
            ) &&
            AchievementType.PERFECT_WEEK !in existingTypes
        ) {
            unlocked += AchievementType.PERFECT_WEEK
        }
        return unlocked
    }

    fun typesToRevoke(
        snapshot: AchievementEligibilitySnapshot,
        existingTypes: Set<AchievementType>,
    ): List<AchievementType> {
        val revoked = mutableListOf<AchievementType>()
        if (AchievementType.FIRST_TASK in existingTypes && snapshot.totalCompletions < 1) {
            revoked += AchievementType.FIRST_TASK
        }
        if (AchievementType.TEN_TASKS in existingTypes && snapshot.totalCompletions < 10) {
            revoked += AchievementType.TEN_TASKS
        }
        if (AchievementType.PERFECT_WEEK in existingTypes && !snapshot.hasAnyPerfectWeek) {
            revoked += AchievementType.PERFECT_WEEK
        }
        return revoked
    }

    fun isPerfectWeek(completionCount: Int, lateCompletionCount: Int): Boolean =
        completionCount > 0 && lateCompletionCount == 0
}
