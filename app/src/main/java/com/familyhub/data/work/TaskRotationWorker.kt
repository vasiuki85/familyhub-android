package com.familyhub.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyhub.domain.usecase.RotateTasksUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@HiltWorker
class TaskRotationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rotateTasksUseCase: RotateTasksUseCase
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltEntryPoint {
        fun rotateTasksUseCase(): RotateTasksUseCase
    }

    // Fallback constructor for cases when HiltWorkerFactory is not used
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        EntryPointAccessors.fromApplication(context, HiltEntryPoint::class.java).rotateTasksUseCase()
    )
    override suspend fun doWork(): Result {
        return try {
            rotateTasksUseCase()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
