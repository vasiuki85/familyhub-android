package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.entity.FamilyGoalEntity
import com.familyhub.data.local.entity.FamilyGoalStageEntity
import com.familyhub.data.local.entity.FamilyGoalStepEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.domain.model.SyncSettings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FamilyGoalsRepositoryTombstoneTest {

    private val dao = mockk<FamilyGoalDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: FamilyGoalsRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.settingsFlow } returns flowOf(familySettings())
        coEvery { syncSettingsDataStore.addDeletedDocuments(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedDocuments(any()) } just Runs
        coEvery { dao.deleteGoal(any()) } just Runs
        coEvery {
            dao.upsertGoalWithPlan(any(), any(), any())
        } just Runs
        coEvery { dao.upsertGoal(any()) } just Runs
        repository = FamilyGoalsRepository(dao, CloudDeletionLedger(syncSettingsDataStore))
    }

    @Test
    fun delete_recordsGoalStagesAndStepsInSingleBatchBeforeDaoDelete() = runTest {
        val goal = sampleGoal()
        coEvery { dao.getStageIdsByGoal("goal-1") } returns listOf("stage-1", "stage-2")
        coEvery { dao.getStepIdsByGoal("goal-1") } returns listOf("step-1", "step-2")

        repository.delete(goal)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(match { documents ->
                documents.map { it.collection to it.documentId }.toSet() == setOf(
                    DeletedCloudDocumentCollections.FAMILY_GOALS to "goal-1",
                    DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to "stage-1",
                    DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to "stage-2",
                    DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to "step-1",
                    DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to "step-2"
                )
            })
            dao.deleteGoal("goal-1")
        }
        coVerify(exactly = 1) { syncSettingsDataStore.addDeletedDocuments(any()) }
    }

    @Test
    fun upsertGoalWithPlan_tombstonesOnlyRemovedChildIdsBeforeDaoReplacement() = runTest {
        val goal = sampleGoal()
        val stages = listOf(
            FamilyGoalStage(
                id = "stage-keep",
                goalId = "goal-1",
                title = "Keep",
                description = null,
                position = 0,
                steps = listOf(
                    FamilyGoalStep("step-keep", "goal-1", "stage-keep", "Keep step", false, 0),
                    FamilyGoalStep("step-new", "goal-1", "stage-keep", "New step", false, 1)
                )
            ),
            FamilyGoalStage(
                id = "stage-new",
                goalId = "goal-1",
                title = "New",
                description = null,
                position = 1,
                steps = emptyList()
            )
        )
        coEvery { dao.getStageIdsByGoal("goal-1") } returns listOf("stage-keep", "stage-removed")
        coEvery { dao.getStepIdsByGoal("goal-1") } returns listOf("step-keep", "step-removed")

        repository.upsert(goal, stages)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedDocuments(
                match { documents ->
                    documents.map { it.collection to it.documentId }.toSet() == setOf(
                        DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to "stage-removed",
                        DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to "step-removed"
                    )
                }
            )
            dao.upsertGoalWithPlan(any<FamilyGoalEntity>(), any(), any())
            syncSettingsDataStore.removeDeletedDocuments(any())
        }
        coVerify(exactly = 0) {
            syncSettingsDataStore.addDeletedDocuments(
                match { documents ->
                    documents.any { it.documentId == "stage-keep" || it.documentId == "step-keep" }
                }
            )
        }
    }

    @Test
    fun upsertGoalWithPlan_clearsTombstonesForReusedChildIdsAfterDaoUpsert() = runTest {
        val goal = sampleGoal()
        val stages = listOf(
            FamilyGoalStage(
                id = "stage-keep",
                goalId = "goal-1",
                title = "Keep",
                description = null,
                position = 0,
                steps = listOf(
                    FamilyGoalStep("step-keep", "goal-1", "stage-keep", "Keep step", false, 0)
                )
            )
        )
        coEvery { dao.getStageIdsByGoal("goal-1") } returns listOf("stage-keep")
        coEvery { dao.getStepIdsByGoal("goal-1") } returns listOf("step-keep")

        repository.upsert(goal, stages)

        coVerify {
            syncSettingsDataStore.removeDeletedDocuments(
                match { documents ->
                    documents.map { it.collection to it.documentId }.toSet() == setOf(
                        DeletedCloudDocumentCollections.FAMILY_GOALS to "goal-1",
                        DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES to "stage-keep",
                        DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS to "step-keep"
                    )
                }
            )
        }
    }

    @Test
    fun upsertGoalOnly_clearsGoalTombstoneWithoutTouchingStagesOrSteps() = runTest {
        repository.upsert(sampleGoal())

        coVerifyOrder {
            dao.upsertGoal(any<FamilyGoalEntity>())
            syncSettingsDataStore.removeDeletedDocuments(
                match { documents ->
                    documents.single().let {
                        it.collection == DeletedCloudDocumentCollections.FAMILY_GOALS &&
                            it.documentId == "goal-1"
                    }
                }
            )
        }
        coVerify(exactly = 0) { dao.getStageIdsByGoal(any()) }
        coVerify(exactly = 0) { dao.getStepIdsByGoal(any()) }
    }

    private fun sampleGoal() = FamilyGoal(
        id = "goal-1",
        title = "Trip",
        description = null,
        targetPoints = 1,
        currentPoints = 0,
        deadlineAt = null,
        status = FamilyGoalStatus.ACTIVE,
        createdByUserId = "user-1",
        createdAt = 1L,
        updatedAt = 1L
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
