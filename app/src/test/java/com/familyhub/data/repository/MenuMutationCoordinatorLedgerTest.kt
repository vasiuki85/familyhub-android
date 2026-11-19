package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.DefaultMenuMutationClock
import com.familyhub.domain.utils.MenuSyncIds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MenuMutationCoordinatorLedgerTest {

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
    private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)
    private val templateId = "tpl-1"

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
        }
        coEvery { familyMemberDao.getById(actorUserId) } returns FamilyMemberEntity(
            id = actorUserId,
            displayName = "Admin",
            roleLabel = "Admin",
            avatarColor = "#000000",
            role = MemberRole.ADMIN,
        )
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
    fun deleteWeeklyMenu_recordsParentAndEntriesBeforeRoom() = runTest {
        val order = mutableListOf<String>()
        val ledger = orderLedger(order)
        coordinator = coordinatorWithLedger(ledger)
        val entry = entryEntity(day = 0)
        val current = WeeklyMenuWithEntries(menu = parentEntity(), entries = listOf(entry))
        coEvery { weeklyMenuDao.getById(canonicalParentId) } returnsMany listOf(current, current)
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            order.add("room")
            secondArg<suspend () -> Unit>().invoke()
        }

        coordinator.deleteWeeklyMenu(actorUserId, canonicalParentId)

        assertEquals(listOf("ledger", "room"), order)
        assertEquals(
            setOf(
                DeletedCloudDocumentCollections.WEEKLY_MENUS to canonicalParentId,
                DeletedCloudDocumentCollections.MENU_ENTRIES to entry.id,
            ),
            recordingLedger.recorded.toSet(),
        )
    }

    @Test
    fun deleteMenuTemplate_recordsParentAndEntriesBeforeRoom() = runTest {
        val order = mutableListOf<String>()
        val ledger = orderLedger(order)
        coordinator = coordinatorWithLedger(ledger)
        val entry = templateEntryEntity(day = 0)
        val current = MenuTemplateWithEntries(template = templateParent(), entries = listOf(entry))
        coEvery { menuTemplateDao.getById(templateId) } returnsMany listOf(current, current)
        coEvery { database.withTransaction<Unit>(any()) } coAnswers {
            order.add("room")
            secondArg<suspend () -> Unit>().invoke()
        }

        coordinator.deleteMenuTemplate(actorUserId, templateId)

        assertEquals(listOf("ledger", "room"), order)
        assertEquals(
            setOf(
                DeletedCloudDocumentCollections.MENU_TEMPLATES to templateId,
                DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES to entry.id,
            ),
            recordingLedger.recorded.toSet(),
        )
    }

    @Test
    fun replaceWeeklyMenu_recordsTombstonesOnlyForRemovedChildren() = runTest {
        val unchanged = entryEntity(customDish = "Keep", updatedAt = 100L)
        val removed = entryEntity(day = 1, meal = "LUNCH", customDish = "Gone", updatedAt = 400L)
        val current = WeeklyMenuWithEntries(
            menu = parentEntity(updatedAt = 200L),
            entries = listOf(unchanged, removed),
        )
        coEvery { weeklyMenuDao.getByStartDate(weekStart) } returnsMany listOf(current, current, current)

        coordinator.replaceWeeklyMenu(
            actorUserId,
            weeklyMenu(entries = listOf(menuEntry(dayOfWeek = 0, customDish = "Keep"))),
        )

        assertEquals(
            listOf(DeletedCloudDocumentCollections.MENU_ENTRIES to removed.id),
            recordingLedger.recorded,
        )
    }

    @Test
    fun replaceWeeklyMenu_exactNoOp_skipsDaoAndLedger() = runTest {
        val unchanged = entryEntity(customDish = "Keep", updatedAt = 100L)
        val current = WeeklyMenuWithEntries(menu = parentEntity(updatedAt = 200L), entries = listOf(unchanged))
        coEvery { weeklyMenuDao.getByStartDate(weekStart) } returns current

        coordinator.replaceWeeklyMenu(
            actorUserId,
            weeklyMenu(entries = listOf(menuEntry(dayOfWeek = 0, customDish = "Keep"))),
        )

        coVerify(exactly = 0) { weeklyMenuDao.upsertMenu(any()) }
        coVerify(exactly = 0) { weeklyMenuDao.upsertEntries(any()) }
        coVerify(exactly = 0) { database.withTransaction<Unit>(any()) }
        assertTrue(recordingLedger.recorded.isEmpty())
        // clearDeletions is allowed on exact no-op to drop stale tombstones.
    }

    private fun coordinatorWithLedger(ledger: CloudDeletionLedgerPort) = MenuMutationCoordinator(
        database,
        weeklyMenuDao,
        menuTemplateDao,
        dailyAssignmentDao,
        familyMemberDao,
        ledger,
        clock,
    )

    private fun orderLedger(order: MutableList<String>) = object : CloudDeletionLedgerPort {
        override suspend fun recordDeletions(entries: Collection<Pair<String, String>>) {
            order.add("ledger")
            recordingLedger.recorded.addAll(entries)
        }

        override suspend fun clearDeletions(entries: Collection<Pair<String, String>>) {
            recordingLedger.cleared.addAll(entries)
        }
    }

    private fun parentEntity(updatedAt: Long = 0L) = WeeklyMenuEntity(
        id = canonicalParentId,
        startDateMillis = weekStart,
        isAutoGenerated = false,
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    private fun templateParent(updatedAt: Long = 0L) = MenuTemplateEntity(
        id = templateId,
        name = "Week",
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    private fun entryEntity(
        day: Int = 0,
        meal: String = "BREAKFAST",
        customDish: String = "Toast",
        updatedAt: Long = 0L,
    ) = MenuEntryEntity(
        id = MenuSyncIds.menuEntryId(weekStart, day, meal),
        menuId = canonicalParentId,
        dayOfWeek = day,
        mealType = meal,
        recipeId = null,
        customDish = customDish,
        servings = 1,
        assignedMemberId = null,
        cookingTaskId = null,
        prepTaskId = null,
        updatedAt = updatedAt,
    )

    private fun templateEntryEntity(day: Int = 0) = MenuTemplateEntryEntity(
        id = MenuSyncIds.menuTemplateEntryId(templateId, day, "BREAKFAST"),
        templateId = templateId,
        dayOfWeek = day,
        mealType = "BREAKFAST",
        recipeId = null,
        customDish = "Toast",
        servings = 1,
        assignedMemberId = null,
        cookingTaskId = null,
        prepTaskId = null,
        updatedAt = 0L,
    )

    private fun weeklyMenu(entries: List<MenuEntry>) = WeeklyMenu(
        id = canonicalParentId,
        startDateMillis = weekStart,
        isAutoGenerated = false,
        createdAt = 1L,
        entries = entries,
    )

    private fun menuEntry(dayOfWeek: Int, customDish: String) = MenuEntry(
        id = MenuSyncIds.menuEntryId(weekStart, dayOfWeek, MealType.BREAKFAST.name),
        menuId = canonicalParentId,
        dayOfWeek = dayOfWeek,
        mealType = MealType.BREAKFAST,
        recipeId = null,
        customDish = customDish,
        servings = 1,
        assignedMemberId = null,
        cookingTaskId = null,
        prepTaskId = null,
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
