package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.FamilyEventDao
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.FamilyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FamilyEventRepository @Inject constructor(
    private val dao: FamilyEventDao,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    fun getAllEvents(): Flow<List<FamilyEvent>> {
        return dao.getAll().map { it.map { entity -> entity.toDomain() } }
    }

    suspend fun getUpcomingEvents(fromDate: Long, limit: Int): List<FamilyEvent> {
        return dao.getUpcoming(fromDate, limit).map { it.toDomain() }
    }

    suspend fun getEventById(id: String): FamilyEvent? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun getEventByGoogleCalendarId(googleCalendarEventId: String): FamilyEvent? {
        return dao.getByGoogleCalendarId(googleCalendarEventId)?.toDomain()
    }

    suspend fun getEventsBetween(start: Long, end: Long): List<FamilyEvent> {
        return dao.getBetween(start, end).map { it.toDomain() }
    }

    suspend fun upsertEvent(event: FamilyEvent) {
        dao.upsert(event.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.EVENTS, event.id)
    }

    suspend fun deleteEvent(event: FamilyEvent) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.EVENTS, event.id)
        dao.delete(event.toEntity())
    }
}
