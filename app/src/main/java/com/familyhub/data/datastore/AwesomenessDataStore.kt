package com.familyhub.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyhub.domain.model.AwesomenessEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.awesomenessDataStore: DataStore<Preferences> by preferencesDataStore(name = "awesomeness")

class AwesomenessDataStore(private val context: Context) {
    companion object {
        private val KEY_ENTRIES = stringPreferencesKey("entries")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val entriesFlow: Flow<List<AwesomenessEntry>> =
        context.awesomenessDataStore.data.map { prefs ->
            val entriesJson = prefs[KEY_ENTRIES] ?: "[]"
            try {
                json.decodeFromString<List<AwesomenessEntry>>(entriesJson)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveEntries(entries: List<AwesomenessEntry>) {
        context.awesomenessDataStore.edit { prefs ->
            val entriesJson = json.encodeToString(entries)
            prefs[KEY_ENTRIES] = entriesJson
        }
    }
}

