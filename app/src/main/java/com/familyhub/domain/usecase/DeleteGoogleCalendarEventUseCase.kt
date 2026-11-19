package com.familyhub.domain.usecase

import android.content.Context
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.CalendarService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteGoogleCalendarEventUseCase @Inject constructor(
    private val googleCalendarService: CalendarService,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(googleEventId: String) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val calendarId = syncSettingsDataStore.calendarSyncSettingsFlow.first().googleCalendarId
        if (account == null || calendarId == null) return@withContext

        val client = googleCalendarService.getCalendarClient(account)
        try {
            client.events().delete(calendarId, googleEventId).execute()
        } catch (_: Exception) {
            // Ignore remote deletion errors to avoid blocking local delete
        }
    }
}
