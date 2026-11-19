package com.familyhub.ui.screens.calendar

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.calendar.model.CalendarListEntry

data class CalendarSyncUiState(
    val isSignedIn: Boolean = false,
    val googleSignInAccount: GoogleSignInAccount? = null,
    val calendars: List<CalendarListEntry> = emptyList(),
    val selectedCalendarId: String? = null,
    val needsCalendarScopeGrant: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSignInDialog: Boolean = false,
    val syncCompleted: Boolean = false,
    val signInErrorCode: Int? = null,
    val signInErrorName: String? = null
)
