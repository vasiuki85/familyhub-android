package com.familyhub.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.PenaltyDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.PenaltyEntity
import com.familyhub.data.local.entity.RewardRequestEntity
import com.familyhub.data.local.entity.UserScoreEntity
import com.familyhub.data.local.entity.UserWalletEntity
import com.familyhub.domain.model.AccountingIdempotencyConflictException
import com.familyhub.domain.model.AccountingOverflowException
import com.familyhub.domain.model.AccountingTransitionForbiddenException
import com.familyhub.domain.model.AccountingTransitionInvalidException
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.PenaltyCancelResult
import com.familyhub.domain.model.PenaltyCreateResult
import com.familyhub.domain.model.RewardApprovalResult
import com.familyhub.domain.model.RewardDeclineResult
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.WeekUtils
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingMutationCoordinatorInstrumentedTest {

    private lateinit var database: FamilyHubDatabase
    private lateinit var penaltyCoordinator: PenaltyTransitionCoordinator
    private lateinit var rewardCoordinator: RewardRequestTransitionCoordinator
    private lateinit var penaltyDao: PenaltyDao
    private lateinit var walletDao: UserWalletDao
    private lateinit var scoreDao: UserScoreDao
    private lateinit var rewardRequestDao: RewardRequestDao

    private val parentId = "parent"
    private val adminId = "admin"
    private val childId = "child"
    private val pendingId = "pending-user"
    private val missingActorId = "ghost"
    private val penaltyId = "penalty-1"
    private val requestId = "request-1"
    private val createdAt = 1_704_067_200_000L
    private val weekStart = WeekUtils.getWeekStartMillis(createdAt)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        penaltyCoordinator = PenaltyTransitionCoordinator(database)
        rewardCoordinator = RewardRequestTransitionCoordinator(database)
        penaltyDao = database.penaltyDao()
        walletDao = database.userWalletDao()
        scoreDao = database.userScoreDao()
        rewardRequestDao = database.rewardRequestDao()

        runBlocking {
            database.familyMemberDao().upsert(
                FamilyMemberEntity(adminId, "Admin", "Admin", "#000", MemberRole.ADMIN)
            )
            database.familyMemberDao().upsert(
                FamilyMemberEntity(parentId, "Parent", "Parent", "#001", MemberRole.PARENT)
            )
            database.familyMemberDao().upsert(
                FamilyMemberEntity(childId, "Child", "Child", "#111", MemberRole.CHILD)
            )
            database.familyMemberDao().upsert(
                FamilyMemberEntity(pendingId, "Pending", "Pending", "#222", MemberRole.PENDING)
            )
            walletDao.upsert(UserWalletEntity(childId, 100, 0L))
            scoreDao.upsert(UserScoreEntity(childId, weekStart, 100, 0L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun penaltyCreate_success_setsIssuedByFromActor() {
        runBlocking {
        val before = AccountingBaselineVerifier.snapshot(database)
        val result = penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = "late",
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        assertThat(result).isInstanceOf(PenaltyCreateResult.Created::class.java)
        assertThat(penaltyDao.getById(penaltyId)?.issuedByUserId).isEqualTo(parentId)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(80)
        val after = AccountingBaselineVerifier.snapshot(database)
        assertBaselinePreservedOnPenaltyCreate(before, after, childId, weekStart)
        }
    }

    @Test
    fun penaltyCreate_sameIdIdempotent() {
        runBlocking {
        penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = "late",
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        val second = penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = "late",
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        assertThat(second).isInstanceOf(PenaltyCreateResult.IdempotentNoOp::class.java)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(80)
        }
    }

    @Test
    fun penaltyCreate_sameIdDifferentPayloadRejected() {
        runBlocking {
        penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = "late",
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        assertThrows(AccountingIdempotencyConflictException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = parentId,
                    amount = 30,
                    comment = "different",
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        assertThat(walletDao.get(childId)?.balance).isEqualTo(80)
        }
    }

    @Test
    fun penaltyCreate_childActorForbidden() {
        runBlocking {
        assertThrows(AccountingTransitionForbiddenException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = childId,
                    amount = 10,
                    comment = null,
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        assertThat(penaltyDao.getById(penaltyId)).isNull()
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun penaltyCreate_pendingActorForbidden() {
        runBlocking {
        assertThrows(AccountingTransitionForbiddenException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = pendingId,
                    amount = 10,
                    comment = null,
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        }
    }

    @Test
    fun penaltyCreate_missingActorForbidden() {
        runBlocking {
        assertThrows(AccountingTransitionForbiddenException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = missingActorId,
                    amount = 10,
                    comment = null,
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        }
    }

    @Test
    fun penaltyCreate_adminActorAllowed() {
        runBlocking {
        val result = penaltyCoordinator.create(
            userId = childId,
            actorUserId = adminId,
            amount = 10,
            comment = null,
            penaltyId = "penalty-admin",
            createdAt = createdAt
        )
        assertThat(result).isInstanceOf(PenaltyCreateResult.Created::class.java)
        }
    }

    @Test
    fun penaltyCreate_intMinValueRejected() {
        runBlocking {
        assertThrows(AccountingOverflowException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = parentId,
                    amount = Int.MIN_VALUE,
                    comment = null,
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        assertThat(penaltyDao.getById(penaltyId)).isNull()
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun penaltyCancel_legacyPositiveAmountRejected() {
        runBlocking {
        penaltyDao.upsert(
            PenaltyEntity(
                id = penaltyId,
                userId = childId,
                issuedByUserId = parentId,
                amount = 50,
                comment = null,
                createdAt = createdAt,
                canceled = false,
                canceledAt = null,
                originType = null,
                originId = null
            )
        )
        assertThrows(AccountingTransitionInvalidException::class.java) {
            runBlocking {
                penaltyCoordinator.cancel(penaltyId, parentId)
            }
        }
        assertThat(penaltyDao.getById(penaltyId)?.canceled).isFalse()
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun penaltyCancel_legacyIntMinValueRejected() {
        runBlocking {
        penaltyDao.upsert(
            PenaltyEntity(
                id = penaltyId,
                userId = childId,
                issuedByUserId = parentId,
                amount = Int.MIN_VALUE,
                comment = null,
                createdAt = createdAt,
                canceled = false,
                canceledAt = null,
                originType = null,
                originId = null
            )
        )
        assertThrows(AccountingTransitionInvalidException::class.java) {
            runBlocking {
                penaltyCoordinator.cancel(penaltyId, parentId)
            }
        }
        assertThat(penaltyDao.getById(penaltyId)?.canceled).isFalse()
        }
    }

    @Test
    fun penaltyCancel_success() {
        runBlocking {
        penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = null,
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        val before = AccountingBaselineVerifier.snapshot(database)
        val result = penaltyCoordinator.cancel(penaltyId, parentId, createdAt + 500L)
        assertThat(result).isEqualTo(PenaltyCancelResult.Canceled)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        val after = AccountingBaselineVerifier.snapshot(database)
        assertThat(AccountingBaselineVerifier.scoreBaseline(before, childId, weekStart))
            .isEqualTo(AccountingBaselineVerifier.scoreBaseline(after, childId, weekStart))
        }
    }

    @Test
    fun penaltyCancel_doubleCancelNoDoubleRefund() {
        runBlocking {
        penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = null,
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        penaltyCoordinator.cancel(penaltyId, parentId, createdAt + 100L)
        val second = penaltyCoordinator.cancel(penaltyId, parentId, createdAt + 200L)
        assertThat(second).isEqualTo(PenaltyCancelResult.IdempotentNoOp)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun rewardApprove_success() {
        runBlocking {
        insertPendingRequest(cost = 30)
        val before = AccountingBaselineVerifier.snapshot(database)
        val processedAt = 2_000L
        val result = rewardCoordinator.approve(requestId, parentId, processedAt)
        assertThat(result).isEqualTo(RewardApprovalResult.Success)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(70)
        assertThat(rewardRequestDao.getById(requestId)?.processedByUserId).isEqualTo(parentId)
        val after = AccountingBaselineVerifier.snapshot(database)
        assertThat(AccountingBaselineVerifier.walletBaseline(before, childId))
            .isEqualTo(AccountingBaselineVerifier.walletBaseline(after, childId))
        }
    }

    @Test
    fun rewardApprove_negativeCostRejected() {
        runBlocking {
        insertPendingRequest(cost = -30)
        val result = rewardCoordinator.approve(requestId, parentId)
        assertThat(result).isEqualTo(RewardApprovalResult.InvalidRewardCost)
        assertThat(rewardRequestDao.getById(requestId)?.status).isEqualTo(RewardRequestStatus.PENDING.name)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun rewardApprove_zeroCostAllowed() {
        runBlocking {
        insertPendingRequest(cost = 0)
        val result = rewardCoordinator.approve(requestId, parentId)
        assertThat(result).isEqualTo(RewardApprovalResult.Success)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
    }

    @Test
    fun rewardApprove_concurrentApproveDeductsOnce() {
        runBlocking {
        insertPendingRequest(cost = 30)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val io = Dispatchers.IO
        val first = async(io) {
            ready.countDown()
            ready.await()
            start.await()
            rewardCoordinator.approve(requestId, parentId, 1_000L)
        }
        val second = async(io) {
            ready.countDown()
            ready.await()
            start.await()
            rewardCoordinator.approve(requestId, parentId, 1_001L)
        }
        ready.await()
        start.countDown()
        val results = listOf(first.await(), second.await())
        assertThat(results.count { it == RewardApprovalResult.Success }).isEqualTo(1)
        assertThat(results.count { it == RewardApprovalResult.AlreadyProcessed }).isEqualTo(1)
        assertThat(walletDao.get(childId)?.balance).isEqualTo(70)
        }
    }

    @Test
    fun rewardConcurrentApproveAndDecline_oneTerminalStatus() {
        runBlocking {
        insertPendingRequest(cost = 30)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val io = Dispatchers.IO
        val approve = async(io) {
            ready.countDown()
            ready.await()
            start.await()
            rewardCoordinator.approve(requestId, parentId, 1_000L)
        }
        val decline = async(io) {
            ready.countDown()
            ready.await()
            start.await()
            rewardCoordinator.decline(requestId, parentId, 1_001L)
        }
        ready.await()
        start.countDown()
        val results = listOf(approve.await(), decline.await())
        assertThat(results.count { it == RewardApprovalResult.Success || it == RewardDeclineResult.Success }).isEqualTo(1)
        assertThat(results.count {
            it == RewardApprovalResult.AlreadyProcessed || it == RewardDeclineResult.AlreadyProcessed
        }).isEqualTo(1)
        val terminal = rewardRequestDao.getById(requestId)!!
        assertThat(terminal.status).isAnyOf(RewardRequestStatus.APPROVED.name, RewardRequestStatus.DECLINED.name)
        if (terminal.status == RewardRequestStatus.APPROVED.name) {
            assertThat(walletDao.get(childId)?.balance).isEqualTo(70)
        } else {
            assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        }
        }
    }

    @Test
    fun rewardApprove_childActorForbidden() {
        runBlocking {
        insertPendingRequest(cost = 10)
        assertThrows(AccountingTransitionForbiddenException::class.java) {
            runBlocking {
                rewardCoordinator.approve(requestId, childId)
            }
        }
        assertThat(rewardRequestDao.getById(requestId)?.status).isEqualTo(RewardRequestStatus.PENDING.name)
        }
    }

    @Test
    fun penaltyCreate_overflowRollsBack() {
        runBlocking {
        walletDao.upsert(UserWalletEntity(childId, Int.MIN_VALUE, 0L))
        scoreDao.upsert(UserScoreEntity(childId, weekStart, 0, 0L))
        assertThrows(AccountingOverflowException::class.java) {
            runBlocking {
                penaltyCoordinator.create(
                    userId = childId,
                    actorUserId = parentId,
                    amount = 1,
                    comment = null,
                    penaltyId = penaltyId,
                    createdAt = createdAt
                )
            }
        }
        assertThat(penaltyDao.getById(penaltyId)).isNull()
        assertThat(walletDao.get(childId)?.balance).isEqualTo(Int.MIN_VALUE)
        }
    }

    @Test
    fun penaltyCreate_failureRollsBackAllWrites() {
        runBlocking {
        installTrigger("abort_score_insert", "BEFORE INSERT ON user_scores BEGIN SELECT RAISE(ABORT, 'fail'); END")
        try {
            assertThrows(SQLiteException::class.java) {
                runBlocking {
                    penaltyCoordinator.create(
                        userId = childId,
                        actorUserId = parentId,
                        amount = 20,
                        comment = null,
                        penaltyId = penaltyId,
                        createdAt = createdAt
                    )
                }
            }
            assertThat(penaltyDao.getById(penaltyId)).isNull()
            assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
            assertThat(scoreDao.getUserScoreForWeek(childId, weekStart)?.score).isEqualTo(100)
        } finally {
            dropTrigger("abort_score_insert")
        }
        }
    }

    @Test
    fun penaltyCancel_failureRollsBackRefund() {
        runBlocking {
        penaltyCoordinator.create(
            userId = childId,
            actorUserId = parentId,
            amount = 20,
            comment = null,
            penaltyId = penaltyId,
            createdAt = createdAt
        )
        installTrigger("abort_score_on_cancel", "BEFORE INSERT ON user_scores BEGIN SELECT RAISE(ABORT, 'fail'); END")
        try {
            assertThrows(SQLiteException::class.java) {
                runBlocking {
                    penaltyCoordinator.cancel(penaltyId, parentId, createdAt + 500L)
                }
            }
            assertThat(penaltyDao.getById(penaltyId)?.canceled).isFalse()
            assertThat(walletDao.get(childId)?.balance).isEqualTo(80)
        } finally {
            dropTrigger("abort_score_on_cancel")
        }
        }
    }

    @Test
    fun rewardApprove_failureRollsBackWallet() {
        runBlocking {
        insertPendingRequest(cost = 30)
        installTrigger(
            "abort_request_replace",
            "BEFORE INSERT ON reward_requests WHEN NEW.status = 'APPROVED' BEGIN SELECT RAISE(ABORT, 'fail'); END"
        )
        try {
            assertThrows(SQLiteException::class.java) {
                runBlocking {
                    rewardCoordinator.approve(requestId, parentId, 5_000L)
                }
            }
            assertThat(rewardRequestDao.getById(requestId)?.status).isEqualTo(RewardRequestStatus.PENDING.name)
            assertThat(walletDao.get(childId)?.balance).isEqualTo(100)
        } finally {
            dropTrigger("abort_request_replace")
        }
        }
    }

    private fun assertBaselinePreservedOnPenaltyCreate(
        before: com.familyhub.data.export.ExportData,
        after: com.familyhub.data.export.ExportData,
        userId: String,
        weekStart: Long
    ) {
        assertThat(AccountingBaselineVerifier.scoreBaseline(before, userId, weekStart))
            .isEqualTo(AccountingBaselineVerifier.scoreBaseline(after, userId, weekStart))
        assertThat(AccountingBaselineVerifier.walletBaseline(before, userId))
            .isEqualTo(AccountingBaselineVerifier.walletBaseline(after, userId))
    }

    private suspend fun insertPendingRequest(cost: Int) {
        rewardRequestDao.upsert(
            RewardRequestEntity(
                id = requestId,
                rewardId = "reward-1",
                rewardTitle = "Treat",
                rewardCost = cost,
                requesterUserId = childId,
                note = null,
                status = RewardRequestStatus.PENDING.name,
                requestedAt = 500L,
                processedAt = null,
                processedByUserId = null
            )
        )
    }

    private fun installTrigger(name: String, sql: String) {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER $name $sql")
    }

    private fun dropTrigger(name: String) {
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS $name")
    }
}
