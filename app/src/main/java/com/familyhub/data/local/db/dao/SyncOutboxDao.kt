package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.familyhub.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Query("SELECT MAX(sequence) FROM sync_outbox")
    suspend fun maxSequence(): Long?

    @Query("SELECT * FROM sync_outbox WHERE sequence <= :maxSequence ORDER BY sequence")
    suspend fun entriesThrough(maxSequence: Long): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE sequence <= :maxSequence")
    suspend fun deleteThrough(maxSequence: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox LIMIT 1)")
    suspend fun hasEntries(): Boolean

    @Query("UPDATE sync_tracking_control SET ignoreDepth = ignoreDepth + 1 WHERE id = 1")
    suspend fun beginTrackingSuppression()

    @Query(
        "UPDATE sync_tracking_control " +
            "SET ignoreDepth = ignoreDepth - 1 WHERE id = 1 AND ignoreDepth > 0"
    )
    suspend fun endTrackingSuppression()

    @Query("DELETE FROM sync_outbox WHERE collectionKey IN (:keys)")
    suspend fun deleteByCollectionKeys(keys: Set<String>)

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
