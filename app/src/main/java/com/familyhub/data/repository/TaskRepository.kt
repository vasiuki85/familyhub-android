package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderConflictException
import com.familyhub.domain.model.TaskOrderSnapshotEntry
import com.familyhub.domain.model.TaskOrderTransactionGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val cloudDeletionLedger: CloudDeletionLedger,
    private val database: FamilyHubDatabase,
) {
    private fun mapToDomain(entities: List<com.familyhub.data.local.entity.TaskEntity>): List<Task> {
        return entities.map { it.toDomain() }
    }

    fun getAllTasks(): Flow<List<Task>> {
        return dao.getAll().map { mapToDomain(it) }
    }

    fun getTasksForUser(userId: String): Flow<List<Task>> {
        return dao.getByAssignee(userId).map { mapToDomain(it) }
    }

    suspend fun getTaskById(id: String): Task? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun getAllTasksSync(): List<Task> {
        return dao.getAllSync().map { it.toDomain() }
    }

    suspend fun upsertTask(task: Task) {
        timber.log.Timber.d("TaskRepository.upsertTask: Saving task id=%s, title=%s", task.id, task.title)
        dao.upsert(task.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.TASKS, task.id)
        timber.log.Timber.d("TaskRepository.upsertTask: Task saved successfully id=%s", task.id)
    }

    suspend fun upsertTasks(tasks: List<Task>) {
        dao.upsertAll(tasks.map { it.toEntity() })
        cloudDeletionLedger.clearDeletions(
            DeletedCloudDocumentCollections.TASKS,
            tasks.map { it.id }
        )
    }

    suspend fun deleteTask(task: Task) {
        cloudDeletionLedger.recordDeletions(
            listOf(
                DeletedCloudDocumentCollections.TASKS to task.id,
                DeletedCloudDocumentCollections.TASK_ROTATIONS to task.id
            )
        )
        dao.delete(task.toEntity())
    }

    /**
     * Order-only persistence:
     * 1) validate plan (unique ids, snapshot alignment, unique projected orderIndex)
     * 2) re-read authoritative visible cohort inside one Room transaction
     * 3) refuse if add/delete/visibility/orderIndex drifted
     * 4) CAS-update each change; every statement must affect exactly 1 row
     *
     * Does not upsert payloads and does not touch CloudDeletionLedger.
     */
    suspend fun applyManualOrderChanges(
        expectedSnapshot: List<TaskOrderSnapshotEntry>,
        changes: List<TaskOrderChange>,
        currentUserId: String,
    ) {
        if (changes.isEmpty()) return
        TaskOrderTransactionGuard.validatePlan(expectedSnapshot, changes)

        database.withTransaction {
            val currentRows = dao.getVisibleOrderIndices(currentUserId)
            val currentSnapshot = currentRows.map {
                TaskOrderSnapshotEntry(taskId = it.id, orderIndex = it.orderIndex)
            }
            TaskOrderTransactionGuard.ensureSnapshotMatches(expectedSnapshot, currentSnapshot)

            for (change in changes) {
                val affected = dao.updateOrderIndexCas(
                    taskId = change.taskId,
                    expectedOrderIndex = change.expectedOrderIndex,
                    newOrderIndex = change.newOrderIndex,
                )
                if (affected != 1) {
                    throw TaskOrderConflictException(
                        "CAS order update failed for ${change.taskId}: affected=$affected"
                    )
                }
            }
        }
    }
}
