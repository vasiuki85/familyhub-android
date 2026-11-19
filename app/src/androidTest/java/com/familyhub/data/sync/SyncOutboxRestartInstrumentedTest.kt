package com.familyhub.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.di.MIGRATION_19_20
import com.familyhub.di.MIGRATION_20_21
import com.familyhub.di.MIGRATION_21_22
import com.familyhub.di.MIGRATION_22_23
import com.familyhub.di.MIGRATION_23_24
import com.familyhub.di.MIGRATION_24_25
import com.familyhub.di.MIGRATION_25_26
import com.familyhub.di.MIGRATION_26_27
import com.familyhub.di.MIGRATION_27_28
import com.familyhub.di.MIGRATION_28_29
import com.familyhub.di.MIGRATION_29_30
import com.familyhub.di.MIGRATION_30_31
import com.familyhub.di.MIGRATION_31_32
import com.familyhub.di.MIGRATION_32_33
import com.familyhub.di.MIGRATION_33_34
import com.familyhub.di.MIGRATION_34_35
import com.familyhub.di.MIGRATION_35_36
import com.familyhub.di.MIGRATION_36_37
import com.familyhub.di.MIGRATION_37_38
import com.familyhub.di.MIGRATION_38_39
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies deterministic file-backed restart gate behaviour for the persisted sync outbox.
 *
 * Uses a REAL on-disk Room database (schema v39 + SYNC_OUTBOX_DATABASE_CALLBACK)
 * so that close→reopen faithfully simulates process recreation.
 */
@RunWith(AndroidJUnit4::class)
class SyncOutboxRestartInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Nuke any leftover test DB from a previous run.
        closeAndDeleteDatabase()
        openDatabase()
    }

    @After
    fun tearDown() {
        closeAndDeleteDatabase()
    }

    // ── Production mirror: real file-backed builder ────────────────────

    private fun openDatabase() {
        database = Room.databaseBuilder(context, FamilyHubDatabase::class.java, TEST_DB_NAME)
            .addMigrations(
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_36_37,
                MIGRATION_37_38,
                MIGRATION_38_39,
            )
            .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    private fun closeDatabase() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    /** Closes the handle (if open) and deletes the on-disk test database. */
    private fun closeAndDeleteDatabase() {
        closeDatabase()
        context.deleteDatabase(TEST_DB_NAME)
    }

    // ── Test ───────────────────────────────────────────────────────────

    @Test
    fun outboxSurvivesProcessRestart_deleteThroughRemovesOnlyOldSequence() =
        runBlocking {
            // 1) Local Task UPSERT creates outbox row
            database.taskDao().upsert(task("task-restart"))

            val outbox = database.syncOutboxDao()
            val initialSequence = checkNotNull(outbox.maxSequence()) {
                "Expected at least one outbox row after task upsert"
            }
            assertThat(initialSequence).isGreaterThan(0L)

            // Verify the outbox entry
            val initialEntries = outbox.entriesThrough(initialSequence)
            assertThat(initialEntries).hasSize(1)
            assertThat(initialEntries.single().collectionKey).isEqualTo("tasks")
            assertThat(initialEntries.single().entityId).isEqualTo("task-restart")
            assertThat(initialEntries.single().operation)
                .isEqualTo(SyncOutboxOperation.UPSERT.name)

            // 2) Close DB fully and reopen — simulates process recreation
            val oldMax = initialSequence
            database.close()
            openDatabase()

            // After restart: outbox row and max sequence MUST be preserved
            val afterRestartOutbox = database.syncOutboxDao()
            assertThat(afterRestartOutbox.maxSequence()).isEqualTo(oldMax)

            val afterRestartEntries = afterRestartOutbox.entriesThrough(oldMax)
            assertThat(afterRestartEntries).hasSize(1)
            assertThat(afterRestartEntries.single().collectionKey).isEqualTo("tasks")
            assertThat(afterRestartEntries.single().entityId).isEqualTo("task-restart")
            assertThat(afterRestartEntries.single().operation)
                .isEqualTo(SyncOutboxOperation.UPSERT.name)

            // 3) Write NEW unrelated operation with sequence > oldMax
            database.shoppingItemDao().upsert(shoppingItem("new-item"))

            val newSequence = checkNotNull(afterRestartOutbox.maxSequence()) {
                "Expected outbox row for shopping item"
            }
            assertThat(newSequence).isGreaterThan(oldMax)

            // deleteThrough(oldMax) → only the old operation is deleted
            afterRestartOutbox.deleteThrough(oldMax)

            val remainingAfterDelete = afterRestartOutbox.entriesThrough(Long.MAX_VALUE)
            assertThat(remainingAfterDelete).hasSize(1)
            assertThat(remainingAfterDelete.single().collectionKey).isEqualTo("shoppingItems")
            assertThat(remainingAfterDelete.single().entityId).isEqualTo("new-item")
            assertThat(remainingAfterDelete.single().operation)
                .isEqualTo(SyncOutboxOperation.UPSERT.name)

            // 4) Close/reopen again — prove new operation survived the second restart
            database.close()
            openDatabase()

            val finalEntries = database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)
            assertThat(finalEntries).hasSize(1)
            val finalEntry = finalEntries.single()
            assertThat(finalEntry.collectionKey).isEqualTo("shoppingItems")
            assertThat(finalEntry.entityId).isEqualTo("new-item")
            assertThat(finalEntry.operation).isEqualTo(SyncOutboxOperation.UPSERT.name)

            // Sanity: old operation is truly gone
            assertThat(
                finalEntries.none {
                    it.collectionKey == "tasks" && it.entityId == "task-restart"
                }
            ).isTrue()
        }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun task(id: String): TaskEntity = TaskEntity(
        id = id,
        title = id,
        description = null,
        createdByUserId = "parent",
        assignedToUserId = "child",
        dueDate = null,
        repeatRule = "NONE",
        status = "TODO",
        createdAt = 1L,
    )

    private fun shoppingItem(id: String): ShoppingItemEntity = ShoppingItemEntity(
        id = id,
        listId = "restart-list",
        title = id,
        createdByUserId = "child",
        isBought = false,
        createdAt = 2L,
        categoryId = null,
        quantity = null,
        quantityUnit = null,
        note = null,
        boughtAt = null,
    )

    private companion object {
        const val TEST_DB_NAME = "test-sync-outbox-restart.db"
    }
}
