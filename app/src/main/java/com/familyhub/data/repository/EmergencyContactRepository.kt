package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.EmergencyContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyContactRepository @Inject constructor(
    private val dao: EmergencyContactDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllContacts(): Flow<List<EmergencyContact>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun upsert(contact: EmergencyContact) {
        dao.upsert(contact.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.EMERGENCY_CONTACTS, contact.id)
    }

    suspend fun delete(contact: EmergencyContact) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.EMERGENCY_CONTACTS, contact.id)
        dao.delete(contact.toEntity())
    }
}
