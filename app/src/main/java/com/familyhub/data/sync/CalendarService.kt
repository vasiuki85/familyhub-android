package com.familyhub.data.sync

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.calendar.Calendar

interface CalendarService {
    fun getGoogleSignInClient(): GoogleSignInClient
    fun getCalendarClient(account: GoogleSignInAccount): Calendar
}

