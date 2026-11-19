package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.TaskTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskTemplateRepository @Inject constructor(
    private val dao: TaskTemplateDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllTemplates(): Flow<List<TaskTemplate>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun upsert(template: TaskTemplate) {
        dao.upsert(template.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.TASK_TEMPLATES, template.id)
    }

    suspend fun upsertAll(templates: List<TaskTemplate>) {
        dao.upsertAll(templates.map { it.toEntity() })
        cloudDeletionLedger.clearDeletions(
            DeletedCloudDocumentCollections.TASK_TEMPLATES,
            templates.map { it.id }
        )
    }

    suspend fun delete(template: TaskTemplate) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.TASK_TEMPLATES, template.id)
        dao.delete(template.toEntity())
    }

    suspend fun deleteById(id: String) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.TASK_TEMPLATES, id)
        dao.deleteById(id)
    }
}
