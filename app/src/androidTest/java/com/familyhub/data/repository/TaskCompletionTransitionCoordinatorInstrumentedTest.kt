package com.familyhub.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.TaskEntity
import com.familyhub.data.local.entity.UserAchievementEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.AchievementType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskTransitionForbiddenException
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.utils.CompletionCycleIds
import com.familyhub.domain.utils.RepeatSuccessorIds
import com.familyhub.domain.utils.WeekUtils
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCompletionTransitionCoordinatorInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var coordinator: TaskCompletionTransitionCoordinator
    private lateinit var taskDao: TaskDao
    private lateinit var completionDao: TaskCompletionDao
    private lateinit var scoreDao: UserScoreDao
    private lateinit var walletDao: UserWalletDao
    private lateinit var achievementDao: UserAchievementDao

    private val parentId = "parent"
    private val childId = "child"
    private val otherChildId = "child-2"
    private val taskId = "task-1"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = TaskCompletionTransitionCoordinator(database)
        taskDao = database.taskDao()
        completionDao = database.taskCompletionDao()
        scoreDao = database.userScoreDao()
        walletDao = database.userWalletDao()
        achievementDao = database.userAchievementDao()

        runBlocking {
            listOf(
                FamilyMemberEntity(parentId, "Parent", "Parent", "#000", MemberRole.PARENT),
                FamilyMemberEntity(childId, "Child", "Child", "#111", MemberRole.CHILD),
                FamilyMemberEntity(otherChildId, "Other", "Other", "#222", MemberRole.CHILD)
            ).forEach { database.familyMemberDao().upsert(it) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun directCompletionAwardsOnce() = runBlocking {
        insertTask(rewardPoints = 10)
        val result = coordinator.complete(taskId, parentId, completionTime = 1_000L)
        assertThat(result.mutated).isTrue()
        assertThat(result.scoreImpact.totalDelta).isEqualTo(10)
        assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
    }

    @Test
    fun staleDoubleCompletionAwardsOnce() = runBlocking {
        insertTask(rewardPoints = 10)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val second = coordinator.complete(taskId, parentId, completionTime = 2_000L)
        assertThat(second.mutated).isFalse()
        assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
    }

    @Test
    fun secondCycleAfterUndoCreatesSecondCompletionAndAwardsAgain() = runBlocking {
        insertTask(rewardPoints = 10)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        coordinator.undo(taskId, parentId, undoTime = 1_500L)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(1L)
        val second = coordinator.complete(taskId, parentId, completionTime = 2_000L)
        assertThat(second.mutated).isTrue()
        assertThat(second.scoreImpact.totalDelta).isEqualTo(10)
        assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(10)
    }

    @Test
    fun undoAfterTwoCyclesReversesOnlyLatestCompletion() = runBlocking {
        insertTask(rewardPoints = 10)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        coordinator.undo(taskId, parentId, undoTime = 1_500L)
        coordinator.complete(taskId, parentId, completionTime = 2_000L)
        coordinator.undo(taskId, parentId, undoTime = 3_000L)
        assertThat(completionDao.getCompletionCount(childId)).isEqualTo(0)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(0)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(2L)
    }

    @Test
    fun completionUsesDeterministicIdForCycle() = runBlocking {
        insertTask(rewardPoints = 10)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val expectedId = CompletionCycleIds.deterministicId(taskId, 0L)
        assertThat(completionDao.getLatestCompletionForTask(taskId)?.id).isEqualTo(expectedId)
    }

    @Test
    fun repeatSuccessorStartsAtCycleZero() = runBlocking {
        insertTask(rewardPoints = 5, repeatRule = RepeatRule.DAILY, completionCycle = 3L)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val successorId = RepeatSuccessorIds.deterministicId(taskId)
        assertThat(taskDao.getById(successorId)?.completionCycle).isEqualTo(0L)
    }

    @Test
    fun childCannotCompleteForeignTask() = runBlocking {
        insertTask(rewardPoints = 5)
        try {
            coordinator.complete(taskId, otherChildId, completionTime = 1_000L)
            error("Expected forbidden")
        } catch (_: TaskTransitionForbiddenException) {
        }
    }

    @Test
    fun childCannotConfirm() = runBlocking {
        insertTask(rewardPoints = 5, requiresApproval = true)
        coordinator.complete(taskId, childId, completionTime = 1_000L)
        try {
            coordinator.confirm(taskId, childId, completionTime = 2_000L)
            error("Expected forbidden")
        } catch (_: TaskTransitionForbiddenException) {
        }
    }

    @Test
    fun eachRejectedApprovalAttemptAdvancesCompletionCycle() = runBlocking {
        insertTask(rewardPoints = 5, requiresApproval = true)

        val firstCompletion = coordinator.complete(taskId, childId, completionTime = 1_000L)
        assertThat(firstCompletion.newStatus).isEqualTo(TaskStatus.WAITING_FOR_APPROVAL)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(0L)

        coordinator.cancelWaiting(taskId, parentId)
        assertThat(taskDao.getById(taskId)?.status).isEqualTo(TaskStatus.TODO.name)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(1L)

        val secondCompletion = coordinator.complete(taskId, childId, completionTime = 2_000L)
        assertThat(secondCompletion.newStatus).isEqualTo(TaskStatus.WAITING_FOR_APPROVAL)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(1L)

        coordinator.cancelWaiting(taskId, parentId)
        assertThat(taskDao.getById(taskId)?.status).isEqualTo(TaskStatus.TODO.name)
        assertThat(taskDao.getById(taskId)?.completionCycle).isEqualTo(2L)
    }

    @Test
    fun repeatCompletionCreatesSingleSuccessor() = runBlocking {
        insertTask(rewardPoints = 5, repeatRule = RepeatRule.DAILY)
        val result = coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val successorId = RepeatSuccessorIds.deterministicId(taskId)
        assertThat(result.repeatCreated).isTrue()
        assertThat(taskDao.getById(successorId)).isNotNull()
        assertThat(taskDao.getAllSync().count { it.id == successorId }).isEqualTo(1)
    }

    @Test
    fun undoPreservesRepeatSuccessor() = runBlocking {
        insertTask(rewardPoints = 5, repeatRule = RepeatRule.DAILY)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val successorId = RepeatSuccessorIds.deterministicId(taskId)
        coordinator.undo(taskId, parentId, undoTime = 2_000L)
        assertThat(taskDao.getById(successorId)).isNotNull()
    }

    @Test
    fun redoDoesNotCreateDuplicateSuccessor() = runBlocking {
        insertTask(rewardPoints = 5, repeatRule = RepeatRule.DAILY)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        coordinator.undo(taskId, parentId, undoTime = 2_000L)
        val redo = coordinator.complete(taskId, parentId, completionTime = 3_000L)
        val successorId = RepeatSuccessorIds.deterministicId(taskId)
        assertThat(redo.repeatCreated).isFalse()
        assertThat(taskDao.getAllSync().count { it.id == successorId }).isEqualTo(1)
    }

    @Test
    fun missingHistoricalSuccessorIsNotRestoredOnRedo() = runBlocking {
        insertTask(rewardPoints = 5, repeatRule = RepeatRule.DAILY)
        coordinator.complete(taskId, parentId, completionTime = 1_000L)
        val successorId = RepeatSuccessorIds.deterministicId(taskId)
        taskDao.delete(taskDao.getById(successorId)!!)
        coordinator.undo(taskId, parentId, undoTime = 2_000L)
        val redo = coordinator.complete(taskId, parentId, completionTime = 3_000L)
        assertThat(redo.repeatCreated).isFalse()
        assertThat(taskDao.getById(successorId)).isNull()
    }

    @Test
    fun transactionFailureRollsBackAllTables() = runBlocking {
        insertTask(rewardPoints = 10, repeatRule = RepeatRule.DAILY)
        val sqlite = database.openHelper.writableDatabase
        installWalletAbortTrigger(sqlite)
        try {
            assertThrows(SQLiteException::class.java) {
                runBlocking {
                    coordinator.complete(taskId, parentId, completionTime = 1_000L)
                }
            }
            assertThat(taskDao.getById(taskId)?.status).isEqualTo(TaskStatus.TODO.name)
            assertThat(completionDao.getCompletionCount(childId)).isEqualTo(0)
            assertThat(scoreDao.getUserScoreForWeek(childId, WeekUtils.getWeekStartMillis(1_000L))).isNull()
            assertThat(walletDao.get(childId)).isNull()
            assertThat(achievementDao.getAchievementsForUser(childId).first()).isEmpty()
            assertThat(taskDao.getById(RepeatSuccessorIds.deterministicId(taskId))).isNull()
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS abort_wallet_insert")
        }
    }

    @Test
    fun firstTaskAchievementGrantedAndRevokedOnUndo() {
        runBlocking {
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)

            assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)

            coordinator.undo(taskId, parentId, undoTime = 1_500L)

            assertThat(achievementDao.getAchievementsForUser(childId).first()).isEmpty()
            assertThat(completionDao.getCompletionCount(childId)).isEqualTo(0)
        }
    }

    @Test
    fun firstTaskAchievementReGrantedOnRedoAfterUndo() {
        runBlocking {
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)
            coordinator.undo(taskId, parentId, undoTime = 1_500L)
            assertThat(achievementDao.getAchievementsForUser(childId).first()).isEmpty()

            val redo = coordinator.complete(taskId, parentId, completionTime = 2_000L)

            assertThat(redo.mutated).isTrue()
            assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)
        }
    }

    @Test
    fun firstTaskAchievementSurvivesWhenOneOfTwoCompletionsUndone() {
        runBlocking {
            insertTask(taskId = "task-1", rewardPoints = 10)
            insertTask(taskId = "task-2", rewardPoints = 10)
            coordinator.complete("task-1", parentId, completionTime = 1_000L)
            coordinator.complete("task-2", parentId, completionTime = 2_000L)

            assertThat(completionDao.getCompletionCount(childId)).isEqualTo(2)
            assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)

            coordinator.undo("task-2", parentId, undoTime = 2_500L)

            assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
            assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)
        }
    }

    @Test
    fun undoAchievementRevocationRollsBackWhenTransactionFails() {
        runBlocking {
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)
            assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)

            val sqlite = database.openHelper.writableDatabase
            installAchievementDeleteAbortTrigger(sqlite)
            try {
                assertThrows(SQLiteException::class.java) {
                    runBlocking {
                        coordinator.undo(taskId, parentId, undoTime = 1_500L)
                    }
                }
                assertThat(taskDao.getById(taskId)?.status).isEqualTo(TaskStatus.DONE.name)
                assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
                assertThat(achievementTypes(childId)).contains(AchievementType.FIRST_TASK.name)
            } finally {
                sqlite.execSQL("DROP TRIGGER IF EXISTS abort_achievement_delete")
            }
        }
    }

    @Test
    fun undoDoesNotDeleteOtherUserAchievements() {
        runBlocking {
            achievementDao.insert(
                UserAchievementEntity(
                    userId = otherChildId,
                    type = AchievementType.FIRST_TASK.name,
                    earnedAt = 500L,
                )
            )
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)
            coordinator.undo(taskId, parentId, undoTime = 1_500L)

            assertThat(achievementDao.getAchievementsForUser(childId).first()).isEmpty()
            assertThat(achievementTypes(otherChildId)).contains(AchievementType.FIRST_TASK.name)
        }
    }

    @Test
    fun perfectWeekGrantedWhenFirstTaskAlreadyEarned() {
        runBlocking {
            achievementDao.insert(
                UserAchievementEntity(
                    userId = childId,
                    type = AchievementType.FIRST_TASK.name,
                    earnedAt = 500L,
                )
            )
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)

            assertThat(achievementTypes(childId)).containsExactly(
                AchievementType.FIRST_TASK.name,
                AchievementType.PERFECT_WEEK.name,
            )
        }
    }

    @Test
    fun perfectWeekRevokedOnUndoWhenWeekNoLongerPerfect() {
        runBlocking {
            achievementDao.insert(
                UserAchievementEntity(
                    userId = childId,
                    type = AchievementType.FIRST_TASK.name,
                    earnedAt = 500L,
                )
            )
            insertTask(rewardPoints = 10)
            coordinator.complete(taskId, parentId, completionTime = 1_000L)
            assertThat(achievementTypes(childId)).contains(AchievementType.PERFECT_WEEK.name)

            coordinator.undo(taskId, parentId, undoTime = 1_500L)

            assertThat(achievementTypes(childId)).isEmpty()
        }
    }

    @Test
    fun perfectWeekSurvivesWhenOneOfTwoSameWeekCompletionsUndone() {
        runBlocking {
            achievementDao.insert(
                UserAchievementEntity(
                    userId = childId,
                    type = AchievementType.FIRST_TASK.name,
                    earnedAt = 500L,
                )
            )
            val weekStart = WeekUtils.getWeekStartMillis(1_000L)
            insertTask(taskId = "task-1", rewardPoints = 10)
            insertTask(taskId = "task-2", rewardPoints = 10)
            coordinator.complete("task-1", parentId, completionTime = weekStart + 1_000L)
            coordinator.complete("task-2", parentId, completionTime = weekStart + 2_000L)

            assertThat(achievementTypes(childId)).contains(AchievementType.PERFECT_WEEK.name)

            coordinator.undo("task-2", parentId, undoTime = weekStart + 2_500L)

            assertThat(completionDao.getCompletionCount(childId)).isEqualTo(1)
            assertThat(achievementTypes(childId)).contains(AchievementType.PERFECT_WEEK.name)
        }
    }

    private fun installWalletAbortTrigger(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER abort_wallet_insert
            BEFORE INSERT ON user_wallets
            BEGIN
                SELECT RAISE(ABORT, 'test abort');
            END
            """.trimIndent()
        )
    }

    private fun installAchievementDeleteAbortTrigger(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER abort_achievement_delete
            BEFORE DELETE ON user_achievements
            BEGIN
                SELECT RAISE(ABORT, 'test abort');
            END
            """.trimIndent()
        )
    }

    private suspend fun achievementTypes(userId: String): List<String> {
        return achievementDao.getAchievementsForUser(userId).first().map { it.type }
    }

    private suspend fun insertTask(
        taskId: String = this.taskId,
        rewardPoints: Int?,
        status: TaskStatus = TaskStatus.TODO,
        repeatRule: RepeatRule = RepeatRule.NONE,
        requiresApproval: Boolean = false,
        completionCycle: Long = 0L
    ) {
        taskDao.upsert(
            TaskEntity(
                id = taskId,
                title = "Test",
                description = null,
                createdByUserId = parentId,
                assignedToUserId = childId,
                dueDate = null,
                repeatRule = repeatRule.name,
                status = status.name,
                createdAt = 0L,
                orderIndex = 0,
                isPrivate = false,
                taskType = TaskType.ROUTINE.name,
                priority = "MEDIUM",
                rewardPoints = rewardPoints,
                requiresApproval = requiresApproval,
                completionCycle = completionCycle
            )
        )
    }
}
