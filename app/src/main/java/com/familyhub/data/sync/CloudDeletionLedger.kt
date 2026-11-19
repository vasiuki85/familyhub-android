package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.SyncSettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class CloudDeletionLedger @Inject constructor(
    private val syncSettingsDataStore: SyncSettingsDataStore
) : CloudDeletionLedgerPort {
    private suspend fun currentFamilyId(): String? {
        return syncSettingsDataStore.settingsFlow.first().accountId?.takeUnless { it.isBlank() }
    }

    suspend fun recordDeletion(collection: String, documentId: String) {
        recordDeletions(listOf(collection to documentId))
    }

    suspend fun recordDeletions(collection: String, documentIds: Collection<String>) {
        if (documentIds.isEmpty()) return
        recordDeletions(documentIds.map { collection to it })
    }

    override suspend fun recordDeletions(entries: Collection<Pair<String, String>>) {
        if (entries.isEmpty()) return
        val familyId = currentFamilyId() ?: return
        val documents = entries.map { (collection, documentId) ->
            DeletedCloudDocumentCollections.requireAllowed(collection)
            DeletedCloudDocument(
                familyId = familyId,
                collection = collection,
                documentId = documentId
            )
        }
        syncSettingsDataStore.addDeletedDocuments(documents)
    }

    suspend fun clearDeletion(collection: String, documentId: String) {
        clearDeletions(listOf(collection to documentId))
    }

    suspend fun clearDeletions(collection: String, documentIds: Collection<String>) {
        if (documentIds.isEmpty()) return
        clearDeletions(documentIds.map { collection to it })
    }

    override suspend fun clearDeletions(entries: Collection<Pair<String, String>>) {
        if (entries.isEmpty()) return
        val familyId = currentFamilyId() ?: return
        val documents = entries.map { (collection, documentId) ->
            DeletedCloudDocumentCollections.requireAllowed(collection)
            DeletedCloudDocument(
                familyId = familyId,
                collection = collection,
                documentId = documentId
            )
        }
        syncSettingsDataStore.removeDeletedDocuments(documents)
    }
}
