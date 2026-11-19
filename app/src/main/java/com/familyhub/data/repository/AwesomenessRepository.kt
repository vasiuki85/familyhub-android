package com.familyhub.data.repository

import com.familyhub.data.datastore.AwesomenessDataStore
import com.familyhub.domain.model.AwesomenessEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AwesomenessRepository @Inject constructor(
    private val dataStore: AwesomenessDataStore
) {
    fun getAllEntries(): Flow<List<AwesomenessEntry>> = dataStore.entriesFlow

    suspend fun addEntry(entry: AwesomenessEntry) {
        val currentEntries = dataStore.entriesFlow.first()
        dataStore.saveEntries(currentEntries + entry)
    }

    suspend fun updateEntry(entry: AwesomenessEntry) {
        val currentEntries = dataStore.entriesFlow.first()
        dataStore.saveEntries(
            currentEntries.map { if (it.id == entry.id) entry else it }
        )
    }

    suspend fun deleteEntry(entry: AwesomenessEntry) {
        val currentEntries = dataStore.entriesFlow.first()
        dataStore.saveEntries(currentEntries.filter { it.id != entry.id })
    }
}

