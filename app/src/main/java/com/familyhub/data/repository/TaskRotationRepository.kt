package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.TaskRotationDao
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.TaskRotationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRotationRepository @Inject constructor(
    private val dao: TaskRotationDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllRotations(): Flow<List<TaskRotationConfig>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getRotationForTask(taskId: String): TaskRotationConfig? {
        return dao.getByTaskId(taskId)?.toDomain()
    }

    suspend fun upsertRotation(config: TaskRotationConfig) {
        dao.upsert(config.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.TASK_ROTATIONS, config.taskId)
    }

    suspend fun deleteRotation(taskId: String) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.TASK_ROTATIONS, taskId)
        dao.delete(taskId)
    }

    suspend fun getDueRotations(referenceTime: Long): List<TaskRotationConfig> {
        return dao.getDue(referenceTime).map { it.toDomain() }
    }
}
