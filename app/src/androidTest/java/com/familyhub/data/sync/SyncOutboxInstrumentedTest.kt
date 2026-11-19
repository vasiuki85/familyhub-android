package com.familyhub.data.sync

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.domain.model.MemberRole
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncOutboxInstrumentedTest {

    private lateinit var database: FamilyHubDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    installSyncOutboxInfrastructure(db)
                }
            })
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localWrites_areJournaledAndAcknowledgedByCapturedSequence() {
        runBlocking {
            val outbox = database.syncOutboxDao()

            database.taskDao().upsert(task("task-a"))
            val capturedSequence = checkNotNull(outbox.maxSequence())
            assertThat(outbox.entriesThrough(capturedSequence).map { entry ->
                Triple(entry.collectionKey, entry.entityId, entry.operation)
            }).containsExactly(Triple("tasks", "task-a", SyncOutboxOperation.UPSERT.name))

            database.shoppingItemDao().upsert(shoppingItem("item-after-capture"))
            outbox.deleteThrough(capturedSequence)

            assertThat(outbox.entriesThrough(Long.MAX_VALUE).map { entry ->
                Triple(entry.collectionKey, entry.entityId, entry.operation)
            }).containsExactly(
                Triple("shoppingItems", "item-after-capture", SyncOutboxOperation.UPSERT.name)
            )
        }
    }

    @Test
    fun cloudImportTransaction_doesNotJournalImportedRows() {
        runBlocking {
            val outbox = database.syncOutboxDao()

            database.withTransaction {
                outbox.beginTrackingSuppression()
                try {
                    database.taskDao().upsert(task("cloud-task"))
                    database.shoppingItemDao().upsert(shoppingItem("cloud-item"))
                } finally {
                    outbox.endTrackingSuppression()
                }
            }

            assertThat(outbox.entriesThrough(Long.MAX_VALUE)).isEmpty()
        }
    }

    @Test
    fun nestedSuppression_doesNotReenableTriggersUntilOuterScopeEnds() {
        runBlocking {
            val outbox = database.syncOutboxDao()

            database.withTransaction {
                outbox.beginTrackingSuppression()
                outbox.beginTrackingSuppression()
                database.taskDao().upsert(task("nested-import"))

                outbox.endTrackingSuppression()
                database.taskDao().upsert(task("outer-import"))

                outbox.endTrackingSuppression()
                database.taskDao().upsert(task("user-after-import"))
            }

            assertThat(outbox.entriesThrough(Long.MAX_VALUE).map { it.entityId })
                .containsExactly("user-after-import")
        }
    }

    @Test
    fun transactionSuppression_preventsOutboxJournal_and_allowsConcurrentWriteAfterCommit() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val syncSettings = SyncSettingsDataStore(context)
            syncSettings.resetForSignOut()
            val tracker = SyncChangeTracker(database, syncSettings)
            val outbox = database.syncOutboxDao()
            val memberUpserted = CompletableDeferred<Unit>()
            val allowCommit = CompletableDeferred<Unit>()
            val concurrentTaskAttempted = CompletableDeferred<Unit>()
            val concurrentTaskWritten = CompletableDeferred<Unit>()

            try {
                val helperJob = async(Dispatchers.IO) {
                    tracker.runInTransactionWithoutTracking {
                        database.familyMemberDao().upsert(
                            FamilyMemberEntity(
                                id = "uid-bootstrap",
                                displayName = "Bootstrap",
                                roleLabel = "Bootstrap",
                                avatarColor = "#000",
                                role = MemberRole.PENDING,
                            )
                        )
                        memberUpserted.complete(Unit)
                        allowCommit.await()
                    }
                }

                withTimeout(5_000) { memberUpserted.await() }

                val taskJob = async(Dispatchers.IO) {
                    concurrentTaskAttempted.complete(Unit)
                    database.taskDao().upsert(task("concurrent-task"))
                    concurrentTaskWritten.complete(Unit)
                }

                withTimeout(5_000) { concurrentTaskAttempted.await() }
                val completedBeforeCommit = withTimeoutOrNull(150) {
                    concurrentTaskWritten.await()
                }
                assertThat(completedBeforeCommit).isNull()

                allowCommit.complete(Unit)
                withTimeout(5_000) {
                    helperJob.await()
                    taskJob.await()
                }

                val entries = outbox.entriesThrough(Long.MAX_VALUE)
                assertThat(entries.map { it.entityId })
                    .containsExactly("concurrent-task")

                database.openHelper.writableDatabase
                    .query("SELECT ignoreDepth FROM sync_tracking_control WHERE id = 1")
                    .use { cursor ->
                        assertThat(cursor.moveToFirst()).isTrue()
                        assertThat(cursor.getInt(0)).isEqualTo(0)
                    }

                database.taskDao().upsert(task("post-verify"))
                assertThat(outbox.entriesThrough(Long.MAX_VALUE).map { it.entityId })
                    .containsExactly("concurrent-task", "post-verify")
                    .inOrder()
            } finally {
                allowCommit.complete(Unit)
                tracker.close()
                syncSettings.resetForSignOut()
            }
        }
    }

    private fun memberEntity(
        id: String,
        displayName: String = id,
        role: MemberRole = MemberRole.CHILD,
    ): FamilyMemberEntity = FamilyMemberEntity(
        id = id,
        displayName = displayName,
        roleLabel = displayName,
        avatarColor = "#2196F3",
        role = role,
    )

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
        listId = "list-1",
        title = id,
        createdByUserId = "child",
        isBought = false,
        createdAt = 1L,
        categoryId = null,
        quantity = null,
        quantityUnit = null,
        note = null,
        boughtAt = null,
    )
}
