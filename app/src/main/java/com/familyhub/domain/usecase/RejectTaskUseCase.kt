package com.familyhub.domain.usecase

import com.familyhub.data.repository.TaskCompletionTransitionCoordinator
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskTransitionResult
import javax.inject.Inject

class RejectTaskUseCase @Inject constructor(
    private val coordinator: TaskCompletionTransitionCoordinator
) {
    suspend operator fun invoke(
        task: Task,
        actorUserId: String
    ): TaskTransitionResult = coordinator.cancelWaiting(
        taskId = task.id,
        actorUserId = actorUserId
    )
}
