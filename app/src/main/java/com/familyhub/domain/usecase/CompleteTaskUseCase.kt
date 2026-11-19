package com.familyhub.domain.usecase

import com.familyhub.data.repository.TaskCompletionTransitionCoordinator
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskTransitionResult
import com.familyhub.util.TimeUtils
import javax.inject.Inject

class CompleteTaskUseCase @Inject constructor(
    private val coordinator: TaskCompletionTransitionCoordinator
) {
    suspend operator fun invoke(
        task: Task,
        actorUserId: String,
        completionTime: Long = TimeUtils.now()
    ): TaskTransitionResult = coordinator.complete(
        taskId = task.id,
        actorUserId = actorUserId,
        completionTime = completionTime
    )
}
