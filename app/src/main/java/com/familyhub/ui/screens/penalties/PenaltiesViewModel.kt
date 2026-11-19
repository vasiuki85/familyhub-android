package com.familyhub.ui.screens.penalties

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Penalty
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PenaltiesUiState(
    val isLoading: Boolean = true,
    val penalties: List<Penalty> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val errorMessage: String? = null
)

@HiltViewModel
class PenaltiesViewModel @Inject constructor(
    private val penaltyRepository: PenaltyRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PenaltiesUiState())
    val state: StateFlow<PenaltiesUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            appSettingsDataStore.settingsFlow.flatMapLatest { settings ->
                val currentUserId = settings.currentUserId
                combine(
                    penaltyRepository.getAllPenalties(),
                    memberRepository.getAllMembers()
                ) { penalties, members ->
                    val currentMember = members.find { it.id == currentUserId }
                    val role = currentMember?.role ?: MemberRole.PENDING
                    PenaltiesUiState(
                        isLoading = false,
                        penalties = penalties,
                        members = members,
                        currentUserId = currentUserId,
                        currentUserRole = role,
                        errorMessage = _state.value.errorMessage
                    )
                }
            }.catch { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.General,
                            tag = "PenaltiesViewModel",
                        ),
                    )
                }
            }.collect { newState ->
                _state.update { current ->
                    newState.copy(errorMessage = current.errorMessage)
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun createPenalty(targetUserId: String, amountInput: String, comment: String?) {
        val currentState = _state.value
        if (!PenaltyEligibilityPolicy.canManage(currentState.currentUserRole)) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.penalties_error_only_parents))
            }
            return
        }
        val amount = amountInput.toIntOrNull()
        if (amount == null || amount == 0) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.penalties_error_invalid_amount))
            }
            return
        }
        if (targetUserId.isBlank()) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.penalties_error_target_missing))
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val freshState = _state.value
                val actor = memberRepository.getMemberById(freshState.currentUserId)
                val target = memberRepository.getMemberById(targetUserId)
                val eligibility = PenaltyEligibilityPolicy.validate(actor?.role, target)
                if (eligibility != PenaltyEligibilityResult.Eligible) {
                    _state.update {
                        it.copy(errorMessage = context.getString(eligibility.errorMessageRes()))
                    }
                    return@runCatching
                }
                val authorizedActorId = actor?.id ?: return@runCatching
                penaltyRepository.createPenalty(
                    userId = targetUserId,
                    actorUserId = authorizedActorId,
                    amount = amount,
                    comment = comment?.ifBlank { null },
                    originType = "manual"
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.General,
                            tag = "PenaltiesViewModel",
                        ),
                    )
                }
            }
        }
    }

    fun cancelPenalty(penaltyId: String) {
        val currentState = _state.value
        if (!PenaltyEligibilityPolicy.canManage(currentState.currentUserRole)) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.penalties_error_only_parents))
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val currentUserId = _state.value.currentUserId
                val actorRole = memberRepository.getMemberById(currentUserId)?.role
                if (!PenaltyEligibilityPolicy.canManage(actorRole)) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.penalties_error_only_parents))
                    }
                    return@runCatching
                }
                penaltyRepository.cancelPenalty(
                    id = penaltyId,
                    actorUserId = currentUserId
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.General,
                            tag = "PenaltiesViewModel",
                        ),
                    )
                }
            }
        }
    }

    private fun PenaltyEligibilityResult.errorMessageRes(): Int = when (this) {
        PenaltyEligibilityResult.Eligible -> error("Eligible result has no error message")
        PenaltyEligibilityResult.ActorNotAllowed -> R.string.penalties_error_only_parents
        PenaltyEligibilityResult.TargetMissing -> R.string.penalties_error_target_missing
        PenaltyEligibilityResult.TargetPending -> R.string.penalties_error_target_pending
    }
}
