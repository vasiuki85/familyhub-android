package com.familyhub.ui.screens.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.EmergencyContactRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.domain.model.EmergencyContact
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ContactsUiState(
    val isLoading: Boolean = true,
    val contacts: List<EmergencyContact> = emptyList(),
    val errorMessage: String? = null,
    val currentUserRole: MemberRole = MemberRole.PENDING,
) {
    val canManageContacts: Boolean
        get() = currentUserRole == MemberRole.ADMIN || currentUserRole == MemberRole.PARENT
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: EmergencyContactRepository,
    private val memberRepository: FamilyMemberRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            runCatching {
                combine(
                    repository.getAllContacts(),
                    memberRepository.getAllMembers(),
                    appSettingsDataStore.settingsFlow,
                ) { contacts, members, settings ->
                    ContactsUiState(
                        isLoading = false,
                        contacts = contacts,
                        errorMessage = null,
                        currentUserRole = members
                            .firstOrNull { it.id == settings.currentUserId }
                            ?.role
                            ?: MemberRole.PENDING,
                    )
                }.collect { _state.value = it }
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        error,
                        UserFacingErrorContext.Contacts,
                        tag = "ContactsViewModel",
                    ),
                )
            }
        }
    }

    fun addContact(name: String, phoneNumber: String, description: String?) {
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()
        
        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.contacts_error_name_required)
            )
            return
        }
        
        if (trimmedPhone.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.contacts_error_phone_required)
            )
            return
        }

        viewModelScope.launch {
            val contact = EmergencyContact(
                id = idGenerator.newId(),
                name = trimmedName,
                phoneNumber = trimmedPhone,
                description = description?.trim()?.takeIf { it.isNotBlank() },
                createdAt = TimeUtils.now()
            )

            runCatching { repository.upsert(contact) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Contacts,
                            tag = "ContactsViewModel",
                        ),
                    )
                }
        }
    }

    fun updateContact(contact: EmergencyContact, name: String, phoneNumber: String, description: String?) {
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()
        
        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.contacts_error_name_required)
            )
            return
        }
        
        if (trimmedPhone.isBlank()) {
            _state.value = _state.value.copy(
                errorMessage = context.getString(R.string.contacts_error_phone_required)
            )
            return
        }

        viewModelScope.launch {
            val updatedContact = contact.copy(
                name = trimmedName,
                phoneNumber = trimmedPhone,
                description = description?.trim()?.takeIf { it.isNotBlank() }
            )

            runCatching { repository.upsert(updatedContact) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Contacts,
                            tag = "ContactsViewModel",
                        ),
                    )
                }
        }
    }

    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            runCatching { repository.delete(contact) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Contacts,
                            tag = "ContactsViewModel",
                        ),
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}


