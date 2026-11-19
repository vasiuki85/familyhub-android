package com.familyhub.data.local.db.converters

import androidx.room.TypeConverter
import com.familyhub.data.local.entity.TaskAttachmentPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskAttachmentListConverter {
    private val json = Json { encodeDefaults = true }

    @TypeConverter
    fun fromStorage(value: String?): List<TaskAttachmentPayload> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<TaskAttachmentPayload>>(value)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStorage(list: List<TaskAttachmentPayload>?): String {
        return json.encodeToString(list ?: emptyList())
    }
}
