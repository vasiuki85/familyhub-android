package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Appreciation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppreciationRepository @Inject constructor(
    private val dao: AppreciationDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAll(): Flow<List<Appreciation>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun add(appreciation: Appreciation) {
        dao.upsert(appreciation.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.APPRECIATIONS, appreciation.id)
    }

    suspend fun delete(appreciation: Appreciation) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.APPRECIATIONS, appreciation.id)
        dao.deleteById(appreciation.id)
    }

    suspend fun clear() {
        dao.clear()
    }
}
