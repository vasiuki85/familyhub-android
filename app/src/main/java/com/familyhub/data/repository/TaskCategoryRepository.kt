package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.TaskCategory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskCategoryRepository @Inject constructor(
    private val dao: TaskCategoryDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllCategories(): Flow<List<TaskCategory>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun upsert(category: TaskCategory) {
        dao.upsert(category.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.TASK_CATEGORIES, category.id)
    }

    suspend fun upsertAll(categories: List<TaskCategory>) {
        dao.upsertAll(categories.map { it.toEntity() })
        cloudDeletionLedger.clearDeletions(
            DeletedCloudDocumentCollections.TASK_CATEGORIES,
            categories.map { it.id }
        )
    }

    suspend fun delete(category: TaskCategory) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.TASK_CATEGORIES, category.id)
        dao.delete(category.toEntity())
    }

    suspend fun getById(id: String): TaskCategory? {
        return dao.getById(id)?.toDomain()
    }
}
