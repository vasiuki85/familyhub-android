package com.familyhub.ui.screens.appreciations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.AppreciationRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.domain.model.Appreciation
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.displayLabel
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AppreciationListItem(
    val appreciation: Appreciation,
    val sender: FamilyMember?,
    val recipient: FamilyMember?,
    val senderName: String,
    val recipientName: String,
    val createdAtText: String
)

data class AppreciationsUiState(
    val isLoading: Boolean = true,
    val appreciations: List<AppreciationListItem> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val errorMessage: String? = null
) {
    val canMutateFamily: Boolean
        get() = currentUserRole != MemberRole.PENDING
}

@HiltViewModel
class AppreciationsViewModel @Inject constructor(
    private val appreciationRepository: AppreciationRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(AppreciationsUiState())
    val state: StateFlow<AppreciationsUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                appreciationRepository.getAll(),
                memberRepository.getAllMembers(),
                appSettingsDataStore.settingsFlow
            ) { appreciations, members, settings ->
                val memberMap = members.associateBy(FamilyMember::id)
                AppreciationsUiState(
                    isLoading = false,
                    appreciations = appreciations.map { appreciation ->
                        AppreciationListItem(
                            appreciation = appreciation,
                            sender = memberMap[appreciation.senderId],
                            recipient = memberMap[appreciation.recipientId],
                            senderName = memberMap[appreciation.senderId]?.displayLabel()
                                ?: context.getString(R.string.appreciations_unknown_member),
                            recipientName = memberMap[appreciation.recipientId]?.displayLabel()
                                ?: context.getString(R.string.appreciations_unknown_member),
                            createdAtText = TimeUtils.formatDateTime(appreciation.createdAt)
                        )
                    },
                    members = members,
                    currentUserId = settings.currentUserId,
                    currentUserRole = members.firstOrNull {
                        it.id == settings.currentUserId
                    }?.role ?: MemberRole.PENDING,
                    errorMessage = null
                )
            }.catch { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.AppreciationsLoad,
                        tag = "AppreciationsViewModel",
                    ),
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun sendAppreciation(recipientId: String?, message: String): Boolean {
        val currentState = _state.value
        if (!currentState.canMutateFamily) return false
        val trimmedMessage = message.trim()
        if (recipientId.isNullOrBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.appreciations_error_missing_recipient)
            )
            return false
        }
        if (trimmedMessage.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.appreciations_error_message_required)
            )
            return false
        }
        val senderId = currentState.currentUserId
        if (senderId.isBlank()) {
            _state.value = currentState.copy(
                errorMessage = context.getString(R.string.error_no_current_user)
            )
            return false
        }

        viewModelScope.launch {
            val appreciation = Appreciation(
                id = idGenerator.newId(),
                senderId = senderId,
                recipientId = recipientId,
                message = trimmedMessage,
                createdAt = TimeUtils.now()
            )

            runCatching { appreciationRepository.add(appreciation) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.AppreciationsCreate,
                            tag = "AppreciationsViewModel",
                        ),
                    )
                }
        }
        return true
    }

    fun deleteAppreciation(appreciation: Appreciation) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            runCatching { appreciationRepository.delete(appreciation) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.AppreciationsDelete,
                            tag = "AppreciationsViewModel",
                        ),
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
