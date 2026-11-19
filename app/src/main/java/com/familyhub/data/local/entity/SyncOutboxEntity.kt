package com.familyhub.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["collectionKey", "entityId"])],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val sequence: Long = 0L,
    val collectionKey: String,
    val entityId: String,
    val operation: String,
)

@Entity(tableName = "sync_tracking_control")
data class SyncTrackingControlEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0")
    val ignoreDepth: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
