package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyhub.data.local.entity.FamilyEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyEventDao {
    @Query("SELECT * FROM family_events ORDER BY startDateTime ASC")
    fun getAll(): Flow<List<FamilyEventEntity>>

    @Query(
        "SELECT * FROM family_events WHERE startDateTime >= :fromDate ORDER BY startDateTime ASC LIMIT :limit"
    )
    suspend fun getUpcoming(fromDate: Long, limit: Int): List<FamilyEventEntity>

    @Query(
        "SELECT * FROM family_events WHERE startDateTime BETWEEN :start AND :end ORDER BY startDateTime ASC"
    )
    suspend fun getBetween(start: Long, end: Long): List<FamilyEventEntity>

    @Query("SELECT * FROM family_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FamilyEventEntity?

    @Query("SELECT * FROM family_events WHERE googleCalendarEventId = :googleCalendarEventId LIMIT 1")
    suspend fun getByGoogleCalendarId(googleCalendarEventId: String): FamilyEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: FamilyEventEntity)

    @Delete
    suspend fun delete(event: FamilyEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<FamilyEventEntity>)

    @Query("DELETE FROM family_events")
    suspend fun clear()
}

