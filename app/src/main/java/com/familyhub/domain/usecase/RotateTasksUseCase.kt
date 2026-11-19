package com.familyhub.domain.usecase

import android.util.Log
import com.familyhub.data.notification.TaskNotificationManager
import com.familyhub.data.notification.TaskNotificationResult
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.domain.model.TaskRotationConfig
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.utils.TaskRotationUtils
import com.familyhub.util.TimeUtils
import javax.inject.Inject

class RotateTasksUseCase @Inject constructor(
    private val taskRotationRepository: TaskRotationRepository,
    private val taskRepository: TaskRepository,
    private val memberRepository: FamilyMemberRepository,
    private val recordTaskHistoryUseCase: RecordTaskHistoryUseCase,
    private val taskNotificationManager: TaskNotificationManager
) {
    companion object {
        private const val TAG = "RotateTasksUseCase"
    }
    suspend operator fun invoke(now: Long = TimeUtils.now()) {
        val dueRotations = taskRotationRepository.getDueRotations(now)
        if (dueRotations.isEmpty()) {
            return
        }

        for (rotation in dueRotations) {
            handleRotation(rotation, now)
        }
    }

    private suspend fun handleRotation(rotation: TaskRotationConfig, now: Long) {
        if (rotation.memberIds.size < 2) {
            // Не имеет смысла вращать между одним участником
            return
        }

        val task = taskRepository.getTaskById(rotation.taskId) ?: return

        val currentIndex = rotation.memberIds.indexOf(task.assignedToUserId).takeIf { it >= 0 }
            ?: rotation.currentIndex.coerceIn(0, rotation.memberIds.lastIndex)
        val nextIndex = (currentIndex + 1) % rotation.memberIds.size
        val nextAssigneeId = rotation.memberIds[nextIndex]

        val updatedTask = task.copy(
            assignedToUserId = nextAssigneeId,
            status = if (task.status == TaskStatus.DONE) TaskStatus.TODO else task.status
        )
        taskRepository.upsertTask(updatedTask)

        val previousAssigneeId = task.assignedToUserId
        val previousName = previousAssigneeId.let { memberRepository.getMemberById(it)?.displayName }
        val nextName = memberRepository.getMemberById(nextAssigneeId)?.displayName ?: nextAssigneeId

        recordTaskHistoryUseCase.recordRotation(
            task = updatedTask,
            previousAssigneeId = previousAssigneeId,
            newAssigneeId = nextAssigneeId,
            previousName = previousName,
            newName = nextName
        )

        val nextRotationAt = TaskRotationUtils.advanceAfterTrigger(rotation.nextRotationAt, rotation.frequency, now)
        val updatedRotation = rotation.copy(
            currentIndex = nextIndex,
            lastRotationAt = now,
            nextRotationAt = nextRotationAt
        )
        taskRotationRepository.upsertRotation(updatedRotation)

        when (val result = taskNotificationManager.showTaskRotationNotification(updatedTask.title, nextName)) {
            TaskNotificationResult.SHOWN -> {
                Log.d(TAG, "Уведомление о ротации отправлено для задачи: ${updatedTask.title}")
            }
            TaskNotificationResult.PERMISSION_REQUIRED -> {
                Log.w(TAG, "Не удалось отправить уведомление о ротации для задачи '${updatedTask.title}': требуется разрешение")
            }
            TaskNotificationResult.NOTIFICATIONS_DISABLED -> {
                Log.w(TAG, "Не удалось отправить уведомление о ротации для задачи '${updatedTask.title}': уведомления отключены")
            }
        }
    }
}
