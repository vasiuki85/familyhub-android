package com.familyhub.domain.usecase

import com.familyhub.data.repository.TaskHistoryRepository
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskHistory
import com.familyhub.domain.model.TaskHistoryChangeType
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.TimeUtils
import javax.inject.Inject

/**
 * Use Case для записи истории изменений задач
 */
class RecordTaskHistoryUseCase @Inject constructor(
    private val historyRepository: TaskHistoryRepository,
    private val idGenerator: IdGenerator,
    private val textProvider: TaskHistoryTextProvider
) {
    suspend fun recordCreated(task: Task, createdByUserId: String) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = createdByUserId,
            changeType = TaskHistoryChangeType.CREATED,
            changeDescription = textProvider.taskCreated(task.title),
            oldValue = null,
            newValue = task.title,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
    
    suspend fun recordUpdated(
        task: Task,
        oldTask: Task?,
        changedByUserId: String,
        changes: List<String>
    ) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = changedByUserId,
            changeType = TaskHistoryChangeType.UPDATED,
            changeDescription = textProvider.taskUpdated(changes),
            oldValue = oldTask?.title,
            newValue = task.title,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
    
    suspend fun recordStatusChanged(
        task: Task,
        oldStatus: String,
        newStatus: String,
        changedByUserId: String
    ) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = changedByUserId,
            changeType = TaskHistoryChangeType.STATUS_CHANGED,
            changeDescription = textProvider.statusChanged(oldStatus, newStatus),
            oldValue = oldStatus,
            newValue = newStatus,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
    
    suspend fun recordDeleted(task: Task, deletedByUserId: String) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = deletedByUserId,
            changeType = TaskHistoryChangeType.DELETED,
            changeDescription = textProvider.taskDeleted(task.title),
            oldValue = task.title,
            newValue = null,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
    
    suspend fun recordAssigned(
        task: Task,
        oldAssigneeId: String?,
        newAssigneeId: String,
        changedByUserId: String
    ) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = changedByUserId,
            changeType = TaskHistoryChangeType.ASSIGNED,
            changeDescription = textProvider.taskReassigned(),
            oldValue = oldAssigneeId,
            newValue = newAssigneeId,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
    
    suspend fun recordPrivacyChanged(
        task: Task,
        oldPrivate: Boolean,
        newPrivate: Boolean,
        changedByUserId: String
    ) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = changedByUserId,
            changeType = TaskHistoryChangeType.PRIVACY_CHANGED,
            changeDescription = textProvider.privacyChanged(newPrivate),
            oldValue = oldPrivate.toString(),
            newValue = newPrivate.toString(),
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }

    suspend fun recordRotation(
        task: Task,
        previousAssigneeId: String?,
        newAssigneeId: String,
        previousName: String?,
        newName: String
    ) {
        val history = TaskHistory(
            id = idGenerator.newId(),
            taskId = task.id,
            changedByUserId = previousAssigneeId ?: task.createdByUserId,
            changeType = TaskHistoryChangeType.ROTATED,
            changeDescription = textProvider.rotationChanged(previousName, newName),
            oldValue = previousAssigneeId,
            newValue = newAssigneeId,
            timestamp = TimeUtils.now()
        )
        historyRepository.insertHistory(history)
    }
}

