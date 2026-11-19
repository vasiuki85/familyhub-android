package com.familyhub.domain.usecase

import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.model.WeeklyAchievement
import com.familyhub.domain.model.WeeklyFamilyReport
import com.familyhub.domain.model.WeeklyMemberReport
import com.familyhub.domain.model.WeeklyRewardRedemption
import com.familyhub.domain.utils.MemberPointsStatistics
import com.familyhub.domain.utils.WeekUtils
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GenerateWeeklyReportUseCase @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val familyMemberRepository: FamilyMemberRepository,
    private val rewardRepository: RewardRepository,
    private val penaltyRepository: PenaltyRepository,
) {
    private val millisInWeek = TimeUnit.DAYS.toMillis(7)

    suspend operator fun invoke(weekStart: Long = WeekUtils.getCurrentWeekStartMillis()): WeeklyFamilyReport {
        val weekEnd = weekStart + millisInWeek - 1

        val members = familyMemberRepository.getAllMembers().first()
        val memberMap = members.associateBy { it.id }

        val completions = scoreRepository.getTaskCompletionsBetweenWeeks(weekStart, weekStart).first()
        val penalties = penaltyRepository.getAllPenalties().first()
            .filter { penalty -> !penalty.canceled && penalty.createdAt in weekStart..weekEnd }
        val achievements = scoreRepository.getAllAchievements().first()
            .filter { it.earnedAt in weekStart..weekEnd }
        val approvedRequests = rewardRepository.getAllRequests().first()
            .filter { request ->
                request.status == RewardRequestStatus.APPROVED &&
                    request.processedAt != null &&
                    request.processedAt in weekStart..weekEnd
            }

        val completionsByUser = completions.groupBy { it.userId }
        val penaltiesByUser = penalties.groupBy { it.userId }

        val memberSummaries = members.map { member ->
            val memberCompletions = completionsByUser[member.id].orEmpty()
            val memberPenalties = penaltiesByUser[member.id].orEmpty()
            WeeklyMemberReport(
                member = member,
                tasksCompleted = memberCompletions.size,
                pointsEarned = MemberPointsStatistics.pointsEarnedFromCompletions(memberCompletions),
                pointsDeducted = MemberPointsStatistics.pointsDeductedFromPenalties(memberPenalties),
            )
        }

        val totalTasks = memberSummaries.sumOf { it.tasksCompleted }
        val totalPointsEarned = memberSummaries.sumOf { it.pointsEarned }
        val totalPointsDeducted = memberSummaries.sumOf { it.pointsDeducted }

        val achievementSummaries = achievements.mapNotNull { achievement ->
            val member = memberMap[achievement.userId] ?: return@mapNotNull null
            WeeklyAchievement(
                member = member,
                type = achievement.type,
                earnedAt = achievement.earnedAt
            )
        }.sortedBy { it.earnedAt }

        val rewardRedemptions = approvedRequests.mapNotNull { request ->
            val member = memberMap[request.requesterUserId] ?: return@mapNotNull null
            WeeklyRewardRedemption(
                member = member,
                rewardTitle = request.rewardTitle,
                rewardCost = request.rewardCost,
                processedAt = request.processedAt!!
            )
        }.sortedBy { it.processedAt }

        return WeeklyFamilyReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            totalTasksCompleted = totalTasks,
            totalPointsEarned = totalPointsEarned,
            totalPointsDeducted = totalPointsDeducted,
            memberSummaries = memberSummaries,
            achievements = achievementSummaries,
            rewardRedemptions = rewardRedemptions
        )
    }
}
