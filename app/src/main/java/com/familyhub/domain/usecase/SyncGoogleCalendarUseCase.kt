package com.familyhub.domain.usecase

import android.content.Context
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.sync.CalendarService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.data.datastore.AppSettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.google.api.services.calendar.model.EventReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

class SyncGoogleCalendarUseCase @Inject constructor(
    private val googleCalendarService: CalendarService,
    private val familyEventRepository: FamilyEventRepository,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(accountOverride: GoogleSignInAccount? = null) = withContext(Dispatchers.IO) {
        val account = accountOverride ?: GoogleSignIn.getLastSignedInAccount(context)
        val calendarId = syncSettingsDataStore.calendarSyncSettingsFlow.first().googleCalendarId
        val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId

        if (account == null || calendarId == null) {
            throw IllegalStateException("Google account not signed in or calendar not selected.")
        }

        val calendarClient = googleCalendarService.getCalendarClient(account)

        // Fetch events from Google Calendar
        val googleEvents = calendarClient.events().list(calendarId)
            .setTimeMin(
                com.google.api.client.util.DateTime(
                    System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
                )
            ) // Last 30 days
            .setTimeMax(
                com.google.api.client.util.DateTime(
                    System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(365)
                )
            ) // Next 365 days
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute()
            .items

        googleEvents?.forEach { googleEvent ->
            val eventId = googleEvent.id
            val existingEvent = familyEventRepository.getEventByGoogleCalendarId(eventId)

            val startTime = googleEvent.start?.dateTime?.value ?: googleEvent.start?.date?.value ?: return@forEach
            val endTime = googleEvent.end?.dateTime?.value ?: googleEvent.end?.date?.value ?: return@forEach

            val durationMinutes = ((endTime - startTime) / (1000 * 60)).toInt()

            val familyEvent = FamilyEvent(
                id = existingEvent?.id ?: idGenerator.newId(),
                title = googleEvent.summary ?: "No title",
                description = googleEvent.description,
                startDateTime = startTime,
                createdByUserId = currentUserId,
                durationMinutes = durationMinutes,
                recurrence = mapGoogleRecurrenceToLocal(googleEvent.recurrence), // MODIFIED
                reminderMinutesBefore = mapGoogleRemindersToLocal(googleEvent.reminders), // MODIFIED
                googleCalendarEventId = eventId
            )
            familyEventRepository.upsertEvent(familyEvent)
        }

        // Push local events to Google Calendar
        val allLocalEvents = familyEventRepository.getAllEvents().first()
        allLocalEvents.forEach { localEvent ->
            if (localEvent.googleCalendarEventId == null) {
                // New event, create in Google Calendar
                val googleEvent = Event().apply {
                    summary = localEvent.title
                    description = localEvent.description
                    start = EventDateTime().setDateTime(com.google.api.client.util.DateTime(localEvent.startDateTime))
                    end = EventDateTime().setDateTime(com.google.api.client.util.DateTime(localEvent.startDateTime + localEvent.durationMinutes * 60 * 1000L))
                    recurrence = mapLocalRecurrenceToGoogle(localEvent.recurrence) // MODIFIED
                    reminders = mapLocalRemindersToGoogle(localEvent.reminderMinutesBefore) // MODIFIED
                }
                val createdEvent = calendarClient.events().insert(calendarId, googleEvent).execute()
                familyEventRepository.upsertEvent(localEvent.copy(googleCalendarEventId = createdEvent.id))
            } else {
                // Existing event, update in Google Calendar
                val googleEvent = calendarClient.events().get(calendarId, localEvent.googleCalendarEventId).execute()
                if (googleEvent != null) {
                    googleEvent.apply {
                        summary = localEvent.title
                        description = localEvent.description
                        start = EventDateTime().setDateTime(com.google.api.client.util.DateTime(localEvent.startDateTime))
                        end = EventDateTime().setDateTime(com.google.api.client.util.DateTime(localEvent.startDateTime + localEvent.durationMinutes * 60 * 1000L))
                        recurrence = mapLocalRecurrenceToGoogle(localEvent.recurrence) // MODIFIED
                        reminders = mapLocalRemindersToGoogle(localEvent.reminderMinutesBefore) // MODIFIED
                    }
                    calendarClient.events().update(calendarId, googleEvent.id, googleEvent).execute()
                }
            }
        }

        // Deletion Handling (Google Calendar to Local)
        val localEventsWithGoogleId = allLocalEvents.filter { it.googleCalendarEventId != null }
        val googleEventIds = googleEvents?.map { it.id }?.toSet() ?: emptySet()

        localEventsWithGoogleId.forEach { localEvent ->
            if (localEvent.googleCalendarEventId !in googleEventIds) {
                familyEventRepository.deleteEvent(localEvent)
            }
        }

        // Local deletions are handled at delete-time via DeleteGoogleCalendarEventUseCase.
    }

    private fun mapGoogleRecurrenceToLocal(googleRecurrence: List<String>?): EventRecurrence {
        val rrule = googleRecurrence?.firstOrNull() ?: return EventRecurrence.NONE
        return when {
            rrule.contains("FREQ=DAILY") -> EventRecurrence.DAILY
            rrule.contains("FREQ=WEEKLY") -> EventRecurrence.WEEKLY
            rrule.contains("FREQ=MONTHLY") -> EventRecurrence.MONTHLY
            rrule.contains("FREQ=YEARLY") -> EventRecurrence.YEARLY
            else -> EventRecurrence.NONE
        }
    }

    private fun mapGoogleRemindersToLocal(googleReminders: Event.Reminders?): Int? {
        if (googleReminders == null) return null
        if (googleReminders.useDefault == true) return 0 // Default reminder

        return googleReminders.overrides?.firstOrNull { it.method == "popup" }?.minutes
    }

    private fun mapLocalRecurrenceToGoogle(localRecurrence: EventRecurrence): List<String>? {
        return when (localRecurrence) {
            EventRecurrence.NONE -> null
            EventRecurrence.DAILY -> listOf("RRULE:FREQ=DAILY")
            EventRecurrence.WEEKLY -> listOf("RRULE:FREQ=WEEKLY")
            EventRecurrence.MONTHLY -> listOf("RRULE:FREQ=MONTHLY")
            EventRecurrence.YEARLY -> listOf("RRULE:FREQ=YEARLY")
        }
    }

    private fun mapLocalRemindersToGoogle(localReminderMinutesBefore: Int?): Event.Reminders? {
        if (localReminderMinutesBefore == null) return null

        return Event.Reminders().apply {
            useDefault = false
            overrides = listOf(
                EventReminder().apply {
                    method = "popup"
                    minutes = localReminderMinutesBefore
                }
            )
        }
    }
}
