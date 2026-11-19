package com.familyhub.data.restore

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportTask
import com.familyhub.data.importer.DataImportService
import com.familyhub.data.importer.ImportRaceGuardException
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.sync.SyncChangeTracker
import com.familyhub.data.sync.SYNC_OUTBOX_DATABASE_CALLBACK
import com.familyhub.domain.model.MemberRole
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room replace + restore barrier semantics.
 * Mandatory in CI via verify_android_test_results.py.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedRestoreRoomInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var importService: DataImportService
    private lateinit var syncSettingsDataStore: SyncSettingsDataStore
    private lateinit var syncChangeTracker: SyncChangeTracker
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .addCallback(SYNC_OUTBOX_DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        syncSettingsDataStore = SyncSettingsDataStore(context)
        runBlocking { syncSettingsDataStore.resetForSignOut() }
        importService = buildImportService(database.taskDao())
    }

    @After
    fun tearDown() {
        runBlocking { syncSettingsDataStore.resetForSignOut() }
        if (::syncChangeTracker.isInitialized) syncChangeTracker.close()
        database.close()
    }

    @Test
    fun successfulReplace_replacesRepresentativeTablesInOneTransaction() {
        runBlocking {
        seedOldData()
        val snapshot = validatedSnapshot(newSnapshot())

        importService.replaceValidatedExport(snapshot.data, settleDelayMs = 0L)

        val members = database.familyMemberDao().getAll().first()
        val tasks = database.taskDao().getAllSync()
        assertThat(members).hasSize(1)
        assertThat(members.single().id).isEqualTo("new-m")
        assertThat(members.single().displayName).isEqualTo("Новый")
        assertThat(tasks).hasSize(1)
        assertThat(tasks.single().id).isEqualTo("new-t")
        assertThat(tasks.none { it.id == "old-t" }).isTrue()
        }
    }

    @Test
    fun successfulReplace_doesNotJournalImportedRows() {
        runBlocking {
            seedOldData()
            database.syncOutboxDao().deleteThrough(Long.MAX_VALUE)

            importService.replaceValidatedExport(newSnapshot(), settleDelayMs = 0L)

            assertThat(database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
        }
    }

    @Test
    fun userWriteWaitingOnImportTransaction_isJournaledAfterSuppressionEnds() {
        runBlocking {
            val realTaskDao = database.taskDao()
            val importReachedTaskWrite = CompletableDeferred<Unit>()
            val releaseImport = CompletableDeferred<Unit>()
            val blockingTaskDao = object : TaskDao by realTaskDao {
                override suspend fun upsertAll(tasks: List<TaskEntity>) {
                    importReachedTaskWrite.complete(Unit)
                    releaseImport.await()
                    realTaskDao.upsertAll(tasks)
                }
            }
            val blockingImport = buildImportService(blockingTaskDao)

            val importJob = async(Dispatchers.IO) {
                blockingImport.replaceValidatedExport(newSnapshot(), settleDelayMs = 0L)
            }
            withTimeout(5_000L) { importReachedTaskWrite.await() }

            val userWrite = async(Dispatchers.IO) {
                realTaskDao.upsert(taskEntity("user-during-import"))
            }
            delay(150L)
            assertThat(userWrite.isCompleted).isFalse()

            releaseImport.complete(Unit)
            importJob.await()
            userWrite.await()

            assertThat(
                database.syncOutboxDao().entriesThrough(Long.MAX_VALUE).map { it.entityId }
            ).containsExactly("user-during-import")
        }
    }

    @Test
    fun successfulReplace_replacesMenusAndTemplates() {
        runBlocking {
            seedOldMenuData()
            val snapshot = validatedSnapshot(menuSnapshot())

            importService.replaceValidatedExport(snapshot.data, settleDelayMs = 0L)

            val templates = database.menuTemplateDao().getAllWithEntries()
            val menus = database.weeklyMenuDao().getAllWithEntries()
            assertThat(templates).hasSize(1)
            assertThat(templates.single().template.id).isEqualTo("tpl-new")
            assertThat(templates.single().entries).hasSize(1)
            assertThat(templates.single().entries.single().id)
                .isEqualTo(com.familyhub.domain.utils.MenuSyncIds.menuTemplateEntryId("tpl-new", 1, "LUNCH"))
            assertThat(menus).hasSize(1)
            assertThat(menus.single().menu.id)
                .isEqualTo(com.familyhub.domain.utils.MenuSyncIds.weeklyMenuId(1_000L))
            assertThat(menus.single().entries).hasSize(1)
            assertThat(menus.single().entries.single().id)
                .isEqualTo(com.familyhub.domain.utils.MenuSyncIds.menuEntryId(1_000L, 2, "DINNER"))
            assertThat(templates.none { it.template.id == "tpl-old" }).isTrue()
            assertThat(menus.none { it.menu.id == "menu-old" }).isTrue()
        }
    }

    @Test
    fun menuTemplateInsertFailure_rollsBackEntireSnapshot() {
        runBlocking {
            seedOldMenuData()
            val realMenuTemplateDao = database.menuTemplateDao()
            val failingMenuTemplateDao = object : MenuTemplateDao by realMenuTemplateDao {
                override suspend fun insertEntries(entries: List<MenuTemplateEntryEntity>) {
                    throw IllegalStateException("forced menu template entry failure")
                }
            }
            val failingImport = buildImportService(
                taskDao = database.taskDao(),
                menuTemplateDao = failingMenuTemplateDao
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    failingImport.replaceValidatedExport(menuSnapshot(), settleDelayMs = 0L)
                }
            }

            val templates = database.menuTemplateDao().getAllWithEntries()
            val menus = database.weeklyMenuDao().getAllWithEntries()
            assertThat(templates.single().template.id).isEqualTo("tpl-old")
            assertThat(templates.single().entries.single().id).isEqualTo("te-old")
            assertThat(menus.single().menu.id).isEqualTo("menu-old")
            assertThat(menus.single().entries.single().id).isEqualTo("me-old")
        }
    }

    @Test
    fun artificialFailure_rollsBackEntireSnapshot() {
        runBlocking {
        seedOldData()
        val realTaskDao = database.taskDao()
        val failingTaskDao = object : TaskDao by realTaskDao {
            override suspend fun upsertAll(tasks: List<TaskEntity>) {
                throw IllegalStateException("forced task upsert failure")
            }
        }
        val failingImport = buildImportService(failingTaskDao)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                failingImport.replaceValidatedExport(newSnapshot(), settleDelayMs = 0L)
            }
        }

        val members = database.familyMemberDao().getAll().first()
        val tasks = database.taskDao().getAllSync()
        assertThat(members.map { it.id }).containsExactly("old-m")
        assertThat(tasks.map { it.id }).containsExactly("old-t")
        }
    }

    @Test
    fun applyValidatedRestore_afterSettleAndLateInvalidation_hasNoPendingChanges() {
        runBlocking {
        seedOldData()
        syncSettingsDataStore.setPendingChanges(true)

        importService.applyValidatedRestoreSnapshot(validatedSnapshot(newSnapshot()))

        // Settle already ran inside applyValidatedRestoreSnapshot; wait for any late delivery.
        delay(POST_RETURN_INVALIDATION_WAIT_MS)

        val settings = syncSettingsDataStore.settingsFlow.first()
        assertThat(settings.hasPendingChanges).isFalse()
        }
    }

    @Test
    fun beginBarrier_secondDifferentFingerprint_rejectedWithoutChangingFirst() {
        runBlocking {
        syncSettingsDataStore.beginRestoreBarrier(
            fingerprintSha256 = FINGERPRINT_A,
            sourceFamilyId = SOURCE_A
        )

        assertThrows(RestoreBarrierConflictException::class.java) {
            runBlocking {
                syncSettingsDataStore.beginRestoreBarrier(
                    fingerprintSha256 = FINGERPRINT_B,
                    sourceFamilyId = SOURCE_B
                )
            }
        }

        val settings = syncSettingsDataStore.settingsFlow.first()
        assertThat(settings.restoreInProgress).isTrue()
        assertThat(settings.restoreBarrierFingerprint).isEqualTo(FINGERPRINT_A)
        assertThat(settings.restoreBarrierSourceFamilyId).isEqualTo(SOURCE_A)
        assertThat(settings.suppressAuthMemberBootstrap).isTrue()
        }
    }

    @Test
    fun finalizeBarrier_wrongFingerprintOrSource_rejectedWithoutClearingBinding() {
        runBlocking {
        seedBindingAndTombstones()
        syncSettingsDataStore.beginRestoreBarrier(
            fingerprintSha256 = FINGERPRINT_A,
            sourceFamilyId = SOURCE_A
        )

        assertThrows(RestoreBarrierConflictException::class.java) {
            runBlocking {
                syncSettingsDataStore.finalizeRestoreBinding(
                    expectedFingerprint = FINGERPRINT_B,
                    sourceFamilyId = SOURCE_A,
                    suppressAuthMemberBootstrap = false
                )
            }
        }
        assertThrows(RestoreBarrierConflictException::class.java) {
            runBlocking {
                syncSettingsDataStore.finalizeRestoreBinding(
                    expectedFingerprint = FINGERPRINT_A,
                    sourceFamilyId = SOURCE_B,
                    suppressAuthMemberBootstrap = false
                )
            }
        }

        val settings = syncSettingsDataStore.settingsFlow.first()
        assertThat(settings.restoreInProgress).isTrue()
        assertThat(settings.restoreBarrierFingerprint).isEqualTo(FINGERPRINT_A)
        assertThat(settings.restoreBarrierSourceFamilyId).isEqualTo(SOURCE_A)
        assertThat(settings.accountId).isEqualTo(FAMILY_ID)
        assertThat(settings.deviceId).isEqualTo(DEVICE_ID)
        assertThat(settings.googleCalendarId).isEqualTo(CALENDAR_ID)
        assertThat(syncSettingsDataStore.getDeletedMembers(FAMILY_ID)).contains(TOMBSTONE_MEMBER)
        }
    }

    @Test
    fun finalizeBarrier_correctFingerprint_clearsBindingKeepsCalendar() {
        runBlocking {
        seedBindingAndTombstones()
        syncSettingsDataStore.beginRestoreBarrier(
            fingerprintSha256 = FINGERPRINT_A,
            sourceFamilyId = SOURCE_A
        )

        syncSettingsDataStore.finalizeRestoreBinding(
            expectedFingerprint = FINGERPRINT_A,
            sourceFamilyId = SOURCE_A,
            suppressAuthMemberBootstrap = true
        )

        val settings = syncSettingsDataStore.settingsFlow.first()
        assertThat(settings.restoreInProgress).isFalse()
        assertThat(settings.restoreBarrierFingerprint).isNull()
        assertThat(settings.restoreBarrierSourceFamilyId).isNull()
        assertThat(settings.accountId).isNull()
        assertThat(settings.deviceId).isNull()
        assertThat(settings.localDataFamilyId).isEqualTo(SOURCE_A)
        assertThat(settings.googleCalendarId).isEqualTo(CALENDAR_ID)
        assertThat(settings.suppressAuthMemberBootstrap).isTrue()
        assertThat(syncSettingsDataStore.getDeletedMembers(FAMILY_ID)).isEmpty()
        }
    }

    /** A write committed after the checkpoint must make full-clear import abort without data loss. */
    @Test
    fun syncImport_writeCommittedAfterCheckpoint_isPreserved() {
        runBlocking {
            // 1. Seed a member so the export is valid
            database.familyMemberDao().upsertAll(
                listOf(
                    FamilyMemberEntity(
                        id = "mem-1",
                        displayName = "Member",
                        roleLabel = "Parent",
                        avatarColor = "#AAAAAA",
                        role = MemberRole.PARENT
                    )
                )
            )

            val t1Id = "t1-before-capture"
            val t1 = taskEntity(t1Id).copy(title = "Before Capture")
            database.taskDao().upsert(t1)

            // Build an ExportData that represents the DB state at checkpoint —
            // only T1, no T2.
            val exportWithoutT2 = ExportData(
                members = listOf(
                    ExportMember(
                        id = "mem-1", displayName = "Member",
                        roleLabel = "Parent", avatarColor = "#AAAAAA", role = "PARENT"
                    )
                ),
                tasks = listOf(
                    ExportTask(
                        id = t1Id, title = "Before Capture",
                        createdByUserId = "mem-1", assignedToUserId = "mem-1",
                        repeatRule = "NONE", status = "TODO",
                        createdAt = 3L, taskType = "ROUTINE", priority = "MEDIUM"
                    )
                ),
                exportDate = "2026-08-08 00:00:00"
            )

            // 2. Capture sync snapshot — freezes checkpoint max sequence
            val captured = syncChangeTracker.captureSyncSnapshot { exportWithoutT2 }

            // 3. Deterministic post-capture write (same thread, no delay needed)
            val t2Id = "t2-post-capture"
            val t2Title = "Post-Capture Survivor"
            val t2 = taskEntity(t2Id).copy(title = t2Title)
            database.taskDao().upsert(t2)

            // 4. Guarded import must abort before clearing any table. Repository therefore does
            // not reach checkpoint acknowledgement or lastSync update in this cycle.
            val failure = runCatching {
                importService.replaceValidatedExport(
                    data = captured.value,
                    settleDelayMs = 0L,
                    expectedOutboxMaxSequence = captured.checkpoint.outboxMaxSequence,
                )
            }.exceptionOrNull()
            assertThat(failure).isInstanceOf(ImportRaceGuardException::class.java)
            val guardFailure = failure as ImportRaceGuardException
            assertThat(guardFailure.expectedOutboxMaxSequence)
                .isEqualTo(captured.checkpoint.outboxMaxSequence)
            assertThat(guardFailure.actualOutboxMaxSequence)
                .isGreaterThan(captured.checkpoint.outboxMaxSequence)

            // --- DESIRED INVARIANT ASSERTIONS ---

            // A. post-capture task T2 survives because import did not start.
            val allTasks = database.taskDao().getAllSync()
            val t2Survivor = allTasks.find { it.id == t2Id }
            assertThat(t2Survivor).isNotNull()
            assertThat(t2Survivor!!.title).isEqualTo(t2Title)

            // B. Neither the captured rows nor the newer row were acknowledged on abort.
            val outboxAfterAbort = database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)
            assertThat(outboxAfterAbort.any { it.entityId == t1Id }).isTrue()
            assertThat(outboxAfterAbort.any { it.entityId == t2Id }).isTrue()

            // C. suppression ignoreDepth balanced to 0 → tracking system is live
            //    Write T3 and verify SQL triggers fire (proves ignoreDepth = 0)
            val t3Id = "t3-depth-probe"
            database.taskDao().upsert(taskEntity(t3Id))
            val outboxAfterProbe = database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)
            assertThat(outboxAfterProbe.any { it.entityId == t3Id }).isTrue()
        }
    }

    @Test
    fun syncImport_unchangedOutboxAtCheckpoint_importsAndCanAcknowledge() {
        runBlocking {
            seedOldData()
            val captured = syncChangeTracker.captureSyncSnapshot { newSnapshot() }

            importService.replaceValidatedExport(
                data = captured.value,
                settleDelayMs = 0L,
                expectedOutboxMaxSequence = captured.checkpoint.outboxMaxSequence,
            )
            syncChangeTracker.markSynchronized(captured.checkpoint)

            assertThat(database.taskDao().getAllSync().map { it.id })
                .containsExactlyElementsIn(captured.value.tasks.map { it.id })
            assertThat(database.syncOutboxDao().entriesThrough(Long.MAX_VALUE)).isEmpty()
        }
    }

    private suspend fun seedBindingAndTombstones() {
        syncSettingsDataStore.setLocalDataFamilyId(FAMILY_ID)
        val enabled = syncSettingsDataStore.enableSyncForAccount(FAMILY_ID, DEVICE_ID)
        assertThat(enabled).isTrue()
        syncSettingsDataStore.setGoogleCalendarId(CALENDAR_ID)
        syncSettingsDataStore.addDeletedMember(FAMILY_ID, TOMBSTONE_MEMBER)
    }

    private fun buildImportService(
        taskDao: TaskDao,
        menuTemplateDao: MenuTemplateDao = database.menuTemplateDao()
    ): DataImportService {
        return DataImportService(
            database = database,
            memberDao = database.familyMemberDao(),
            taskDao = taskDao,
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
            menuTemplateDao = menuTemplateDao,
            weeklyMenuDao = database.weeklyMenuDao(),
            taskHistoryDao = database.taskHistoryDao(),
            dailyAssignmentDao = database.dailyAssignmentDao(),
            json = Json { ignoreUnknownKeys = true },
            syncChangeTracker = SyncChangeTracker(
                database = database,
                syncSettingsDataStore = syncSettingsDataStore
            ).also { syncChangeTracker = it }
        )
    }

    private fun validatedSnapshot(data: ExportData): ValidatedRestoreSnapshot =
        ValidatedRestoreSnapshot(
            payloadVersion = EncryptedBackupFormat.PAYLOAD_VERSION,
            createdAt = 1_700_000_000_000L,
            sourceFamilyId = null,
            data = data,
            counts = RestorePayloadValidator.countsOf(data)
        )

    private fun newSnapshot(): ExportData =
        ExportData(
            members = listOf(
                ExportMember(
                    id = "new-m",
                    displayName = "Новый",
                    roleLabel = "Parent",
                    avatarColor = "#00FF00",
                    role = "PARENT"
                )
            ),
            tasks = listOf(
                ExportTask(
                    id = "new-t",
                    title = "Новая задача",
                    createdByUserId = "new-m",
                    assignedToUserId = "new-m",
                    repeatRule = "NONE",
                    status = "TODO",
                    createdAt = 2L,
                    taskType = "ROUTINE",
                    priority = "HIGH"
                )
            ),
            exportDate = "2026-07-18 00:00:00"
        )

    private fun menuSnapshot(): ExportData =
        ExportData(
            members = listOf(
                ExportMember(
                    id = "new-m",
                    displayName = "New",
                    roleLabel = "Parent",
                    avatarColor = "#00FF00",
                    role = "PARENT"
                )
            ),
            menuTemplates = listOf(
                ExportMenuTemplate(id = "tpl-new", name = "Weekday", createdAt = 1L)
            ),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(
                    id = "te-new",
                    templateId = "tpl-new",
                    dayOfWeek = 1,
                    mealType = "LUNCH",
                    servings = 4
                )
            ),
            weeklyMenus = listOf(
                ExportWeeklyMenu(id = "menu-new", startDateMillis = 1_000L, createdAt = 1L)
            ),
            menuEntries = listOf(
                ExportMenuEntry(
                    id = "me-new",
                    menuId = "menu-new",
                    dayOfWeek = 2,
                    mealType = "DINNER",
                    servings = 2
                )
            )
        )

    private suspend fun seedOldMenuData() {
        database.menuTemplateDao().insertTemplate(
            MenuTemplateEntity(id = "tpl-old", name = "Old template", createdAt = 1L)
        )
        database.menuTemplateDao().insertEntries(
            listOf(
                MenuTemplateEntryEntity(
                    id = "te-old",
                    templateId = "tpl-old",
                    dayOfWeek = 0,
                    mealType = "BREAKFAST",
                    recipeId = null,
                    customDish = null,
                    servings = 2,
                    assignedMemberId = null,
                    cookingTaskId = null,
                    prepTaskId = null
                )
            )
        )
        database.weeklyMenuDao().upsertMenu(
            WeeklyMenuEntity(
                id = "menu-old",
                startDateMillis = 500L,
                isAutoGenerated = false,
                createdAt = 1L
            )
        )
        database.weeklyMenuDao().upsertEntries(
            listOf(
                com.familyhub.data.local.entity.MenuEntryEntity(
                    id = "me-old",
                    menuId = "menu-old",
                    dayOfWeek = 3,
                    mealType = "SNACK",
                    recipeId = null,
                    customDish = "Soup",
                    servings = 1,
                    assignedMemberId = null,
                    cookingTaskId = null,
                    prepTaskId = null
                )
            )
        )
    }

    private suspend fun seedOldData() {
        database.familyMemberDao().upsertAll(
            listOf(
                FamilyMemberEntity(
                    id = "old-m",
                    displayName = "Old",
                    roleLabel = "Admin",
                    avatarColor = "#FF0000",
                    role = MemberRole.ADMIN
                )
            )
        )
        database.taskDao().upsertAll(
            listOf(
                TaskEntity(
                    id = "old-t",
                    title = "Old task",
                    description = null,
                    createdByUserId = "old-m",
                    assignedToUserId = "old-m",
                    dueDate = null,
                    repeatRule = "NONE",
                    status = "TODO",
                    createdAt = 1L,
                    orderIndex = 0,
                    isPrivate = false,
                    taskType = "ROUTINE",
                    priority = "MEDIUM",
                    rewardPoints = null,
                    requiresApproval = false,
                    categoryId = null,
                    attachments = emptyList(),
                    comments = emptyList(),
                    completionCycle = 0L
                )
            )
        )
    }

    private fun taskEntity(id: String): TaskEntity = TaskEntity(
        id = id,
        title = "Task $id",
        description = null,
        createdByUserId = "member",
        assignedToUserId = "member",
        dueDate = null,
        repeatRule = "NONE",
        status = "TODO",
        createdAt = 3L,
    )

    companion object {
        private const val FINGERPRINT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val FINGERPRINT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        private const val SOURCE_A = "source-family-a"
        private const val SOURCE_B = "source-family-b"
        private const val FAMILY_ID = "family-binding-test"
        private const val DEVICE_ID = "device-binding-test"
        private const val CALENDAR_ID = "calendar-keep-me"
        private const val TOMBSTONE_MEMBER = "tombstone-member-1"
        private const val POST_RETURN_INVALIDATION_WAIT_MS = 1_000L
    }
}
