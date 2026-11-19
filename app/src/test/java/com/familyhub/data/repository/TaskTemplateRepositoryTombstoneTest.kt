package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.entity.TaskTemplateEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.SyncSettings
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.model.TaskType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaskTemplateRepositoryTombstoneTest {

    private val dao = mockk<TaskTemplateDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: TaskTemplateRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(familySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.delete(any<TaskTemplateEntity>()) } just Runs
        coEvery { dao.deleteById(any()) } just Runs
        coEvery { dao.upsert(any<TaskTemplateEntity>()) } just Runs
        repository = TaskTemplateRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsTombstoneBeforeDaoDelete() = runTest {
        repository.delete(sampleTemplate())

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.delete(any<TaskTemplateEntity>())
        }
    }

    @Test
    fun deleteById_recordsTombstoneBeforeDaoDelete() = runTest {
        repository.deleteById("template-1")

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(any())
            dao.deleteById("template-1")
        }
    }

    @Test
    fun upsert_clearsTombstoneAfterDaoUpsert() = runTest {
        repository.upsert(sampleTemplate())

        coVerifyOrder {
            dao.upsert(any<TaskTemplateEntity>())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
    }

    private fun sampleTemplate() = TaskTemplate(
        id = "template-1",
        title = "Trash",
        description = null,
        repeatRule = RepeatRule.WEEKLY,
        defaultAssigneeId = null,
        isPrivate = false,
        taskType = TaskType.ROUTINE,
        createdByUserId = "user-1",
        createdAt = 1L,
        categoryId = null,
        priority = TaskPriority.MEDIUM
    )

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
