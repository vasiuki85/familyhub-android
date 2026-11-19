package com.familyhub.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.TaskDeadlineNotificationPolicy
import com.familyhub.data.notification.TaskNotificationManager
import com.familyhub.data.notification.TaskNotificationResult
import com.familyhub.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import com.familyhub.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class TaskDeadlineCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val notificationManager: TaskNotificationManager,
    private val appSettingsDataStore: AppSettingsDataStore,
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "TaskDeadlineCheckWorker"
        private const val PERIODIC_LOOKAHEAD_MILLIS = 15 * 60 * 1000L
    }
    
    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val windowEnd = now + PERIODIC_LOOKAHEAD_MILLIS
            val currentUserId = appSettingsDataStore.settingsFlow.first().currentUserId
            if (currentUserId.isBlank()) {
                Log.d(TAG, "Пропуск проверки дедлайнов: currentUserId не задан")
                return Result.success()
            }

            val tasks = taskRepository.getAllTasks().first()
            
            var notificationCount = 0
            var failedCount = 0
            
            tasks.forEach { task ->
                if (TaskDeadlineNotificationPolicy.shouldNotify(task, currentUserId, now, windowEnd)) {
                    val dueDate = task.dueDate!!
                    val timeUntil = getTimeUntilDeadline(dueDate, now)
                    when (val result = notificationManager.showTaskDeadlineNotification(
                        task.title,
                        task.id,
                        timeUntil
                    )) {
                        TaskNotificationResult.SHOWN -> {
                            notificationCount++
                            Log.d(TAG, "Уведомление отправлено для задачи: ${task.title}")
                        }
                        TaskNotificationResult.PERMISSION_REQUIRED -> {
                            failedCount++
                            Log.w(TAG, "Не удалось отправить уведомление для задачи '${task.title}': требуется разрешение")
                        }
                        TaskNotificationResult.NOTIFICATIONS_DISABLED -> {
                            failedCount++
                            Log.w(TAG, "Не удалось отправить уведомление для задачи '${task.title}': уведомления отключены")
                        }
                    }
                }
            }
            
            Log.d(TAG, "Проверка дедлайнов завершена. Отправлено: $notificationCount, не удалось: $failedCount")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке дедлайнов задач", e)
            Result.retry()
        }
    }
    
    private fun getTimeUntilDeadline(dueDate: Long, now: Long): String {
        val diff = dueDate - now
        val hours = diff / java.util.concurrent.TimeUnit.HOURS.toMillis(1)
        
        return when {
            hours < 1 -> context.getString(R.string.task_deadline_less_than_hour)
            hours < 24 -> context.getString(R.string.task_deadline_hours, hours.toInt())
            else -> {
                val dateFormat = SimpleDateFormat("dd.MM в HH:mm", Locale.getDefault())
                dateFormat.format(dueDate)
            }
        }
    }
}
