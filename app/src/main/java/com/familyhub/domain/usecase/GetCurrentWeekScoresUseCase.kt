package com.familyhub.domain.usecase

import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.UserScore
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCurrentWeekScoresUseCase @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val memberRepository: FamilyMemberRepository
) {
    suspend operator fun invoke(): List<Pair<FamilyMember, Int>> {
        val weekStart = WeekUtils.getCurrentWeekStartMillis()
        val scoresFlow = scoreRepository.getScoresForWeek(weekStart)
        val scores = scoresFlow.first()
        val membersFlow = memberRepository.getAllMembers()
        val members = membersFlow.first().associateBy { it.id }

        return scores
            .mapNotNull { score ->
                val user = members[score.userId]
                if (user != null) user to score.score else null
            }
            .sortedByDescending { pair -> pair.second }
    }
}

