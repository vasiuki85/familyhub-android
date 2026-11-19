package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.domain.model.SyncSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CloudDeletionLedgerTest {

    @Test
    fun recordDeletion_recordsWhenSyncPausedAndFamilyPresent() = runTest {
        val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(
            SyncSettings(
                isEnabled = false,
                accountId = "family-1",
                deviceId = "device-1",
                lastSyncAt = 1L,
                hasPendingChanges = false,
                lastError = null,
                googleCalendarId = null
            )
        )
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        val ledger = CloudDeletionLedger(syncSettingsDataStore)

        ledger.recordDeletion(DeletedCloudDocumentCollections.TASKS, "task-1")

        coVerify(exactly = 1) {
            syncSettingsDataStore.addDeletedDocuments(
                match { documents ->
                    documents.single().let {
                        it.familyId == "family-1" &&
                            it.collection == DeletedCloudDocumentCollections.TASKS &&
                            it.documentId == "task-1"
                    }
                }
            )
        }
    }

    @Test
    fun recordDeletion_skipsWhenFamilyMissing() = runTest {
        val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(
            SyncSettings(
                isEnabled = true,
                accountId = null,
                deviceId = "device-1",
                lastSyncAt = null,
                hasPendingChanges = false,
                lastError = null,
                googleCalendarId = null
            )
        )
        val ledger = CloudDeletionLedger(syncSettingsDataStore)

        ledger.recordDeletion(DeletedCloudDocumentCollections.TASKS, "task-1")

        coVerify(exactly = 0) {
            syncSettingsDataStore.addDeletedDocuments(any())
        }
    }

    @Test
    fun recordDeletions_writesAllEntriesInSingleBatch() = runTest {
        val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(
            SyncSettings(
                isEnabled = true,
                accountId = "family-1",
                deviceId = "device-1",
                lastSyncAt = null,
                hasPendingChanges = false,
                lastError = null,
                googleCalendarId = null
            )
        )
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        val ledger = CloudDeletionLedger(syncSettingsDataStore)

        ledger.recordDeletions(
            listOf(
                DeletedCloudDocumentCollections.TASKS to "task-1",
                DeletedCloudDocumentCollections.EVENTS to "event-1"
            )
        )

        coVerify(exactly = 1) {
            syncSettingsDataStore.addDeletedDocuments(
                match { documents ->
                    documents.map { it.collection to it.documentId }.toSet() ==
                        setOf(
                            DeletedCloudDocumentCollections.TASKS to "task-1",
                            DeletedCloudDocumentCollections.EVENTS to "event-1"
                        )
                }
            )
        }
    }
}
