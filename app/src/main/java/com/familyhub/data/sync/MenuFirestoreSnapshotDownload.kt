package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.menu.cloud.MenuCloudSnapshot
import com.familyhub.data.menu.firestore.MenuFirestoreDocumentCodec
import com.familyhub.data.menu.firestore.MenuFirestoreRawDocument
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

internal val MENU_FIRESTORE_COLLECTIONS: List<String> = listOf(
    DeletedCloudDocumentCollections.WEEKLY_MENUS,
    DeletedCloudDocumentCollections.MENU_ENTRIES,
    DeletedCloudDocumentCollections.MENU_TEMPLATES,
    DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES,
    DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS,
)

/**
 * Strict SERVER-only menu download. One malformed document rejects the entire snapshot.
 */
internal suspend fun downloadMenuCollections(
    familyDocRef: DocumentReference,
    source: Source,
): MenuCloudSnapshot {
    require(source == Source.SERVER) { "Menu collections must be downloaded from Source.SERVER" }
    val rawDocuments = buildList {
        MENU_FIRESTORE_COLLECTIONS.forEach { collection ->
            val snapshot = familyDocRef.collection(collection).get(source).await()
            snapshot.documents.forEach { document ->
                add(
                    MenuFirestoreRawDocument(
                        collection = collection,
                        documentId = document.id,
                        fields = document.data ?: emptyMap(),
                    ),
                )
            }
        }
    }
    return MenuFirestoreDocumentCodec.decodeSnapshot(rawDocuments)
}
