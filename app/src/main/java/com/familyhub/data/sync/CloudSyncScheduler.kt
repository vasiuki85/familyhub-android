package com.familyhub.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.familyhub.data.work.CloudSyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Singleton
class CloudSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodicSync() {
        val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Debounces local Room mutations into one network-constrained upload attempt.
     * Periodic work remains the crash/reboot fallback; this path keeps active families fresh.
     */
    fun schedulePendingChangesSync() {
        val workRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInitialDelay(PENDING_CHANGES_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            PENDING_CHANGES_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(PENDING_CHANGES_WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "cloud_sync_worker"
        private const val PENDING_CHANGES_WORK_NAME = "cloud_sync_pending_changes_worker"
        private const val PENDING_CHANGES_DEBOUNCE_SECONDS = 3L
        // Чаще обновляем данные: раз в час
        private const val REPEAT_INTERVAL_HOURS = 1L
    }
}
