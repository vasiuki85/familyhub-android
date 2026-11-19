package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.entity.EmergencyContactEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.EmergencyContact
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

class EmergencyContactRepositoryTombstoneTest {

    private val dao = mockk<EmergencyContactDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: EmergencyContactRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(familySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.delete(any<EmergencyContactEntity>()) } just Runs
        coEvery { dao.upsert(any<EmergencyContactEntity>()) } just Runs
        repository = EmergencyContactRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsTombstoneBeforeDaoDelete() = runTest {
        repository.delete(EmergencyContact("contact-1", "Mom", "+123", null, 1L))

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.delete(any<EmergencyContactEntity>())
        }
    }

    @Test
    fun upsert_clearsTombstoneAfterDaoUpsert() = runTest {
        repository.upsert(EmergencyContact("contact-1", "Mom", "+123", null, 1L))

        coVerifyOrder {
            dao.upsert(any<EmergencyContactEntity>())
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
