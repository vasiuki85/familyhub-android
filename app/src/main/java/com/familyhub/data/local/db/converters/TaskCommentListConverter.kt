package com.familyhub.data.local.db.converters

import androidx.room.TypeConverter
import com.familyhub.data.local.entity.TaskCommentPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskCommentListConverter {
    private val json = Json { encodeDefaults = true }

    @TypeConverter
    fun fromStorage(value: String?): List<TaskCommentPayload> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<TaskCommentPayload>>(value)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStorage(list: List<TaskCommentPayload>?): String {
        return json.encodeToString(list ?: emptyList())
    }
}

