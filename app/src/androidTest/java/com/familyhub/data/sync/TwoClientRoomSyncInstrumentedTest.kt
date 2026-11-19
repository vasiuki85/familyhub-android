package com.familyhub.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.export.DataExportService
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportTask
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.repository.AppreciationRepository
import com.familyhub.data.repository.EmergencyContactRepository
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.HouseRulesRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.PenaltyTransitionCoordinator
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskCategoryRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.data.repository.TaskTemplateRepository
import com.familyhub.data.sync.logic.SyncMerger
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.IdGenerator
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * File-backed two-client sync harness using the same Room/outbox/export/merge/import/upload-plan
 * seams as production. Only the transport is replaced by a deterministic shared fake backend.
 */
@RunWith(AndroidJUnit4::class)
class TwoClientRoomSyncInstrumentedTest {

    private lateinit var context: Context
    private lateinit var dataStoreRoot: File
    private lateinit var clientA: TestClient
    private lateinit var clientB: TestClient
    private lateinit var backend: FakeBackend

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName(CLIENT_A))
        context.deleteDatabase(databaseName(CLIENT_B))
        dataStoreRoot = File(context.filesDir, DATASTORE_DIRECTORY)
        dataStoreRoot.deleteRecursively()
        check(dataStoreRoot.mkdirs()) { "Could not create isolated DataStore directory" }

        clientA = openClient(CLIENT_A)
        clientB = openClient(CLIENT_B)
        backend = FakeBackend()
    }

    @After
    fun tearDown() = runBlocking {
        if (::clientA.isInitialized) clientA.close()
        if (::clientB.isInitialized) clientB.close()
        if (::context.isInitialized) {
            context.deleteDatabase(databaseName(CLIENT_A))
            context.deleteDatabase(databaseName(CLIENT_B))
        }
        if (::dataStoreRoot.isInitialized) dataStoreRoot.deleteRecursively()
    }

    @Test
    fun clientsUseIndependentRoomAndDataStoreFiles() = runBlocking {
        clientA.seed(ExportData(tasks = listOf(task(TASK_ONLY_A))))
        clientA.settings.setPendingChanges(true)

        assertThat(clientA.settings.settingsFlow.first().hasPendingChanges).isTrue()
        assertThat(clientB.settings.settingsFlow.first().hasPendingChanges).isFalse()
        assertThat(clientA.database.taskDao().getById(TASK_ONLY_A)).isNotNull()
        assertThat(clientB.database.taskDao().getById(TASK_ONLY_A)).isNull()
    }

    @Test
    fun deletionOnA_isNotRestoredByStaleB() = runBlocking {
        seedBoth(task(TASK_DELETED))

        clientA.deleteTask(TASK_DELETED)
        val uploadA = sync(clientA)

        assertThat(uploadA.plan)
            .containsExactly(PlannedUploadOperation.Delete(TASKS_COLLECTION, TASK_DELETED))
        assertThat(backend.taskIds()).doesNotContain(TASK_DELETED)

        val uploadB = sync(clientB)

        assertThat(uploadB.plan).isEmpty()
        assertThat(clientB.taskIds()).doesNotContain(TASK_DELETED)
        assertThat(backend.taskIds()).doesNotContain(TASK_DELETED)
    }

    @Test
    fun dirtySiblingInSameCollection_doesNotResurrectDeletedEntity() = runBlocking {
        seedBoth(
            task(TASK_DELETED),
            task(TASK_SIBLING, title = "Before"),
        )

        clientA.deleteTask(TASK_DELETED)
        sync(clientA)

        clientB.updateTaskTitle(TASK_SIBLING, "After")
        val uploadB = sync(clientB)

        assertThat(uploadB.captured.checkpoint.pendingEntityIdsByCollection["tasks"])
            .containsExactly(TASK_SIBLING)
        assertThat(uploadB.plan.filterIsInstance<PlannedUploadOperation.Set>().map { it.documentId })
            .containsExactly(TASK_SIBLING)
        assertThat(uploadB.plan.filterIsInstance<PlannedUploadOperation.Delete>()).isEmpty()
        assertThat(clientB.taskIds()).containsExactly(TASK_SIBLING)
        assertThat(backend.taskIds()).containsExactly(TASK_SIBLING)
        assertThat(backend.task(TASK_SIBLING)?.title).isEqualTo("After")
    }

    @Test
    fun pendingDelete_survivesClientRestart() = runBlocking {
        seedBoth(task(TASK_DELETED))

        clientA.deleteTask(TASK_DELETED)
        assertThat(
            clientA.database.syncOutboxDao().entriesThrough(Long.MAX_VALUE).single().operation
        ).isEqualTo(SyncOutboxOperation.DELETE.name)

        clientA.close()
        clientA = openClient(CLIENT_A)

        val capturedAfterRestart = clientA.tracker.captureSyncSnapshot {
            clientA.exportService.getExportData(CURRENT_USER_ID)
        }
        assertThat(capturedAfterRestart.checkpoint.pendingDeletedEntityIdsByCollection["tasks"])
            .containsExactly(TASK_DELETED)

        val uploadA = sync(clientA)

        assertThat(uploadA.plan)
            .containsExactly(PlannedUploadOperation.Delete(TASKS_COLLECTION, TASK_DELETED))
        assertThat(backend.taskIds()).doesNotContain(TASK_DELETED)
        assertThat(clientA.database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun repeatedSync_isIdempotent() = runBlocking {
        seedBoth(task(TASK_SIBLING, title = "Before"))
        clientA.updateTaskTitle(TASK_SIBLING, "After")

        val first = sync(clientA)
        val transactionCountAfterFirst = backend.transactionCount
        val firstLocalTasks = clientA.exportService.getExportData(CURRENT_USER_ID).tasks

        val second = sync(clientA)
        val secondLocalTasks = clientA.exportService.getExportData(CURRENT_USER_ID).tasks

        assertThat(first.plan.filterIsInstance<PlannedUploadOperation.Set>().map { it.documentId })
            .containsExactly(TASK_SIBLING)
        assertThat(second.plan).isEmpty()
        assertThat(backend.transactionCount).isEqualTo(transactionCountAfterFirst)
        assertThat(secondLocalTasks).isEqualTo(firstLocalTasks)
        assertThat(backend.task(TASK_SIBLING)?.title).isEqualTo("After")
        assertThat(clientA.database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
    }

    private suspend fun seedBoth(vararg tasks: ExportTask) {
        val snapshot = ExportData(tasks = tasks.toList())
        backend.reset(snapshot)
        clientA.seed(snapshot)
        clientB.seed(snapshot)
    }

    private suspend fun sync(client: TestClient): SyncRun {
        val captured = client.tracker.captureSyncSnapshot {
            client.exportService.getExportData(CURRENT_USER_ID)
        }
        val remote = backend.download()
        val merged = SyncMerger().mergeSnapshots(
            local = captured.value,
            remote = remote,
            currentUserId = CURRENT_USER_ID,
            isMemberWriteAllowed = true,
            hasPendingChanges = captured.checkpoint.hasPendingChanges,
            pendingByCollection = captured.checkpoint.pendingByCollection,
            pendingEntityIdsByCollection = captured.checkpoint.pendingEntityIdsByCollection,
            pendingDeletedEntityIdsByCollection =
                captured.checkpoint.pendingDeletedEntityIdsByCollection,
        )
        val plan = buildUploadPlan(
            baseSnapshot = remote,
            targetSnapshot = merged,
            isMemberWriteAllowed = true,
            selfMember = null,
            writerRole = MemberRole.ADMIN,
        )

        backend.apply(plan)
        client.importService.importData(
            data = merged,
            expectedOutboxMaxSequence = captured.checkpoint.outboxMaxSequence,
        )
        client.tracker.markSynchronized(captured.checkpoint)

        return SyncRun(captured = captured, merged = merged, plan = plan)
    }

    private fun openClient(clientId: String): TestClient {
        val dataStoreJob = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dataStoreJob + Dispatchers.IO),
            produceFile = { File(dataStoreRoot, "$clientId.preferences_pb") },
        )
        val settings = SyncSettingsDataStore(preferences)
        val database = Room.databaseBuilder(
            context,
            FamilyHubDatabase::class.java,
            databaseName(clientId),
        )
            .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        val tracker = SyncChangeTracker(database, settings)

        return TestClient(
            database = database,
            settings = settings,
            tracker = tracker,
            exportService = buildExportService(database, settings),
            importService = buildImportService(database, tracker),
            dataStoreJob = dataStoreJob,
        )
    }

    private fun buildExportService(
        database: FamilyHubDatabase,
        settings: SyncSettingsDataStore,
    ): DataExportService {
        val deletionLedger = CloudDeletionLedger(settings)
        return DataExportService(
            context = context,
            memberRepository = FamilyMemberRepository(database.familyMemberDao()),
            taskRepository = TaskRepository(database.taskDao(), deletionLedger, database),
            shoppingRepository = ShoppingRepository(
                database.shoppingItemDao(),
                IdGenerator,
                deletionLedger,
            ),
            eventRepository = FamilyEventRepository(database.familyEventDao(), deletionLedger),
            scoreRepository = ScoreRepository(
                database.userScoreDao(),
                database.taskCompletionDao(),
                database.userAchievementDao(),
                database.userWalletDao(),
            ),
            rewardRepository = RewardRepository(
                database.rewardDao(),
                database.rewardRequestDao(),
                deletionLedger,
            ),
            taskRotationRepository = TaskRotationRepository(
                database.taskRotationDao(),
                deletionLedger,
            ),
            houseRulesRepository = HouseRulesRepository(database.houseRuleDao(), deletionLedger),
            taskTemplateRepository = TaskTemplateRepository(
                database.taskTemplateDao(),
                deletionLedger,
            ),
            taskCategoryRepository = TaskCategoryRepository(
                database.taskCategoryDao(),
                deletionLedger,
            ),
            appreciationRepository = AppreciationRepository(
                database.appreciationDao(),
                deletionLedger,
            ),
            familyGoalsRepository = FamilyGoalsRepository(database.familyGoalDao(), deletionLedger),
            emergencyContactRepository = EmergencyContactRepository(
                database.emergencyContactDao(),
                deletionLedger,
            ),
            penaltyRepository = PenaltyRepository(
                database.penaltyDao(),
                PenaltyTransitionCoordinator(database),
            ),
            recipeRepository = RecipeRepository(database.recipeDao(), settings),
            weeklyMenuDao = database.weeklyMenuDao(),
            menuTemplateDao = database.menuTemplateDao(),
            taskHistoryDao = database.taskHistoryDao(),
            dailyAssignmentDao = database.dailyAssignmentDao(),
        )
    }

    private fun buildImportService(
        database: FamilyHubDatabase,
        tracker: SyncChangeTracker,
    ): DataImportService = DataImportService(
        database = database,
        memberDao = database.familyMemberDao(),
        taskDao = database.taskDao(),
        shoppingItemDao = database.shoppingItemDao(),
        eventDao = database.familyEventDao(),
        scoreDao = database.userScoreDao(),
        taskCompletionDao = database.taskCompletionDao(),
        achievementDao = database.userAchievementDao(),
        rewardDao = database.rewardDao(),
        rewardRequestDao = database.rewardRequestDao(),
        walletDao = database.userWalletDao(),
        rotationDao = database.taskRotationDao(),
        houseRuleDao = database.houseRuleDao(),
        taskTemplateDao = database.taskTemplateDao(),
        taskCategoryDao = database.taskCategoryDao(),
        appreciationDao = database.appreciationDao(),
        familyGoalDao = database.familyGoalDao(),
        emergencyContactDao = database.emergencyContactDao(),
        penaltyDao = database.penaltyDao(),
        recipeDao = database.recipeDao(),
        menuTemplateDao = database.menuTemplateDao(),
        weeklyMenuDao = database.weeklyMenuDao(),
        taskHistoryDao = database.taskHistoryDao(),
        dailyAssignmentDao = database.dailyAssignmentDao(),
        json = Json { ignoreUnknownKeys = true },
        syncChangeTracker = tracker,
    )

    private fun task(id: String, title: String = "Task $id"): ExportTask = ExportTask(
        id = id,
        title = title,
        createdByUserId = CURRENT_USER_ID,
        assignedToUserId = CURRENT_USER_ID,
        repeatRule = "NONE",
        status = "TODO",
        createdAt = 1L,
        taskType = "ROUTINE",
    )

    private fun databaseName(clientId: String): String = "two-client-sync-$clientId.db"

    private data class SyncRun(
        val captured: CapturedSyncSnapshot<ExportData>,
        val merged: ExportData,
        val plan: List<PlannedUploadOperation>,
    )

    private class TestClient(
        val database: FamilyHubDatabase,
        val settings: SyncSettingsDataStore,
        val tracker: SyncChangeTracker,
        val exportService: DataExportService,
        val importService: DataImportService,
        private val dataStoreJob: Job,
    ) {
        suspend fun seed(snapshot: ExportData) {
            importService.importData(
                data = snapshot,
                markSynchronized = true,
            )
            assertThat(database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
        }

        suspend fun deleteTask(taskId: String) {
            database.taskDao().delete(requireNotNull(database.taskDao().getById(taskId)))
        }

        suspend fun updateTaskTitle(taskId: String, title: String) {
            val current = requireNotNull(database.taskDao().getById(taskId))
            database.taskDao().upsert(current.copy(title = title))
        }

        suspend fun taskIds(): Set<String> = database.taskDao().getAllSync().mapTo(mutableSetOf()) { it.id }

        suspend fun close() {
            tracker.close()
            database.close()
            dataStoreJob.cancelAndJoin()
        }
    }

    private class FakeBackend {
        private var snapshot: ExportData = ExportData()
        var transactionCount: Int = 0
            private set

        fun reset(snapshot: ExportData) {
            this.snapshot = snapshot.copy(tasks = snapshot.tasks.map { it.copy() })
            transactionCount = 0
        }

        fun download(): ExportData = snapshot.copy(tasks = snapshot.tasks.map { it.copy() })

        fun apply(operations: List<PlannedUploadOperation>) {
            if (operations.isEmpty()) return
            val tasks = snapshot.tasks.associateBy(ExportTask::id).toMutableMap()
            operations.forEach { operation ->
                val collection = when (operation) {
                    is PlannedUploadOperation.Delete -> operation.collection
                    is PlannedUploadOperation.Set -> operation.collection
                }
                check(collection == TASKS_COLLECTION) {
                    "Unexpected collection in task-only fake backend: $collection"
                }
                when (operation) {
                    is PlannedUploadOperation.Delete -> tasks.remove(operation.documentId)
                    is PlannedUploadOperation.Set -> {
                        val task = operation.data as? ExportTask
                            ?: error("Expected ExportTask for ${operation.documentId}")
                        tasks[operation.documentId] = task
                    }
                }
            }
            snapshot = snapshot.copy(tasks = tasks.values.sortedBy(ExportTask::id))
            transactionCount += 1
        }

        fun taskIds(): Set<String> = snapshot.tasks.mapTo(mutableSetOf(), ExportTask::id)

        fun task(taskId: String): ExportTask? = snapshot.tasks.find { it.id == taskId }
    }

    private companion object {
        const val CLIENT_A = "a"
        const val CLIENT_B = "b"
        const val CURRENT_USER_ID = "admin"
        const val TASKS_COLLECTION = "tasks"
        const val TASK_DELETED = "task-deleted"
        const val TASK_SIBLING = "task-sibling"
        const val TASK_ONLY_A = "task-only-a"
        const val DATASTORE_DIRECTORY = "two-client-room-sync-datastore"
    }
}
