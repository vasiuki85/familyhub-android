package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.RepeatRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaskRepositoryTombstoneTest {

    private val dao = mockk<TaskDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(
            com.familyhub.domain.model.SyncSettings(
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
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        repository = TaskRepository(dao, CloudDeletionLedger(syncSettingsDataStore), mockk(relaxed = true))
    }

    private fun sampleTask(id: String = "task-1", title: String = "Wash") = Task(
        id = id,
        title = title,
        description = null,
        createdByUserId = "user-1",
        assignedToUserId = "user-1",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 1L
    )

    @Test
    fun deleteTask_recordsTaskAndCascadedRotationTombstonesInOneBatchBeforeDaoDelete() = runTest {
        val task = sampleTask()
        coEvery { dao.delete(any<TaskEntity>()) } just Runs

        repository.deleteTask(task)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(
                match<Collection<DeletedCloudDocument>> { documents ->
                    documents.toSet() == setOf(
                        DeletedCloudDocument(
                            "family-1",
                            DeletedCloudDocumentCollections.TASKS,
                            "task-1"
                        ),
                        DeletedCloudDocument(
                            "family-1",
                            DeletedCloudDocumentCollections.TASK_ROTATIONS,
                            "task-1"
                        )
                    )
                }
            )
            dao.delete(any<TaskEntity>())
        }
        coVerify(exactly = 1) {
            syncSettingsDataStore.addDeletedDocuments(any())
        }
    }

    @Test
    fun upsertTask_clearsTombstoneAfterDaoUpsert() = runTest {
        val task = sampleTask()
        coEvery { dao.upsert(any<TaskEntity>()) } just Runs

        repository.upsertTask(task)

        coVerifyOrder {
            dao.upsert(any<TaskEntity>())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
    }
}
