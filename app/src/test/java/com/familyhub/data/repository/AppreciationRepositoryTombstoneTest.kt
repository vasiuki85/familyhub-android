package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.entity.AppreciationEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Appreciation
import com.familyhub.domain.model.SyncSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppreciationRepositoryTombstoneTest {

    private val dao = mockk<AppreciationDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: AppreciationRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(familySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.deleteById(any()) } just Runs
        coEvery { dao.upsert(any<AppreciationEntity>()) } just Runs
        repository = AppreciationRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsTombstoneBeforeDaoDelete() = runTest {
        repository.delete(Appreciation("app-1", "sender", "recipient", "Thanks", 1L))

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.deleteById("app-1")
        }
    }

    @Test
    fun add_clearsTombstoneAfterDaoUpsert() = runTest {
        repository.add(Appreciation("app-1", "sender", "recipient", "Thanks", 1L))

        coVerifyOrder {
            dao.upsert(any<AppreciationEntity>())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
    }

    private fun familySettings() = SyncSettings(
        isEnabled = true,
        accountId = "family-1",
        deviceId = "device-1",
        lastSyncAt = null,
        hasPendingChanges = false,
        lastError = null,
        googleCalendarId = null
    )
}
