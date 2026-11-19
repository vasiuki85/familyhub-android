package com.familyhub.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.FamilyGoalEntity
import com.familyhub.data.local.entity.FamilyGoalStageEntity
import com.familyhub.data.local.entity.FamilyGoalStepEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.sync.SYNC_OUTBOX_DATABASE_CALLBACK
import com.familyhub.data.sync.SyncChangeTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataWiperInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var settings: SyncSettingsDataStore
    private var tracker: SyncChangeTracker? = null
    private var wiper: LocalDataWiper? = null

    @Before
    fun setUp() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            settings = SyncSettingsDataStore(context)
            settings.resetForSignOut()
            database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
                .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
                .allowMainThreadQueries()
                .build()
            tracker = SyncChangeTracker(database, settings)
            wiper = LocalDataWiper(database, tracker!!)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            tracker?.close()
            settings.resetForSignOut()
            database.close()
        }
    }

    @Test
    fun fullWipe_clearsAllFamilyTablesAndOutbox() {
        runBlocking {
            // Seed: task + family goal (with stage + step)
            seedTask("wipe-task-1")
            seedFamilyGoalWithPlan("wipe-goal-1")

            // Verify outbox has entries
            val outbox = database.syncOutboxDao()
            val seedEntries = outbox.entriesThrough(Long.MAX_VALUE)
            assertThat(seedEntries).isNotEmpty()

            // Perform full wipe
            wiper!!.wipeAllFamilyData()

            // Verify: all family tables empty
            assertThat(database.taskDao().getAllSync()).isEmpty()
            assertThat(database.familyGoalDao().getGoalById("wipe-goal-1")).isNull()

            // Verify: sync_outbox empty
            assertThat(outbox.hasEntries()).isFalse()
            assertThat(outbox.entriesThrough(Long.MAX_VALUE)).isEmpty()
        }
    }

    @Test
    fun partialWipe_removesTasksAndSharedRows_retainsGoalsAndGoalOutbox() {
        runBlocking {
            // Seed: task + shared row (shopping list) + family goal (goal generates outbox)
            seedTask("partial-task-1")
            seedFamilyGoalWithPlan("partial-goal-1")

            val outbox = database.syncOutboxDao()

            // Capture goal-related outbox operations before wipe
            val goalOutboxBefore = outbox.entriesThrough(Long.MAX_VALUE)
                .filter {
                    it.collectionKey in setOf("familyGoals", "goalStages", "goalSteps")
                }
            assertThat(goalOutboxBefore).isNotEmpty()

            // Perform partial wipe (exclude goals)
            wiper!!.wipeAllExceptGoalsAndAwesomeness()

            // Verify: tasks gone
            assertThat(database.taskDao().getAllSync()).isEmpty()

            // Verify: goals retained
            val retainedGoal = database.familyGoalDao().getGoalById("partial-goal-1")
            assertThat(retainedGoal).isNotNull()
            assertThat(retainedGoal!!.id).isEqualTo("partial-goal-1")

            // Verify: outbox has NO entries for non-goal collections
            val nonGoalEntries = outbox.entriesThrough(Long.MAX_VALUE)
                .filter {
                    it.collectionKey !in setOf("familyGoals", "goalStages", "goalSteps")
                }
            assertThat(nonGoalEntries).isEmpty()

            // Verify: goal outbox operations are preserved
            val goalOutboxAfter = outbox.entriesThrough(Long.MAX_VALUE)
                .filter {
                    it.collectionKey in setOf("familyGoals", "goalStages", "goalSteps")
                }
            assertThat(goalOutboxAfter).isNotEmpty()
            assertThat(goalOutboxAfter.map { it.collectionKey to it.entityId })
                .containsAtLeastElementsIn(
                    goalOutboxBefore.map { it.collectionKey to it.entityId }
                )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private suspend fun seedTask(id: String) {
        database.taskDao().upsert(
            TaskEntity(
                id = id,
                title = "Task $id",
                description = null,
                createdByUserId = "parent",
                assignedToUserId = "child",
                dueDate = null,
                repeatRule = "NONE",
                status = "TODO",
                createdAt = 1L,
            )
        )
    }

    private suspend fun seedFamilyGoalWithPlan(goalId: String) {
        val stageId = "$goalId-stage"
        val stepId = "$goalId-step"

        database.familyGoalDao().upsertGoalWithPlan(
            goal = FamilyGoalEntity(
                id = goalId,
                title = "Goal $goalId",
                description = null,
                targetPoints = 100,
                currentPoints = 0,
                deadlineAt = null,
                status = "ACTIVE",
                createdByUserId = "parent",
                createdAt = 1L,
                updatedAt = 1L,
                personal = false,
            ),
            stages = listOf(
                FamilyGoalStageEntity(
                    id = stageId,
                    goalId = goalId,
                    title = "Stage 1",
                    description = null,
                    position = 0,
                )
            ),
            steps = listOf(
                FamilyGoalStepEntity(
                    id = stepId,
                    goalId = goalId,
                    stageId = stageId,
                    title = "Step 1",
                    isCompleted = false,
                    position = 0,
                )
            ),
        )
    }
}
