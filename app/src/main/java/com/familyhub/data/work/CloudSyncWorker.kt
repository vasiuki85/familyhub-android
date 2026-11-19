package com.familyhub.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyhub.data.restore.RestoreBlockedException
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val performSyncNowUseCase: PerformSyncNowUseCase
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = performSyncNowUseCase()
        return if (result.isSuccess) {
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            if (error is RestoreBlockedException) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
