package com.familyhub.ui.screens.rating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.UserAchievement
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingUiState(
    val items: List<RatingRow> = emptyList(),
    val maxScore: Int = 0,
    val history: List<WeekHistoryUi> = emptyList(),
    val trend: Map<String, List<ScorePoint>> = emptyMap(),
    val selectedMemberId: String? = null,
    val members: List<FamilyMember> = emptyList(),
    val achievements: List<UserAchievement> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class RatingRow(
    val memberName: String,
    val score: Int,
    val avatarColor: String
)

data class WeekHistoryUi(
    val weekStart: Long,
    val rows: List<RatingRow>
)

data class ScorePoint(
    val weekStart: Long,
    val score: Int
)

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val scoreRepository: ScoreRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(RatingUiState())
    val state: StateFlow<RatingUiState> = _state.asStateFlow()

    private val selectedMemberId = MutableStateFlow<String?>(null)

    private val historyWeeks = 6
    private val millisInWeek = java.util.concurrent.TimeUnit.DAYS.toMillis(7)

    init {
        observeScores()
        observeAchievements()
    }

    private fun observeScores() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val currentWeekStart = WeekUtils.getCurrentWeekStartMillis()
                val historyStart = currentWeekStart - (historyWeeks - 1) * millisInWeek
                combine(
                    appSettingsDataStore.settingsFlow,
                    memberRepository.getAllMembers(),
                    scoreRepository.getScoresForWeek(currentWeekStart),
                    scoreRepository.getScoresFromWeek(historyStart)
                ) { settings, members, currentWeekScores, allScores ->
                    val membersMap = members.associateBy { it.id }
                    val ratingRows = currentWeekScores.mapNotNull { score ->
                        val member = membersMap[score.userId]
                        if (member != null) {
                            RatingRow(
                                memberName = member.displayLabel(),
                                score = score.score,
                                avatarColor = member.avatarColor
                            )
                        } else null
                    }.sortedByDescending { it.score }

                    val maxScore = ratingRows.maxOfOrNull { it.score } ?: 0

                    val history = allScores
                        .groupBy { it.weekStartDate }
                        .mapNotNull { (weekStart, weekScores) ->
                            val rows = weekScores.mapNotNull { score ->
                                val member = membersMap[score.userId]
                                if (member != null) {
                                    RatingRow(
                                        memberName = member.displayLabel(),
                                        score = score.score,
                                        avatarColor = member.avatarColor
                                    )
                                } else null
                            }.sortedByDescending { it.score }
                            if (rows.isNotEmpty()) WeekHistoryUi(weekStart, rows) else null
                        }
                        .sortedByDescending { it.weekStart }
                        .take(historyWeeks)

                    val trend = allScores
                        .groupBy { it.userId }
                        .mapValues { (_, scoresForUser) ->
                            scoresForUser
                                .sortedBy { it.weekStartDate }
                                .map { score -> ScorePoint(score.weekStartDate, score.score) }
                        }

                    val targetMemberId = selectedMemberId.value
                        ?: settings.currentUserId.takeIf { it.isNotBlank() }
                        ?: members.firstOrNull()?.id
                    selectedMemberId.value = targetMemberId

                    RatingUiState(
                        items = ratingRows,
                        maxScore = maxScore,
                        history = history,
                        trend = trend,
                        selectedMemberId = targetMemberId,
                        members = members,
                        achievements = _state.value.achievements,
                        isLoading = false,
                        errorMessage = null
                    )
                }.collect { newState ->
                    _state.value = newState
                }
            } catch (e: Exception) {
                _state.value = RatingUiState(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.LoadData,
                        tag = "RatingViewModel",
                    ),
                )
            }
        }
    }

    private fun observeAchievements() {
        viewModelScope.launch {
            selectedMemberId
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { memberId ->
                    scoreRepository.getAchievementsForUser(memberId)
                }
                .collect { achievements ->
                    _state.value = _state.value.copy(
                        achievements = achievements,
                        selectedMemberId = selectedMemberId.value
                    )
                }
        }
    }

    fun selectMember(memberId: String) {
        selectedMemberId.value = memberId
        _state.value = _state.value.copy(selectedMemberId = memberId)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}

