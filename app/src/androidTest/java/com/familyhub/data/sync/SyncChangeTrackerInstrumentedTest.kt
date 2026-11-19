package com.familyhub.data.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncChangeTrackerInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var settings: SyncSettingsDataStore
    private var tracker: SyncChangeTracker? = null

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = SyncSettingsDataStore(context)
        settings.resetForSignOut()
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = runBlocking {
        tracker?.close()
        settings.resetForSignOut()
        database.close()
    }

    @Test
    fun pendingCollections_surviveTrackerRestart_andClearAtomically() = runBlocking {
        tracker = SyncChangeTracker(database, settings)

        database.taskDao().upsert(task("task-1"))
        awaitPending("tasks")
        assertThat(tracker!!.buildPendingByCollection()).containsExactly("tasks", true)

        tracker!!.close()
        tracker = SyncChangeTracker(database, settings)

        assertThat(tracker!!.buildPendingByCollection()).containsExactly("tasks", true)
        assertThat(settings.settingsFlow.first().hasPendingChanges).isTrue()

        tracker!!.markSynchronized()

        assertThat(tracker!!.buildPendingByCollection()).isEmpty()
        assertThat(settings.settingsFlow.first().hasPendingChanges).isFalse()
    }

    @Test
    fun nestedCloudImportSuppression_doesNotCreatePendingCollections() = runBlocking {
        tracker = SyncChangeTracker(database, settings)
        database.invalidationTracker.refreshVersionsAsync()
        delay(INITIAL_OBSERVER_SETTLE_MS)
        tracker!!.markSynchronized()

        // Model both layers of production import suppression:
        //  - Outer runWithoutTracking scopes suppress InvalidationTracker observer
        //  - Inner begin/endTrackingSuppression (inside Room transaction) suppresses SQL outbox triggers
        tracker!!.runWithoutTracking {
            tracker!!.runWithoutTracking {
                database.withTransaction {
                    database.syncOutboxDao().beginTrackingSuppression()
                    try {
                        database.taskDao().upsert(task("cloud-task"))
                    } finally {
                        database.syncOutboxDao().endTrackingSuppression()
                    }
                }
            }
            database.invalidationTracker.refreshVersionsAsync()
            delay(INVALIDATION_SETTLE_MS)
        }
        delay(POST_SUPPRESSION_WAIT_MS)

        assertThat(tracker!!.buildPendingByCollection()).isEmpty()
        assertThat(settings.settingsFlow.first().hasPendingChanges).isFalse()
        // Outbox must also be empty: the SQL triggers were suppressed
        assertThat(database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun changeRecordedAfterSyncSnapshot_isNotAcknowledgedWithOlderSnapshot() = runBlocking {
        tracker = SyncChangeTracker(database, settings)

        database.taskDao().upsert(task("before-snapshot"))
        awaitPending("tasks")

        val captured = tracker!!.captureSyncSnapshot { Unit }
        assertThat(captured.checkpoint.pendingByCollection).containsEntry("tasks", true)

        database.taskDao().upsert(task("after-snapshot"))
        database.invalidationTracker.refreshVersionsAsync()
        awaitPending("tasks")

        tracker!!.markSynchronized(captured.checkpoint)

        assertThat(tracker!!.hasPendingChangesFor("tasks")).isTrue()
        assertThat(settings.getPendingCollections()).contains("tasks")
    }

    @Test
    fun checkpointCapturesExactEntities_andAckPreservesWritesAfterCapture() {
        runBlocking {
            tracker = SyncChangeTracker(database, settings)

            database.taskDao().upsert(task("before-snapshot"))
            val captured = tracker!!.captureSyncSnapshot { Unit }

            assertThat(captured.checkpoint.pendingEntityIdsByCollection)
                .containsEntry("tasks", setOf("before-snapshot"))
            assertThat(captured.checkpoint.outboxMaxSequence).isGreaterThan(0L)

            database.taskDao().upsert(task("after-snapshot"))
            tracker!!.markSynchronized(captured.checkpoint)

            assertThat(
                database.syncOutboxDao().entriesThrough(Long.MAX_VALUE).map { it.entityId }
            ).containsExactly("after-snapshot")
        }
    }

    @Test
    fun updateLastSync_doesNotClearPendingCollections() = runBlocking {
        tracker = SyncChangeTracker(database, settings)

        database.taskDao().upsert(task("after-sync"))
        awaitPending("tasks")
        awaitPersistedPending("tasks")

        settings.updateLastSync(1_234L)

        assertThat(settings.settingsFlow.first().hasPendingChanges).isTrue()
        assertThat(settings.getPendingCollections()).contains("tasks")
    }

    @Test
    fun compaction_deleteThenUpsert_dropsEntityFromDeletedSet() = runBlocking {
        tracker = SyncChangeTracker(database, settings)

        // 1st: UPSERT → outbox UPSERT
        database.taskDao().upsert(task("compact-du-1"))
        awaitPending("tasks")

        // 2nd: DELETE → outbox DELETE
        val entity = database.taskDao().getById("compact-du-1")
        assertThat(entity).isNotNull()
        database.taskDao().delete(entity!!)

        // 3rd: UPSERT → outbox UPSERT (compacted: last=UPSERT, DELETE evicted)
        database.taskDao().upsert(task("compact-du-1"))

        val captured = tracker!!.captureSyncSnapshot { Unit }

        // Entity IS pending
        assertThat(captured.checkpoint.pendingEntityIdsByCollection["tasks"])
            .contains("compact-du-1")
        // Entity is NOT in pending-deleted set (compacted away)
        val deletedForTasks = captured.checkpoint.pendingDeletedEntityIdsByCollection["tasks"]
            ?: emptySet()
        assertThat(deletedForTasks).doesNotContain("compact-du-1")

        // Latest operation = UPSERT
        val ops = captured.checkpoint.outboxOperations
            .filter { it.collectionKey == "tasks" && it.entityId == "compact-du-1" }
        assertThat(ops).hasSize(1)
        assertThat(ops.first().operation).isEqualTo(SyncOutboxOperation.UPSERT)
    }

    @Test
    fun compaction_upsertThenDelete_keepsEntityInDeletedSet() = runBlocking {
        tracker = SyncChangeTracker(database, settings)

        // 1st: UPSERT → outbox UPSERT
        database.taskDao().upsert(task("compact-ud-1"))
        awaitPending("tasks")

        // 2nd: DELETE → outbox DELETE (compacted: last=DELETE, UPSERT evicted)
        val entity = database.taskDao().getById("compact-ud-1")
        assertThat(entity).isNotNull()
        database.taskDao().delete(entity!!)

        val captured = tracker!!.captureSyncSnapshot { Unit }

        // Entity IS pending
        assertThat(captured.checkpoint.pendingEntityIdsByCollection["tasks"])
            .contains("compact-ud-1")
        // Entity IS in pending-deleted set
        assertThat(captured.checkpoint.pendingDeletedEntityIdsByCollection["tasks"])
            .contains("compact-ud-1")

        // Latest operation = DELETE
        val ops = captured.checkpoint.outboxOperations
            .filter { it.collectionKey == "tasks" && it.entityId == "compact-ud-1" }
        assertThat(ops).hasSize(1)
        assertThat(ops.first().operation).isEqualTo(SyncOutboxOperation.DELETE)
    }

    private suspend fun awaitPending(collectionKey: String) {
        withTimeout(PENDING_TIMEOUT_MS) {
            while (tracker!!.buildPendingByCollection()[collectionKey] != true) {
                database.invalidationTracker.refreshVersionsAsync()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun awaitPersistedPending(collectionKey: String) {
        withTimeout(PENDING_TIMEOUT_MS) {
            while (collectionKey !in settings.getPendingCollections()) {
                delay(POLL_MS)
            }
        }
    }

    private fun task(id: String) = TaskEntity(
        id = id,
        title = "Task $id",
        description = null,
        createdByUserId = "admin",
        assignedToUserId = "child",
        dueDate = null,
        repeatRule = RepeatRule.NONE.name,
        status = TaskStatus.TODO.name,
        createdAt = 1L,
        taskType = TaskType.ROUTINE.name,
    )

    private companion object {
        const val POLL_MS = 20L
        const val PENDING_TIMEOUT_MS = 5_000L
        const val INITIAL_OBSERVER_SETTLE_MS = 250L
        // Mirrors the production import barrier: Room delivers invalidations
        // asynchronously after the transaction commits.
        const val INVALIDATION_SETTLE_MS = 2_200L
        const val POST_SUPPRESSION_WAIT_MS = 250L
    }
}
