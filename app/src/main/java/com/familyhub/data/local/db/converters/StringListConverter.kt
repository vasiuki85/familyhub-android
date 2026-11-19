package com.familyhub.data.local.db.converters

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StringListConverter {
    private val json = Json { encodeDefaults = true }

    @TypeConverter
    fun fromStorage(value: String?): List<String> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStorage(list: List<String>?): String {
        return json.encodeToString(list ?: emptyList())
    }
}
