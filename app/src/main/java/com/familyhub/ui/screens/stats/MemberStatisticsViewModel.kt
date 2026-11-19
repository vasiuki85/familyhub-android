package com.familyhub.ui.screens.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.domain.model.MemberStatistics
import com.familyhub.domain.usecase.GetMemberStatisticsUseCase
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemberStatisticsUiState(
    val isLoading: Boolean = false,
    val members: List<MemberStatistics> = emptyList(),
    val selectedMemberId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MemberStatisticsViewModel @Inject constructor(
    private val getMemberStatisticsUseCase: GetMemberStatisticsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        MemberStatisticsUiState(
            isLoading = true,
            members = emptyList(),
            selectedMemberId = null,
            errorMessage = null
        )
    )
    val state: StateFlow<MemberStatisticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val members = getMemberStatisticsUseCase()
                val currentSelection = _state.value.selectedMemberId
                val selectedId = when {
                    members.isEmpty() -> null
                    currentSelection != null && members.any { it.member.id == currentSelection } -> currentSelection
                    else -> members.first().member.id
                }
                _state.value = MemberStatisticsUiState(
                    isLoading = false,
                    members = members,
                    selectedMemberId = selectedId,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.LoadData,
                        tag = "MemberStatisticsViewModel",
                    ),
                )
            }
        }
    }

    fun selectMember(memberId: String) {
        _state.value = _state.value.copy(selectedMemberId = memberId)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
