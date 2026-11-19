package com.familyhub.domain.model

data class ScoreImpact(
    val basePoints: Int,
    val bonusPoints: Int,
    val penaltyPoints: Int,
    val totalDelta: Int,
    val achievementsUnlocked: List<AchievementType>
)
