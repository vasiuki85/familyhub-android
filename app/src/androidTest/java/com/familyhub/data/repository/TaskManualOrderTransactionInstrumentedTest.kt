package com.familyhub.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.TaskOrderChange
import com.familyhub.domain.model.TaskOrderConflictException
import com.familyhub.domain.model.TaskOrderSnapshotEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskManualOrderTransactionInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var repository: TaskRepository

    private val userId = "user-1"
    private val otherUserId = "other-user"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        repository = TaskRepository(
            dao = taskDao,
            cloudDeletionLedger = CloudDeletionLedger(SyncSettingsDataStore(context)),
            database = database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun visibleInsertAfterSnapshot_conflictsAndLeavesOrderUnchanged() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        upsert(task("c", orderIndex = 30))

        assertThrows(TaskOrderConflictException::class.java) {
            runBlocking {
                repository.applyManualOrderChanges(
                    expectedSnapshot = snapshot,
                    changes = listOf(
                        TaskOrderChange("a", 10, 20),
                        TaskOrderChange("b", 20, 10),
                    ),
                    currentUserId = userId,
                )
            }
        }

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(20)
        assertThat(taskDao.getById("c")?.orderIndex).isEqualTo(30)
    }

    @Test
    fun deleteAfterSnapshot_conflictsAndDoesNotResurrect() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        taskDao.delete(task("b", orderIndex = 20))

        assertThrows(TaskOrderConflictException::class.java) {
            runBlocking {
                repository.applyManualOrderChanges(
                    expectedSnapshot = snapshot,
                    changes = listOf(
                        TaskOrderChange("a", 10, 20),
                        TaskOrderChange("b", 20, 10),
                    ),
                    currentUserId = userId,
                )
            }
        }

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
        assertThat(taskDao.getById("b")).isNull()
    }

    @Test
    fun concurrentTitleStatusEdit_preservesPayloadWhenOrderIndexStable() = runBlocking {
        upsert(task("a", orderIndex = 10, title = "Old", status = "TODO"))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        upsert(task("a", orderIndex = 10, title = "New Title", status = "DONE"))

        repository.applyManualOrderChanges(
            expectedSnapshot = snapshot,
            changes = listOf(
                TaskOrderChange("a", 10, 20),
                TaskOrderChange("b", 20, 10),
            ),
            currentUserId = userId,
        )

        val a = taskDao.getById("a")!!
        assertThat(a.orderIndex).isEqualTo(20)
        assertThat(a.title).isEqualTo("New Title")
        assertThat(a.status).isEqualTo("DONE")
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(10)
    }

    @Test
    fun hiddenForeignPrivateInsert_doesNotBlockVisibleCohort() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        upsert(
            task(
                id = "secret",
                orderIndex = 99,
                isPrivate = true,
                createdByUserId = otherUserId,
                assignedToUserId = otherUserId,
            )
        )

        repository.applyManualOrderChanges(
            expectedSnapshot = snapshot,
            changes = listOf(
                TaskOrderChange("a", 10, 20),
                TaskOrderChange("b", 20, 10),
            ),
            currentUserId = userId,
        )

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(20)
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(10)
        assertThat(taskDao.getById("secret")?.orderIndex).isEqualTo(99)
    }

    @Test
    fun visibilityChangeIntoCohort_blocksReorder() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        upsert(
            task(
                id = "hidden",
                orderIndex = 30,
                isPrivate = true,
                createdByUserId = otherUserId,
                assignedToUserId = otherUserId,
            )
        )
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        upsert(
            task(
                id = "hidden",
                orderIndex = 30,
                isPrivate = false,
                createdByUserId = otherUserId,
                assignedToUserId = otherUserId,
            )
        )

        assertThrows(TaskOrderConflictException::class.java) {
            runBlocking {
                repository.applyManualOrderChanges(
                    expectedSnapshot = snapshot,
                    changes = listOf(
                        TaskOrderChange("a", 10, 20),
                        TaskOrderChange("b", 20, 10),
                    ),
                    currentUserId = userId,
                )
            }
        }

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(20)
    }

    @Test
    fun assigneeChangeIntoCohort_blocksReorder() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        upsert(
            task(
                id = "hidden",
                orderIndex = 30,
                isPrivate = true,
                createdByUserId = otherUserId,
                assignedToUserId = otherUserId,
            )
        )
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        upsert(
            task(
                id = "hidden",
                orderIndex = 30,
                isPrivate = true,
                createdByUserId = otherUserId,
                assignedToUserId = userId,
            )
        )

        assertThrows(TaskOrderConflictException::class.java) {
            runBlocking {
                repository.applyManualOrderChanges(
                    expectedSnapshot = snapshot,
                    changes = listOf(
                        TaskOrderChange("a", 10, 20),
                        TaskOrderChange("b", 20, 10),
                    ),
                    currentUserId = userId,
                )
            }
        }

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(20)
    }

    @Test
    fun secondCasFault_rollsBackFirstUpdate() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        val sqlite = database.openHelper.writableDatabase
        installAbortOnSecondOrderUpdate(sqlite)
        try {
            assertThrows(SQLiteException::class.java) {
                runBlocking {
                    repository.applyManualOrderChanges(
                        expectedSnapshot = snapshot,
                        changes = listOf(
                            TaskOrderChange("a", 10, 20),
                            TaskOrderChange("b", 20, 10),
                        ),
                        currentUserId = userId,
                    )
                }
            }
            assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
            assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(20)
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS abort_second_order_update")
        }
    }

    @Test
    fun staleSnapshotAfterSuccessfulCommit_conflicts() = runBlocking {
        upsert(task("a", orderIndex = 10))
        upsert(task("b", orderIndex = 20))
        val snapshot = listOf(
            TaskOrderSnapshotEntry("a", 10),
            TaskOrderSnapshotEntry("b", 20),
        )
        val changes = listOf(
            TaskOrderChange("a", 10, 20),
            TaskOrderChange("b", 20, 10),
        )

        repository.applyManualOrderChanges(snapshot, changes, userId)

        assertThrows(TaskOrderConflictException::class.java) {
            runBlocking {
                repository.applyManualOrderChanges(snapshot, changes, userId)
            }
        }

        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(20)
        assertThat(taskDao.getById("b")?.orderIndex).isEqualTo(10)
    }

    @Test
    fun emptyChanges_isFullNoOp() = runBlocking {
        upsert(task("a", orderIndex = 10))
        repository.applyManualOrderChanges(
            expectedSnapshot = listOf(TaskOrderSnapshotEntry("a", 10)),
            changes = emptyList(),
            currentUserId = userId,
        )
        assertThat(taskDao.getById("a")?.orderIndex).isEqualTo(10)
    }

    private fun installAbortOnSecondOrderUpdate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER abort_second_order_update
            AFTER UPDATE OF orderIndex ON tasks
            WHEN NEW.id = 'b'
            BEGIN
                SELECT RAISE(ABORT, 'test abort second order update');
            END
            """.trimIndent()
        )
    }

    private suspend fun upsert(entity: TaskEntity) {
        taskDao.upsert(entity)
    }

    private fun task(
        id: String,
        orderIndex: Int,
        title: String = "Task $id",
        status: String = "TODO",
        isPrivate: Boolean = false,
        createdByUserId: String = userId,
        assignedToUserId: String = userId,
    ) = TaskEntity(
        id = id,
        title = title,
        description = null,
        createdByUserId = createdByUserId,
        assignedToUserId = assignedToUserId,
        dueDate = null,
        repeatRule = "NONE",
        status = status,
        createdAt = id.hashCode().toLong(),
        orderIndex = orderIndex,
        isPrivate = isPrivate,
        taskType = "ROUTINE",
        priority = "MEDIUM",
    )
}
