package com.familyhub.data.export

import android.content.Context
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.repository.AppreciationRepository
import com.familyhub.data.repository.EmergencyContactRepository
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.HouseRulesRepository
import com.familyhub.data.repository.PenaltyRepository
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.repository.RewardRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskCategoryRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.data.repository.TaskTemplateRepository
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class DataExportServiceMenuExportTest {

    private val context = mockk<Context>()
    private val memberRepository = mockk<FamilyMemberRepository>()
    private val taskRepository = mockk<TaskRepository>()
    private val shoppingRepository = mockk<ShoppingRepository>()
    private val eventRepository = mockk<FamilyEventRepository>()
    private val scoreRepository = mockk<ScoreRepository>()
    private val rewardRepository = mockk<RewardRepository>()
    private val taskRotationRepository = mockk<TaskRotationRepository>()
    private val houseRulesRepository = mockk<HouseRulesRepository>()
    private val taskTemplateRepository = mockk<TaskTemplateRepository>()
    private val taskCategoryRepository = mockk<TaskCategoryRepository>()
    private val appreciationRepository = mockk<AppreciationRepository>()
    private val familyGoalsRepository = mockk<FamilyGoalsRepository>()
    private val emergencyContactRepository = mockk<EmergencyContactRepository>()
    private val penaltyRepository = mockk<PenaltyRepository>()
    private val recipeRepository = mockk<RecipeRepository>()
    private val weeklyMenuDao = mockk<WeeklyMenuDao>()
    private val menuTemplateDao = mockk<MenuTemplateDao>()
    private val taskHistoryDao = mockk<TaskHistoryDao>()
    private val dailyAssignmentDao = mockk<DailyAssignmentDao>()
    private lateinit var service: DataExportService

    @Before
    fun setUp() {
        val tempDir = createTempDirectory(prefix = "fh_menu_export_").toFile()
        every { context.cacheDir } returns tempDir
        every { context.filesDir } returns File(tempDir, "files").apply { mkdirs() }

        coEvery { memberRepository.getAllMembers() } returns flowOf(emptyList())
        coEvery { taskRepository.getAllTasksSync() } returns emptyList()
        coEvery { shoppingRepository.observeLists() } returns flowOf(emptyList())
        coEvery { eventRepository.getAllEvents() } returns flowOf(emptyList())
        coEvery { scoreRepository.getCompletionLedger() } returns flowOf(emptyList())
        coEvery { scoreRepository.getAllAchievements() } returns flowOf(emptyList())
        coEvery { scoreRepository.getAllWallets() } returns flowOf(emptyList())
        coEvery { scoreRepository.getScoresFromWeek(any()) } returns flowOf(emptyList())
        coEvery { rewardRepository.getAllRewards() } returns flowOf(emptyList())
        coEvery { rewardRepository.getAllRequests() } returns flowOf(emptyList())
        coEvery { penaltyRepository.getAllPenalties() } returns flowOf(emptyList())
        coEvery { taskRotationRepository.getAllRotations() } returns flowOf(emptyList())
        coEvery { houseRulesRepository.getAllRules() } returns flowOf(emptyList())
        coEvery { taskTemplateRepository.getAllTemplates() } returns flowOf(emptyList())
        coEvery { taskCategoryRepository.getAllCategories() } returns flowOf(emptyList())
        coEvery { appreciationRepository.getAll() } returns flowOf(emptyList())
        coEvery { familyGoalsRepository.getAllGoalDetails() } returns flowOf(emptyList())
        coEvery { emergencyContactRepository.getAllContacts() } returns flowOf(emptyList())
        coEvery { recipeRepository.observeRecipes() } returns flowOf(emptyList())
        coEvery { weeklyMenuDao.getAllWithEntries() } returns emptyList()
        coEvery { taskHistoryDao.getRecentSnapshot(any()) } returns emptyList()
        coEvery { dailyAssignmentDao.getAllSnapshot() } returns emptyList()
        coEvery { menuTemplateDao.getAllWithEntries() } returns listOf(
            MenuTemplateWithEntries(
                template = MenuTemplateEntity(id = "tpl-1", name = "Weekday", createdAt = 1L),
                entries = listOf(
                    MenuTemplateEntryEntity(
                        id = "te-1",
                        templateId = "tpl-1",
                        dayOfWeek = 1,
                        mealType = "LUNCH",
                        recipeId = null,
                        customDish = null,
                        servings = 2,
                        assignedMemberId = null,
                        cookingTaskId = null,
                        prepTaskId = null
                    )
                )
            )
        )

        service = DataExportService(
            context = context,
            memberRepository = memberRepository,
            taskRepository = taskRepository,
            shoppingRepository = shoppingRepository,
            eventRepository = eventRepository,
            scoreRepository = scoreRepository,
            rewardRepository = rewardRepository,
            taskRotationRepository = taskRotationRepository,
            houseRulesRepository = houseRulesRepository,
            taskTemplateRepository = taskTemplateRepository,
            taskCategoryRepository = taskCategoryRepository,
            appreciationRepository = appreciationRepository,
            familyGoalsRepository = familyGoalsRepository,
            emergencyContactRepository = emergencyContactRepository,
            penaltyRepository = penaltyRepository,
            recipeRepository = recipeRepository,
            weeklyMenuDao = weeklyMenuDao,
            menuTemplateDao = menuTemplateDao,
            taskHistoryDao = taskHistoryDao,
            dailyAssignmentDao = dailyAssignmentDao
        )
    }

    @Test
    fun getExportData_includesMenuTemplatesFromDao() = runTest {
        val exportData = service.getExportData(currentUserId = "user-1")

        assertEquals(
            listOf(ExportMenuTemplate(id = "tpl-1", name = "Weekday", createdAt = 1L)),
            exportData.menuTemplates
        )
        assertEquals(
            listOf(
                ExportMenuTemplateEntry(
                    id = "te-1",
                    templateId = "tpl-1",
                    dayOfWeek = 1,
                    mealType = "LUNCH",
                    servings = 2
                )
            ),
            exportData.menuTemplateEntries
        )
    }

    @Test
    fun getExportData_preservesTaskReminderFields() = runTest {
        coEvery { taskRepository.getAllTasksSync() } returns listOf(
            Task(
                id = "task-1",
                title = "Task",
                description = null,
                createdByUserId = "user-1",
                assignedToUserId = "user-1",
                dueDate = 1_700_000_000_000L,
                dueDateHasTime = true,
                reminderMode = TaskReminderMode.AT_TIME,
                reminderMinutesOfDay = 22 * 60 + 15,
                repeatRule = RepeatRule.NONE,
                status = TaskStatus.TODO,
                createdAt = 1L,
            ),
        )

        val exportedTask = service.getExportData(currentUserId = "user-1").tasks.single()

        assertEquals(true, exportedTask.dueDateHasTime)
        assertEquals(TaskReminderMode.AT_TIME.name, exportedTask.reminderMode)
        assertEquals(22 * 60 + 15, exportedTask.reminderMinutesOfDay)
    }
}
