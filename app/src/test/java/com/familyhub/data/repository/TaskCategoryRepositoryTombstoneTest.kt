package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.entity.TaskCategoryEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.SyncSettings
import com.familyhub.domain.model.TaskCategory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaskCategoryRepositoryTombstoneTest {

    private val dao = mockk<TaskCategoryDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: TaskCategoryRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(familySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.delete(any<TaskCategoryEntity>()) } just Runs
        coEvery { dao.upsert(any<TaskCategoryEntity>()) } just Runs
        repository = TaskCategoryRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsTombstoneBeforeDaoDelete() = runTest {
        repository.delete(TaskCategory("cat-1", "Home", 1L, 1L, 1L))

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.delete(any<TaskCategoryEntity>())
        }
    }

    @Test
    fun upsert_clearsTombstoneAfterDaoUpsert() = runTest {
        repository.upsert(TaskCategory("cat-1", "Home", 1L, 1L, 1L))

        coVerifyOrder {
            dao.upsert(any<TaskCategoryEntity>())
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
