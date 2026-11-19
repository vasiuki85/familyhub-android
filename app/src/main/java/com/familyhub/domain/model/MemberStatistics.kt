package com.familyhub.domain.model

data class MemberStatistics(
    val member: FamilyMember,
    val totalTasksCompleted: Int,
    val totalPointsEarned: Int,
    val averageWeeklyPoints: Double,
    val bestWeek: MemberBestWeek?,
    val weeklyScores: List<MemberWeekScore>,
    val taskTypeBreakdown: List<MemberTaskTypeBreakdown>,
    val achievements: List<UserAchievement>,
    val rewardsSummary: MemberRewardsSummary,
    val walletBalance: Int,
    val totalPenaltiesCount: Int = 0,
    val totalPenaltiesAmount: Int = 0
)

data class MemberBestWeek(
    val weekStart: Long,
    val points: Int
)

data class MemberWeekScore(
    val weekStart: Long,
    val pointsEarned: Int,
    val pointsDeducted: Int = 0,
)

data class MemberTaskTypeBreakdown(
    val taskType: TaskType,
    val count: Int
)

data class MemberRewardsSummary(
    val approved: Int,
    val pending: Int,
    val declined: Int
)
