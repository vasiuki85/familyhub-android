package com.familyhub.data.datastore

import org.junit.Assert.assertThrows
import org.junit.Test

class SyncSettingsDeletedDocumentsContractTest {

    @Test
    fun addDeletedDocument_rejectsUnknownCollection() {
        assertThrows(IllegalArgumentException::class.java) {
            DeletedCloudDocumentCollections.requireAllowed("members")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeletedCloudDocumentCollections.requireAllowed("recipes")
        }
    }
}
