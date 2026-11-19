package com.familyhub.domain.model

/**
 * Агрегированный отчёт о жизни семьи за выбранную неделю.
 */
data class WeeklyFamilyReport(
    val weekStart: Long,
    val weekEnd: Long,
    val totalTasksCompleted: Int,
    val totalPointsEarned: Int,
    val totalPointsDeducted: Int = 0,
    val memberSummaries: List<WeeklyMemberReport>,
    val achievements: List<WeeklyAchievement>,
    val rewardRedemptions: List<WeeklyRewardRedemption>
)

/**
 * Сводка по отдельному члену семьи в рамках недельного отчёта.
 */
data class WeeklyMemberReport(
    val member: FamilyMember,
    val tasksCompleted: Int,
    val pointsEarned: Int,
    val pointsDeducted: Int = 0,
)

/**
 * Информация о достижении, заработанном в течение недели.
 */
data class WeeklyAchievement(
    val member: FamilyMember,
    val type: AchievementType,
    val earnedAt: Long
)

/**
 * Информация о награде, полученной за неделю.
 */
data class WeeklyRewardRedemption(
    val member: FamilyMember,
    val rewardTitle: String,
    val rewardCost: Int,
    val processedAt: Long
)
