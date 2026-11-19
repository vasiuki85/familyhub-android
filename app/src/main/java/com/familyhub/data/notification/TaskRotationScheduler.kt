package com.familyhub.data.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.familyhub.data.work.TaskRotationWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRotationScheduler @Inject constructor(
    context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        private const val WORK_NAME = "task_rotation_checker"
        private const val REPEAT_INTERVAL_HOURS = 12L
    }

    fun schedulePeriodicRotationCheck() {
        val workRequest = PeriodicWorkRequestBuilder<TaskRotationWorker>(
            REPEAT_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
