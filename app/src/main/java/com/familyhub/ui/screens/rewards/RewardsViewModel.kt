package com.familyhub.ui.screens.rewards

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Reward
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.UserWallet
import com.familyhub.domain.usecase.ApproveRewardRequestUseCase
import com.familyhub.domain.usecase.CreateRewardRequestResult
import com.familyhub.domain.usecase.CreateRewardRequestUseCase
import com.familyhub.domain.usecase.CreateRewardUseCase
import com.familyhub.domain.usecase.DeclineRewardRequestUseCase
import com.familyhub.domain.model.RewardApprovalResult
import com.familyhub.domain.model.RewardDeclineResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class RewardsUiState(
    val isLoading: Boolean = true,
    val rewards: List<Reward> = emptyList(),
    val requests: List<RewardRequest> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val wallet: UserWallet = UserWallet(userId = "", balance = 0),
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val memberRepository: FamilyMemberRepository,
    private val scoreRepository: ScoreRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val createRewardUseCase: CreateRewardUseCase,
    private val createRewardRequestUseCase: CreateRewardRequestUseCase,
    private val approveRewardRequestUseCase: ApproveRewardRequestUseCase,
    private val declineRewardRequestUseCase: DeclineRewardRequestUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(RewardsUiState())
    val state: StateFlow<RewardsUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            appSettingsDataStore.settingsFlow.flatMapLatest { settings ->
                val currentUserId = settings.currentUserId
                combine(
                    rewardRepository.getAllRewards(),
                    rewardRepository.getAllRequests(),
                    memberRepository.getAllMembers(),
                    scoreRepository.getWalletForUser(currentUserId).map { wallet ->
                        wallet.copy(userId = currentUserId)
                    }
                ) { rewards, requests, members, wallet ->
                    val currentMember = members.find { it.id == currentUserId }
                    val role = currentMember?.role ?: MemberRole.PENDING
                    RewardsUiState(
                        isLoading = false,
                        rewards = rewards,
                        requests = requests,
                        members = members,
                        currentUserId = currentUserId,
                        currentUserRole = role,
                        wallet = wallet,
                        errorMessage = _state.value.errorMessage,
                        infoMessage = _state.value.infoMessage
                    )
                }
            }.catch { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.LoadData,
                        tag = "RewardsViewModel",
                    ),
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearInfo() {
        _state.value = _state.value.copy(infoMessage = null)
    }

    fun createReward(title: String, description: String?, cost: Int) {
        val currentState = _state.value
        if (currentState.currentUserId.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return
        }
        if (currentState.currentUserRole != MemberRole.PARENT && currentState.currentUserRole != MemberRole.ADMIN) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.rewards_error_only_parents_create)
            )
            return
        }
        viewModelScope.launch {
            try {
                createRewardUseCase(title, description, cost, currentState.currentUserId)
                _state.value = _state.value.copy(
                    infoMessage = context.getString(R.string.rewards_message_reward_created),
                    errorMessage = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Rewards,
                        tag = "RewardsViewModel",
                    ),
                )
            }
        }
    }

    fun requestReward(rewardId: String, note: String?) {
        val currentState = _state.value
        if (currentState.currentUserRole == MemberRole.PENDING) return
        if (currentState.currentUserId.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return
        }
        viewModelScope.launch {
            try {
                when (val result = createRewardRequestUseCase(rewardId, currentState.currentUserId, note)) {
                    is CreateRewardRequestResult.Success -> {
                        _state.value = _state.value.copy(
                            infoMessage = context.getString(R.string.rewards_message_request_sent),
                            errorMessage = null
                        )
                    }
                    CreateRewardRequestResult.RewardNotFound -> {
                        _state.value = _state.value.copy(
                            errorMessage = context.getString(R.string.rewards_error_reward_not_found)
                        )
                    }
                    CreateRewardRequestResult.RewardInactive -> {
                        _state.value = _state.value.copy(
                            errorMessage = context.getString(R.string.rewards_error_reward_inactive)
                        )
                    }
                    CreateRewardRequestResult.InvalidRewardCost -> {
                        _state.value = _state.value.copy(
                            errorMessage = context.getString(R.string.rewards_error_invalid_reward_cost)
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Rewards,
                        tag = "RewardsViewModel",
                    ),
                )
            }
        }
    }

    fun approveRequest(requestId: String) {
        val currentState = _state.value
        if (currentState.currentUserRole != MemberRole.PARENT && currentState.currentUserRole != MemberRole.ADMIN) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.rewards_error_only_parents_approve)
            )
            return
        }
        if (currentState.currentUserId.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return
        }
        viewModelScope.launch {
            when (val result = approveRewardRequestUseCase(requestId, currentState.currentUserId)) {
                RewardApprovalResult.Success -> {
                    _state.value = _state.value.copy(
                        infoMessage = context.getString(R.string.rewards_message_request_approved),
                        errorMessage = null
                    )
                }
                RewardApprovalResult.NotEnoughPoints -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_not_enough_points)
                    )
                }
                RewardApprovalResult.AlreadyProcessed -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_request_already_processed)
                    )
                }
                RewardApprovalResult.NotFound -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_request_not_found)
                    )
                }
                RewardApprovalResult.InvalidRewardCost -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_invalid_reward_cost)
                    )
                }
            }
        }
    }

    fun declineRequest(requestId: String) {
        val currentState = _state.value
        if (currentState.currentUserRole != MemberRole.PARENT && currentState.currentUserRole != MemberRole.ADMIN) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.rewards_error_only_parents_approve)
            )
            return
        }
        if (currentState.currentUserId.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return
        }
        viewModelScope.launch {
            when (val result = declineRewardRequestUseCase(requestId, currentState.currentUserId)) {
                RewardDeclineResult.Success -> {
                    _state.value = _state.value.copy(
                        infoMessage = context.getString(R.string.rewards_message_request_declined),
                        errorMessage = null
                    )
                }
                RewardDeclineResult.AlreadyProcessed -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_request_already_processed)
                    )
                }
                RewardDeclineResult.NotFound -> {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.rewards_error_request_not_found)
                    )
                }
            }
        }
    }

    fun toggleRewardActive(reward: Reward, isActive: Boolean) {
        val currentState = _state.value
        if (currentState.currentUserRole != MemberRole.PARENT && currentState.currentUserRole != MemberRole.ADMIN) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.rewards_error_only_parents_create)
            )
            return
        }
        viewModelScope.launch {
            try {
                val updated = reward.copy(isActive = isActive)
                rewardRepository.upsertReward(updated)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Rewards,
                        tag = "RewardsViewModel",
                    ),
                )
            }
        }
    }
}
