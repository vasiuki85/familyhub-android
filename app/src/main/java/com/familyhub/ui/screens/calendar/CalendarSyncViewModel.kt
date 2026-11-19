package com.familyhub.ui.screens.calendar

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.CalendarService
import com.familyhub.domain.usecase.SyncGoogleCalendarUseCase
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.calendar.model.CalendarListEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import javax.inject.Inject

@HiltViewModel
class CalendarSyncViewModel @Inject constructor(
    private val googleCalendarService: CalendarService,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val syncGoogleCalendarUseCase: SyncGoogleCalendarUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarSyncUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            syncSettingsDataStore.calendarSyncSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    selectedCalendarId = settings.googleCalendarId
                )
            }
        }
    }

    fun onSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            _uiState.value = _uiState.value.copy(
                isSignedIn = true,
                googleSignInAccount = account,
                showSignInDialog = false,
                needsCalendarScopeGrant = false,
                error = null,
                signInErrorCode = null,
                signInErrorName = null
            )
            loadCalendars(account)
        } else {
            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                googleSignInAccount = null,
                // Используем признак ошибки без текста, UI покажет строковый ресурс
                error = "error",
                showSignInDialog = false,
                signInErrorCode = null,
                signInErrorName = null
            )
        }
    }

    fun onSignInError(statusCode: Int, statusName: String) {
        _uiState.value = _uiState.value.copy(
            isSignedIn = false,
            googleSignInAccount = null,
            showSignInDialog = false,
            needsCalendarScopeGrant = false,
            error = null,
            signInErrorCode = statusCode,
            signInErrorName = statusName
        )
    }

    fun getSignInIntent(): Intent {
        return googleCalendarService.getGoogleSignInClient().signInIntent
    }

    private fun loadCalendars(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Ensure Calendar scope is actually granted
                val hasScope = GoogleSignIn.hasPermissions(
                    account,
                    Scope(CalendarScopes.CALENDAR)
                )
                if (!hasScope) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        needsCalendarScopeGrant = true,
                        error = null
                    )
                    return@launch
                }
                val calendars = withContext(Dispatchers.IO) {
                    val client = googleCalendarService.getCalendarClient(account)
                    client.calendarList().list().execute().items ?: emptyList()
                }
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Calendar,
                        tag = "CalendarSyncViewModel",
                    ),
                )
            }
        }
    }

    fun onCalendarSelected(calendarId: String) {
        _uiState.value = _uiState.value.copy(selectedCalendarId = calendarId)
    }

    fun saveSelectedCalendar() {
        viewModelScope.launch {
            _uiState.value.selectedCalendarId?.let { calendarId ->
                syncSettingsDataStore.setGoogleCalendarId(calendarId)
                // Optionally kick off a sync right after saving
                try {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    withContext(Dispatchers.IO) { syncGoogleCalendarUseCase(_uiState.value.googleSignInAccount) }
                    _uiState.value = _uiState.value.copy(isLoading = false, syncCompleted = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.Calendar,
                            tag = "CalendarSyncViewModel",
                        ),
                    )
                }
            }
        }
    }

    fun showSignInDialog() {
        _uiState.value = _uiState.value.copy(showSignInDialog = true)
    }

    fun dismissSignInDialog() {
        _uiState.value = _uiState.value.copy(showSignInDialog = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun syncCalendars() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                withContext(Dispatchers.IO) { syncGoogleCalendarUseCase(_uiState.value.googleSignInAccount) }
                _uiState.value = _uiState.value.copy(isLoading = false, syncCompleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.Calendar,
                        tag = "CalendarSyncViewModel",
                    ),
                )
            }
        }
    }
}
