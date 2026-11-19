package com.familyhub.domain.model

data class TaskTransitionResult(
    val taskId: String,
    val previousStatus: TaskStatus,
    val newStatus: TaskStatus,
    val scoreImpact: ScoreImpact,
    val mutated: Boolean,
    val repeatCreated: Boolean
) {
    companion object {
        fun noOp(
            taskId: String,
            status: TaskStatus
        ): TaskTransitionResult = TaskTransitionResult(
            taskId = taskId,
            previousStatus = status,
            newStatus = status,
            scoreImpact = ScoreImpact(0, 0, 0, 0, emptyList()),
            mutated = false,
            repeatCreated = false
        )
    }
}
