package com.familyhub.data.importer

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.export.ExportMember
import com.familyhub.data.export.ExportPenalty
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCompletion
import com.familyhub.data.local.db.dao.AppreciationDao
import com.familyhub.data.local.db.dao.EmergencyContactDao
import com.familyhub.data.local.db.dao.FamilyEventDao
import com.familyhub.data.local.db.dao.FamilyGoalDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.HouseRuleDao
import com.familyhub.data.local.db.dao.PenaltyDao
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.db.dao.RewardDao
import com.familyhub.data.local.db.dao.RewardRequestDao
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.local.db.dao.TaskCategoryDao
import com.familyhub.data.local.db.dao.TaskCompletionDao
import com.familyhub.data.local.db.dao.TaskDao
import com.familyhub.data.local.db.dao.TaskRotationDao
import com.familyhub.data.local.db.dao.TaskTemplateDao
import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.UserAchievementDao
import com.familyhub.data.local.db.dao.UserScoreDao
import com.familyhub.data.local.db.dao.UserWalletDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.RewardEntity
import com.familyhub.data.local.entity.ShoppingListEntity
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.TaskTransitionInvalidException
import com.familyhub.domain.utils.PenaltyValidationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import io.mockk.Ordering

class DataImportServiceTest {

    private val memberDao: FamilyMemberDao = mockk(relaxed = true)
    private val rewardDao: RewardDao = mockk(relaxed = true)
    private val shoppingItemDao: ShoppingItemDao = mockk(relaxed = true)
    private val taskDao: TaskDao = mockk(relaxed = true)
    private val taskCompletionDao: TaskCompletionDao = mockk(relaxed = true)
    private val penaltyDao: PenaltyDao = mockk(relaxed = true)

    private val weeklyMenuDao: WeeklyMenuDao = mockk(relaxed = true)
    private val menuTemplateDao: MenuTemplateDao = mockk(relaxed = true)

    private val service = DataImportService(
        database = mockk(relaxed = true),
        memberDao = memberDao,
        taskDao = taskDao,
        shoppingItemDao = shoppingItemDao,
        eventDao = mockk(relaxed = true),
        scoreDao = mockk(relaxed = true),
        taskCompletionDao = taskCompletionDao,
        achievementDao = mockk(relaxed = true),
        rewardDao = rewardDao,
        rewardRequestDao = mockk(relaxed = true),
        walletDao = mockk(relaxed = true),
        rotationDao = mockk(relaxed = true),
        houseRuleDao = mockk(relaxed = true),
        taskTemplateDao = mockk(relaxed = true),
        taskCategoryDao = mockk(relaxed = true),
        appreciationDao = mockk(relaxed = true),
        familyGoalDao = mockk(relaxed = true),
        emergencyContactDao = mockk(relaxed = true),
        penaltyDao = penaltyDao,
        recipeDao = mockk(relaxed = true),
        menuTemplateDao = menuTemplateDao,
        weeklyMenuDao = weeklyMenuDao,
        taskHistoryDao = mockk<TaskHistoryDao>(relaxed = true),
        dailyAssignmentDao = mockk<DailyAssignmentDao>(relaxed = true),
        json = Json { ignoreUnknownKeys = true },
        syncChangeTracker = mockk(relaxed = true)
    )

    @Test
    fun applyImportSnapshot_replacesMenuTemplatesInParentChildOrder() = runTest {
        val importedTemplateEntries = slot<List<MenuTemplateEntryEntity>>()
        coEvery { menuTemplateDao.insertEntries(capture(importedTemplateEntries)) } returns Unit

        service.applyImportSnapshot(
            ExportData(
                members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                menuTemplates = listOf(
                    ExportMenuTemplate(id = "tpl-1", name = "Weekday", createdAt = 1L)
                ),
                menuTemplateEntries = listOf(
                    ExportMenuTemplateEntry(
                        id = "te-1",
                        templateId = "tpl-1",
                        dayOfWeek = 1,
                        mealType = "LUNCH",
                        servings = 2
                    )
                ),
                weeklyMenus = listOf(
                    ExportWeeklyMenu(id = "menu-1", startDateMillis = 1_000L, createdAt = 1L)
                ),
                menuEntries = listOf(
                    ExportMenuEntry(
                        id = "me-1",
                        menuId = "menu-1",
                        dayOfWeek = 2,
                        mealType = "DINNER",
                        servings = 3
                    )
                )
            )
        )

        coVerify(ordering = Ordering.ORDERED) {
            menuTemplateDao.clearEntries()
            menuTemplateDao.clearTemplates()
            menuTemplateDao.insertTemplate(any())
            menuTemplateDao.insertEntries(any())
            weeklyMenuDao.clearEntries()
            weeklyMenuDao.clearMenus()
            weeklyMenuDao.upsertMenu(any())
            weeklyMenuDao.upsertEntries(any())
        }
        assertEquals("tpl-1", importedTemplateEntries.captured.single().templateId)
    }

    @Test
    fun importData_invalidMenuMeal_doesNotTouchDaos() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.importData(
                    ExportData(
                        members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                        weeklyMenus = listOf(
                            ExportWeeklyMenu(id = "m", startDateMillis = 1L, createdAt = 1L)
                        ),
                        menuEntries = listOf(
                            ExportMenuEntry(
                                id = "e",
                                menuId = "m",
                                dayOfWeek = 0,
                                mealType = "NOPE",
                                servings = 1
                            )
                        )
                    )
                )
            }
        }
        coVerify(exactly = 0) { weeklyMenuDao.clearMenus() }
        coVerify(exactly = 0) { menuTemplateDao.clearTemplates() }
    }

    @Test
    fun importData_orphanMenuEntry_doesNotTouchDaos() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.importData(
                    ExportData(
                        members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                        menuEntries = listOf(
                            ExportMenuEntry(
                                id = "orphan",
                                menuId = "missing-menu",
                                dayOfWeek = 0,
                                mealType = "BREAKFAST",
                                servings = 1
                            )
                        )
                    )
                )
            }
        }
        coVerify(exactly = 0) { weeklyMenuDao.clearMenus() }
        coVerify(exactly = 0) { weeklyMenuDao.clearEntries() }
        coVerify(exactly = 0) { menuTemplateDao.clearTemplates() }
    }

    @Test
    fun importData_orphanTemplateEntry_doesNotTouchDaos() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.importData(
                    ExportData(
                        members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                        menuTemplateEntries = listOf(
                            ExportMenuTemplateEntry(
                                id = "orphan",
                                templateId = "missing-tpl",
                                dayOfWeek = 0,
                                mealType = "LUNCH",
                                servings = 1
                            )
                        )
                    )
                )
            }
        }
        coVerify(exactly = 0) { menuTemplateDao.clearTemplates() }
        coVerify(exactly = 0) { menuTemplateDao.clearEntries() }
    }

    @Test
    fun importData_updatedAtBelowZero_doesNotTouchDaos() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.importData(
                    ExportData(
                        members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                        weeklyMenus = listOf(
                            ExportWeeklyMenu(
                                id = "m",
                                startDateMillis = 1L,
                                createdAt = 1L,
                                updatedAt = -1L
                            )
                        )
                    )
                )
            }
        }
        coVerify(exactly = 0) { weeklyMenuDao.clearMenus() }
        coVerify(exactly = 0) { menuTemplateDao.clearTemplates() }
    }

    @Test
    fun applyImportSnapshot_preservesRolesWithoutCreatingAdmin() = runTest {
        val data = ExportData(
            members = listOf(
                exportMember("parent-1", MemberRole.PARENT),
                exportMember("child-1", MemberRole.CHILD)
            )
        )
        val importedMembers = slot<List<FamilyMemberEntity>>()
        coEvery { memberDao.upsertAll(capture(importedMembers)) } returns Unit

        service.applyImportSnapshot(data)

        assertEquals(2, importedMembers.captured.size)
        assertEquals(MemberRole.PARENT, importedMembers.captured[0].role)
        assertEquals(MemberRole.CHILD, importedMembers.captured[1].role)
        assertFalse(importedMembers.captured.any { it.role == MemberRole.ADMIN })
    }

    @Test
    fun applyImportSnapshot_clearsRewardsWhenSnapshotIsEmpty() = runTest {
        val importedRewards = slot<List<RewardEntity>>()
        coEvery { rewardDao.upsertAll(capture(importedRewards)) } returns Unit

        service.applyImportSnapshot(
            ExportData(
                members = listOf(exportMember("parent-1", MemberRole.PARENT)),
                rewards = emptyList()
            )
        )

        coVerify(exactly = 1) { rewardDao.clear() }
        assertEquals(0, importedRewards.captured.size)
    }

    @Test
    fun applyImportSnapshot_usesDirectMenuDaoWrites_notCoordinatorPath() = runTest {
        val data = ExportData(
            members = listOf(exportMember("parent-1", MemberRole.PARENT)),
            weeklyMenus = listOf(
                ExportWeeklyMenu(
                    id = "menu-1",
                    startDateMillis = 1_700_000_000_000L,
                    createdAt = 1L,
                    updatedAt = 10L,
                ),
            ),
            menuEntries = listOf(
                ExportMenuEntry(
                    id = "entry-1",
                    menuId = "menu-1",
                    dayOfWeek = 0,
                    mealType = "BREAKFAST",
                    servings = 1,
                    updatedAt = 10L,
                ),
            ),
        )

        service.applyImportSnapshot(data)

        coVerify(exactly = 1) { weeklyMenuDao.upsertMenu(any()) }
        coVerify(exactly = 1) { weeklyMenuDao.upsertEntries(any()) }
        coVerify(exactly = 0) { weeklyMenuDao.upsertMenuWithEntries(any()) }
    }

    @Test
    fun applyImportSnapshot_doesNotCreateDefaultShoppingListForEmptySnapshot() = runTest {
        val importedLists = slot<List<ShoppingListEntity>>()
        coEvery { shoppingItemDao.upsertLists(capture(importedLists)) } returns Unit

        service.applyImportSnapshot(ExportData())

        coVerify(ordering = Ordering.ORDERED) {
            shoppingItemDao.clearLists()
            shoppingItemDao.upsertLists(emptyList())
        }
        assertEquals(0, importedLists.captured.size)
    }

    @Test
    fun importData_negativeTaskCycle_doesNotTouchDaos() = runTest {
        val data = ExportData(tasks = listOf(exportTask(completionCycle = -1L)))

        assertThrows(TaskTransitionInvalidException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { taskDao.clear() }
        coVerify(exactly = 0) { taskCompletionDao.clear() }
        coVerify(exactly = 0) { memberDao.clear() }
    }

    @Test
    fun importData_negativeCompletionCycle_doesNotTouchDaos() = runTest {
        val data = ExportData(
            taskCompletions = listOf(exportCompletion(cycle = -1L))
        )

        assertThrows(TaskTransitionInvalidException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { taskCompletionDao.clear() }
        coVerify(exactly = 0) { taskDao.clear() }
    }

    @Test
    fun importData_conflictingDuplicateLogicalCycle_doesNotTouchDaos() = runTest {
        val data = ExportData(
            taskCompletions = listOf(
                exportCompletion(id = "a", cycle = 0L, updatedAt = 100L),
                exportCompletion(id = "b", cycle = 0L, updatedAt = 200L)
            )
        )

        assertThrows(ImportValidationException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { taskCompletionDao.clear() }
        coVerify(exactly = 0) { taskDao.clear() }
    }

    @Test
    fun importData_validLegacyNullCompletions_passesPreflight() = runTest {
        val data = ExportData(
            members = listOf(exportMember("parent-1", MemberRole.PARENT)),
            taskCompletions = listOf(
                exportCompletion(id = "legacy-a", cycle = null, updatedAt = 100L),
                exportCompletion(id = "legacy-b", cycle = null, updatedAt = 200L)
            )
        )
        coEvery { memberDao.upsertAll(any()) } returns Unit
        coEvery { taskCompletionDao.insertAll(any()) } returns Unit

        service.applyImportSnapshot(ImportCycleValidation.validateAndNormalize(data))

        coVerify(exactly = 1) { taskCompletionDao.clear() }
        coVerify(exactly = 1) { taskCompletionDao.insertAll(any()) }
    }

    @Test
    fun importData_zeroPenaltyAmount_doesNotTouchDaos() = runTest {
        val data = ExportData(penalties = listOf(exportPenalty(amount = 0)))

        assertThrows(PenaltyValidationException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { penaltyDao.clear() }
        coVerify(exactly = 0) { penaltyDao.upsertAll(any()) }
        coVerify(exactly = 0) { memberDao.clear() }
    }

    @Test
    fun importData_positivePenaltyAmount_doesNotTouchDaos() = runTest {
        val data = ExportData(penalties = listOf(exportPenalty(amount = 5)))

        assertThrows(PenaltyValidationException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { penaltyDao.clear() }
        coVerify(exactly = 0) { penaltyDao.upsertAll(any()) }
    }

    @Test
    fun importData_activePenaltyWithCanceledAt_doesNotTouchDaos() = runTest {
        val data = ExportData(
            penalties = listOf(exportPenalty(canceled = false, canceledAt = 1L))
        )

        assertThrows(PenaltyValidationException::class.java) {
            runBlocking { service.importData(data) }
        }

        coVerify(exactly = 0) { penaltyDao.clear() }
        coVerify(exactly = 0) { penaltyDao.upsertAll(any()) }
    }

    private fun exportPenalty(
        amount: Int = -10,
        canceled: Boolean = false,
        canceledAt: Long? = null
    ) = ExportPenalty(
        id = "penalty-1",
        userId = "child-1",
        issuedByUserId = "parent-1",
        amount = amount,
        createdAt = 1L,
        canceled = canceled,
        canceledAt = canceledAt
    )

    private fun exportTask(completionCycle: Long) = ExportTask(
        id = "task-1",
        title = "Task",
        createdByUserId = "parent",
        assignedToUserId = "child",
        status = "TODO",
        completionCycle = completionCycle
    )

    private fun exportCompletion(
        id: String = "completion-1",
        cycle: Long?,
        updatedAt: Long = 1L
    ) = ExportTaskCompletion(
        id = id,
        taskId = "task-1",
        userId = "child",
        taskType = "ROUTINE",
        completionTime = updatedAt,
        weekStartDate = 1L,
        basePoints = 10,
        totalPoints = 10,
        updatedAt = updatedAt,
        completionCycle = cycle
    )

    private fun exportMember(id: String, role: MemberRole) = ExportMember(
        id = id,
        displayName = id,
        roleLabel = role.name,
        avatarColor = "#2196F3",
        role = role.name
    )
}
