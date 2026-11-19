package com.familyhub.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyhub.data.notification.EventNotificationManager
import com.familyhub.data.notification.EventNotificationResult
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.repository.FamilyEventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: FamilyEventRepository,
    private val notificationManager: EventNotificationManager,
    private val reminderScheduler: EventReminderScheduler
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "EventReminderWorker"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_OCCURRENCE_START = "occurrence_start"
    }

    override suspend fun doWork(): Result {
        return try {
            val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
            val occurrenceStart = inputData.getLong(KEY_OCCURRENCE_START, -1)
            if (occurrenceStart <= 0) {
                return Result.failure()
            }

            val event = eventRepository.getEventById(eventId) ?: return Result.success()

            when (val result = notificationManager.showEventReminder(event, occurrenceStart)) {
                EventNotificationResult.SHOWN -> {
                    Log.d(TAG, "Уведомление о событии отправлено: ${event.title}")
                }
                EventNotificationResult.PERMISSION_REQUIRED -> {
                    Log.w(TAG, "Не удалось отправить уведомление о событии '${event.title}': требуется разрешение")
                }
                EventNotificationResult.NOTIFICATIONS_DISABLED -> {
                    Log.w(TAG, "Не удалось отправить уведомление о событии '${event.title}': уведомления отключены")
                }
            }

            reminderScheduler.scheduleNextAfter(event, occurrenceStart)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке напоминания о событии", e)
            Result.retry()
        }
    }
    
}
