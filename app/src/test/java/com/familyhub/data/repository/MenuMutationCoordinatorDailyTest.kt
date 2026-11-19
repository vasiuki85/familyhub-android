package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.domain.model.DailyAssignment
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.DefaultMenuMutationClock
import com.familyhub.domain.utils.MenuMutationForbiddenException
import com.familyhub.domain.utils.MenuMutationStaleException
import com.familyhub.domain.utils.MenuSyncIds
import com.familyhub.domain.utils.MenuTimestampOverflowException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MenuMutationCoordinatorDailyTest {

    private val database: FamilyHubDatabase = mockk(relaxed = true)
    private val weeklyMenuDao: WeeklyMenuDao = mockk(relaxed = true)
    private val menuTemplateDao: MenuTemplateDao = mockk(relaxed = true)
    private val dailyAssignmentDao: DailyAssignmentDao = mockk(relaxed = true)
    private val familyMemberDao: FamilyMemberDao = mockk(relaxed = true)
    private val recordingLedger = RecordingLedger()
    private val clock = DefaultMenuMutationClock(wallClock = { 6_000L })

    private lateinit var coordinator: MenuMutationCoordinator

    private val actorUserId = "actor-admin"
    private val weekStart = 1_700_000_000_000L

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
        }
        stubAdminActor()
        coordinator = MenuMutationCoordinator(
            database,
            weeklyMenuDao,
            menuTemplateDao,
            dailyAssignmentDao,
            familyMemberDao,
            recordingLedger,
            clock,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun concurrentUpsertDay0AndDay1_preservesBothDays() = runTest {
        val store = mutableListOf<DailyAssignmentEntity>()
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } answers { store.toList() }
        coEvery { dailyAssignmentDao.upsertAssignment(any()) } answers {
            val entity = firstArg<DailyAssignmentEntity>()
            store.removeAll { it.id == entity.id }
            store.add(entity)
        }

        val day0 = assignment(dayOfWeek = 0, assignedMemberId = "a")
        val day1 = assignment(dayOfWeek = 1, assignedMemberId = "b")
        val first = async { coordinator.upsertDailyAssignment(actorUserId, day0) }
        val second = async { coordinator.upsertDailyAssignment(actorUserId, day1) }
        first.await()
        second.await()

        assertEquals(setOf(0, 1), store.map { it.dayOfWeek }.toSet())
        assertEquals(2, store.size)
    }

    @Test
    fun upsertDailyAssignment_samePayloadDifferentInputUpdatedAt_isNoOp() = runTest {
        val existing = entity(dayOfWeek = 2, updatedAt = 5_000L)
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } returns listOf(existing)

        val result = coordinator.upsertDailyAssignment(
            actorUserId,
            assignment(dayOfWeek = 2, updatedAt = 0L),
        )

        assertEquals(5_000L, result.updatedAt)
        coVerify(exactly = 0) { dailyAssignmentDao.upsertAssignment(any()) }
        coVerify(exactly = 0) { database.withTransaction<Unit>(any()) }
        assertTrue(recordingLedger.cleared.any { it.second == existing.id })
    }

    @Test
    fun upsertDailyAssignment_changedPayload_stampGreaterThanPersisted() = runTest {
        val existing = entity(dayOfWeek = 2, updatedAt = 5_000L, assignedMemberId = "old")
        val store = mutableListOf(existing)
        val upsertSlot = slot<DailyAssignmentEntity>()
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } answers { store.toList() }
        coEvery { dailyAssignmentDao.upsertAssignment(capture(upsertSlot)) } answers {
            store.clear()
            store.add(firstArg())
        }

        coordinator.upsertDailyAssignment(
            actorUserId,
            assignment(dayOfWeek = 2, assignedMemberId = "new"),
        )

        assertTrue(upsertSlot.captured.updatedAt > 5_000L)
    }

    @Test
    fun upsertDailyAssignment_usesCanonicalId() = runTest {
        val store = mutableListOf<DailyAssignmentEntity>()
        val upsertSlot = slot<DailyAssignmentEntity>()
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } answers { store.toList() }
        coEvery { dailyAssignmentDao.upsertAssignment(capture(upsertSlot)) } answers {
            store.clear()
            store.add(firstArg())
        }

        coordinator.upsertDailyAssignment(
            actorUserId,
            assignment(dayOfWeek = 3, id = "wrong-id"),
        )

        assertEquals(MenuSyncIds.dailyAssignmentId(weekStart, 3), upsertSlot.captured.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun upsertDailyAssignment_invalidDay_rejects() = runTest {
        coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 9))
    }

    @Test
    fun upsertDailyAssignment_persistedLongMaxValue_zeroLedgerAndDao() = runTest {
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } returns listOf(
            entity(dayOfWeek = 1, updatedAt = Long.MAX_VALUE),
        )

        val error = runCatching {
            coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 1, assignedMemberId = "new"))
        }.exceptionOrNull()

        assertTrue(error is MenuTimestampOverflowException)
        coVerify(exactly = 0) { dailyAssignmentDao.upsertAssignment(any()) }
        assertTrue(recordingLedger.recorded.isEmpty())
    }

    @Test
    fun upsertDailyAssignment_staleCohort_rollsBackWithoutDaoWrite() = runTest {
        val baseline = listOf(entity(dayOfWeek = 0))
        val stale = listOf(entity(dayOfWeek = 0), entity(dayOfWeek = 1))
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } returnsMany listOf(baseline, stale)

        val error = runCatching {
            coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 0, assignedMemberId = "new"))
        }.exceptionOrNull()

        assertTrue(error is MenuMutationStaleException)
        coVerify(exactly = 0) { dailyAssignmentDao.upsertAssignment(any()) }
    }

    @Test
    fun deleteDailyAssignmentsForWeek_recordsSingleBatchBeforeRoom() = runTest {
        val order = mutableListOf<String>()
        val ledger = object : CloudDeletionLedgerPort {
            override suspend fun recordDeletions(entries: Collection<Pair<String, String>>) {
                order.add("ledger")
                recordingLedger.recorded.addAll(entries)
            }

            override suspend fun clearDeletions(entries: Collection<Pair<String, String>>) {
                recordingLedger.cleared.addAll(entries)
            }
        }
        coordinator = MenuMutationCoordinator(
            database,
            weeklyMenuDao,
            menuTemplateDao,
            dailyAssignmentDao,
            familyMemberDao,
            ledger,
            clock,
        )
        val current = listOf(
            entity(dayOfWeek = 0, id = "da-0"),
            entity(dayOfWeek = 1, id = "da-1"),
        )
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } returnsMany listOf(current, current)
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            order.add("room")
            secondArg<suspend () -> Unit>().invoke()
        }

        coordinator.deleteDailyAssignmentsForWeek(actorUserId, weekStart)

        assertEquals(listOf("ledger", "room"), order)
        assertEquals(
            setOf("da-0", "da-1"),
            recordingLedger.recorded.map { it.second }.toSet(),
        )
    }

    @Test
    fun upsertDailyAssignment_afterDelete_clearsTombstone() = runTest {
        val store = mutableListOf<DailyAssignmentEntity>()
        val upsertSlot = slot<DailyAssignmentEntity>()
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } answers { store.toList() }
        coEvery { dailyAssignmentDao.upsertAssignment(capture(upsertSlot)) } answers {
            store.clear()
            store.add(firstArg())
        }

        coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 4))

        assertTrue(recordingLedger.cleared.any { it.second == upsertSlot.captured.id })
    }

    @Test
    fun childActor_zeroLedgerTransactionAndDao() = runTest {
        coEvery { familyMemberDao.getById(actorUserId) } returns member(MemberRole.CHILD)

        assertThrowsForbidden {
            coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 0))
        }

        coVerify(exactly = 0) { dailyAssignmentDao.upsertAssignment(any()) }
        coVerify(exactly = 0) { database.withTransaction<Unit>(any()) }
        assertTrue(recordingLedger.recorded.isEmpty())
    }

    @Test
    fun roleDriftBeforeTransaction_zeroRoomWrites() = runTest {
        coEvery { familyMemberDao.getById(actorUserId) } returnsMany listOf(
            member(MemberRole.ADMIN),
            member(MemberRole.CHILD),
        )
        coEvery { dailyAssignmentDao.getByWeek(weekStart) } returns emptyList()

        assertThrowsForbidden {
            coordinator.upsertDailyAssignment(actorUserId, assignment(dayOfWeek = 0, assignedMemberId = "new"))
        }

        coVerify(exactly = 0) { dailyAssignmentDao.upsertAssignment(any()) }
    }

    private fun assertThrowsForbidden(block: suspend () -> Unit) {
        val error = kotlinx.coroutines.runBlocking { runCatching { block() }.exceptionOrNull() }
        assertTrue(error is MenuMutationForbiddenException)
    }

    private fun stubAdminActor() {
        coEvery { familyMemberDao.getById(actorUserId) } returns member(MemberRole.ADMIN)
    }

    private fun member(role: MemberRole) = FamilyMemberEntity(
        id = actorUserId,
        displayName = "Actor",
        roleLabel = role.name,
        avatarColor = "#000000",
        role = role,
    )

    private fun assignment(
        dayOfWeek: Int,
        assignedMemberId: String? = "member-a",
        updatedAt: Long = 0L,
        id: String = MenuSyncIds.dailyAssignmentId(weekStart, dayOfWeek),
    ) = DailyAssignment(
        id = id,
        weekStartMillis = weekStart,
        dayOfWeek = dayOfWeek,
        assignedMemberId = assignedMemberId,
        updatedAt = updatedAt,
    )

    private fun entity(
        dayOfWeek: Int,
        assignedMemberId: String? = "member-a",
        updatedAt: Long = 0L,
        id: String = MenuSyncIds.dailyAssignmentId(weekStart, dayOfWeek),
    ) = DailyAssignmentEntity(
        id = id,
        weekStartMillis = weekStart,
        dayOfWeek = dayOfWeek,
        assignedMemberId = assignedMemberId,
        updatedAt = updatedAt,
    )

    private class RecordingLedger : CloudDeletionLedgerPort {
        val recorded = mutableListOf<Pair<String, String>>()
        val cleared = mutableListOf<Pair<String, String>>()

        override suspend fun recordDeletions(entries: Collection<Pair<String, String>>) {
            recorded.addAll(entries)
        }

        override suspend fun clearDeletions(entries: Collection<Pair<String, String>>) {
            cleared.addAll(entries)
        }
    }
}
