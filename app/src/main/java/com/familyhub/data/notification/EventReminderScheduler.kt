package com.familyhub.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.familyhub.data.work.EventReminderWorker
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.domain.utils.getNextOccurrence
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleReminder(event: FamilyEvent) {
        cancelReminder(event.id)

        val reminderMinutes = event.reminderMinutesBefore ?: return
        val now = System.currentTimeMillis()

        var nextOccurrence = event.getNextOccurrence(now)
        while (nextOccurrence != null) {
            val triggerAt = nextOccurrence - TimeUnit.MINUTES.toMillis(reminderMinutes.toLong())
            if (triggerAt > now) {
                enqueue(event, nextOccurrence, triggerAt - now)
                return
            }
            nextOccurrence = event.getNextOccurrence(nextOccurrence + 1)
        }
    }

    fun scheduleNextAfter(event: FamilyEvent, lastOccurrenceStart: Long) {
        val reference = lastOccurrenceStart + 1
        val reminderMinutes = event.reminderMinutesBefore ?: return

        var nextOccurrence = event.getNextOccurrence(reference)
        while (nextOccurrence != null) {
            val triggerAt = nextOccurrence - TimeUnit.MINUTES.toMillis(reminderMinutes.toLong())
            val now = System.currentTimeMillis()
            if (triggerAt > now) {
                enqueue(event, nextOccurrence, triggerAt - now)
                return
            }
            nextOccurrence = event.getNextOccurrence(nextOccurrence + 1)
        }
    }

    fun cancelReminder(eventId: String) {
        workManager.cancelUniqueWork(workName(eventId))
    }

    private fun enqueue(event: FamilyEvent, occurrenceStart: Long, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(workName(event.id))
            .setInputData(
                workDataOf(
                    EventReminderWorker.KEY_EVENT_ID to event.id,
                    EventReminderWorker.KEY_OCCURRENCE_START to occurrenceStart
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            workName(event.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(eventId: String): String = "event_reminder_$eventId"
}
