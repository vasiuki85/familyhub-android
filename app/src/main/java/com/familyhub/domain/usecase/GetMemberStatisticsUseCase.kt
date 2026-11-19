package com.familyhub.domain.usecase

import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.domain.model.MemberRewardsSummary
import com.familyhub.domain.model.MemberStatistics
import com.familyhub.domain.model.MemberTaskTypeBreakdown
import com.familyhub.domain.model.MemberWeekScore
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.model.TaskCompletion
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.model.UserAchievement
import com.familyhub.domain.model.UserWallet
import com.familyhub.domain.utils.MemberPointsStatistics
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.domain.utils.displayLabel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetMemberStatisticsUseCase @Inject constructor(
    private val familyMemberRepository: FamilyMemberRepository,
    private val scoreRepository: ScoreRepository,
    private val rewardRepository: RewardRepository,
    private val penaltyRepository: PenaltyRepository
) {
    suspend operator fun invoke(): List<MemberStatistics> {
        val members = familyMemberRepository.getAllMembers().first()
        if (members.isEmpty()) {
            return emptyList()
        }

        val completions = scoreRepository.getAllActiveTaskCompletions().first()
        val achievements = scoreRepository.getAllAchievements().first()
        val wallets = scoreRepository.getAllWallets().first()
        val scores = scoreRepository.getScoresFromWeek(0L).first()
        val rewardRequests = rewardRepository.getAllRequests().first()
        val penalties = penaltyRepository.getAllPenalties().first()

        val completionsByUser = completions.groupBy(TaskCompletion::userId)
        val achievementsByUser = achievements.groupBy { it.userId }
        val walletByUser = wallets.associateBy(UserWallet::userId)
        val scoresByUser = scores.groupBy { it.userId }
        val requestsByUser = rewardRequests.groupBy(RewardRequest::requesterUserId)
        val penaltiesByUser = penalties.groupBy(Penalty::userId)

        return members.map { member ->
            val memberCompletions = completionsByUser[member.id].orEmpty()
            val memberPenalties = penaltiesByUser[member.id].orEmpty()
            val memberScoreWeeks = scoresByUser[member.id].orEmpty().map { it.weekStartDate }
            val completionWeeks = memberCompletions.map(TaskCompletion::weekStartDate)
            val penaltyWeeks = memberPenalties
                .filter { penalty -> !penalty.canceled }
                .map { penalty -> WeekUtils.getWeekStartMillis(penalty.createdAt) }
            val trackedWeeks = (memberScoreWeeks + completionWeeks + penaltyWeeks).distinct()

            val weeklyScores = MemberPointsStatistics.weeklyScoresFromCompletionsAndPenalties(
                completions = memberCompletions,
                penalties = memberPenalties,
                weekStarts = trackedWeeks,
            ).takeLast(8)

            val totalPointsEarned = MemberPointsStatistics.pointsEarnedFromCompletions(memberCompletions)
            val totalPointsDeducted = MemberPointsStatistics.pointsDeductedFromPenalties(memberPenalties)
            val averagePoints = MemberPointsStatistics.averageWeeklyPointsEarned(weeklyScores)
            val bestWeek = MemberPointsStatistics.bestWeekFromEarnedScores(weeklyScores)

            val taskTypeBreakdown = TaskType.values().mapNotNull { type ->
                val count = memberCompletions.count { completion -> completion.taskType == type }
                if (count > 0) MemberTaskTypeBreakdown(taskType = type, count = count) else null
            }
            val memberAchievements = achievementsByUser[member.id]
                .orEmpty()
                .sortedByDescending(UserAchievement::earnedAt)
            val requests = requestsByUser[member.id].orEmpty()
            val rewardsSummary = MemberRewardsSummary(
                approved = requests.count { it.status == RewardRequestStatus.APPROVED },
                pending = requests.count { it.status == RewardRequestStatus.PENDING },
                declined = requests.count { it.status == RewardRequestStatus.DECLINED }
            )

            val penaltiesCount = memberPenalties.count { penalty -> !penalty.canceled }
            val penaltiesAmount = totalPointsDeducted

            MemberStatistics(
                member = member,
                totalTasksCompleted = memberCompletions.size,
                totalPointsEarned = totalPointsEarned,
                averageWeeklyPoints = averagePoints,
                bestWeek = bestWeek,
                weeklyScores = weeklyScores,
                taskTypeBreakdown = taskTypeBreakdown,
                achievements = memberAchievements,
                rewardsSummary = rewardsSummary,
                walletBalance = walletByUser[member.id]?.balance ?: 0,
                totalPenaltiesCount = penaltiesCount,
                totalPenaltiesAmount = penaltiesAmount
            )
        }.sortedBy { it.member.displayLabel().lowercase() }
    }
}
