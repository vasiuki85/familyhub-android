package com.familyhub.ui.screens.menu

import android.content.Context
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.datastore.MealPlanPreferencesDataStore
import com.familyhub.data.export.MealPlanShareService
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.notification.MealPlanNotificationManager
import com.familyhub.data.repository.DailyAssignmentRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MealPlanPreferences
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.MenuMutationForbiddenException
import com.familyhub.domain.utils.MenuMutationStaleException
import com.familyhub.domain.utils.WeekUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyMenuViewModelSyncPrepTasksTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsFlow = MutableStateFlow(AppSettings(currentUserId = USER_ID))
    private val membersFlow = MutableStateFlow(
        listOf(
            FamilyMember(
                id = USER_ID,
                displayName = "Parent",
                roleLabel = "Admin",
                avatarColor = "#000000",
                role = MemberRole.ADMIN,
            )
        )
    )

    private val weeklyMenuRepository: WeeklyMenuRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val familyMemberDao: FamilyMemberDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val longPrepRecipe = Recipe(
        id = "recipe-long",
        title = "Stew",
        description = "",
        servings = 4,
        prepTimeMinutes = 120,
        difficulty = RecipeDifficulty.MEDIUM,
        isFavorite = false,
        imageUrl = null,
        notes = "",
        tags = emptyList(),
        ingredients = emptyList(),
        steps = emptyList(),
        category = RecipeCategory.FIRST,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val shortPrepRecipe = Recipe(
        id = "recipe-short",
        title = "Toast",
        description = "",
        servings = 4,
        prepTimeMinutes = 20,
        difficulty = RecipeDifficulty.EASY,
        isFavorite = false,
        imageUrl = null,
        notes = "",
        tags = emptyList(),
        ingredients = emptyList(),
        steps = emptyList(),
        category = RecipeCategory.SALAD,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.getString(R.string.menu_read_only_role_hint) } returns "read-only"
        every { context.getString(R.string.error_unknown) } returns "error"
        every { context.getString(R.string.error_operation_failed) } returns "error"
        every { context.getString(R.string.error_menu_operation_failed) } returns "error"
        every { context.getString(R.string.error_no_current_user) } returns "no-user"
        coEvery { familyMemberDao.getById(USER_ID) } returns FamilyMemberEntity(
            id = USER_ID,
            displayName = "Parent",
            roleLabel = "Admin",
            avatarColor = "#000000",
            role = MemberRole.ADMIN,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setRecipeForMeal_plansPrepDeleteButDoesNotDeleteBeforeMenuCommit() = runTest {
        val prepTask = task(id = "prep-1")
        val menu = weeklyMenu(
            entries = listOf(
                menuEntry(recipeId = "recipe-long", prepTaskId = "prep-1"),
            ),
        )
        val viewModel = createViewModel(menu, listOf(longPrepRecipe, shortPrepRecipe))
        advanceUntilIdle()

        val callOrder = mutableListOf<String>()
        coEvery { weeklyMenuRepository.upsertMenu(any(), any()) } coAnswers {
            callOrder.add("upsert")
        }
        coEvery { taskRepository.getTaskById("prep-1") } returns prepTask
        coEvery { taskRepository.deleteTask(any()) } answers {
            callOrder.add("delete")
        }

        viewModel.setRecipeForMeal(0, MealType.BREAKFAST, "recipe-short")
        advanceUntilIdle()

        assertEquals(listOf("upsert", "delete"), callOrder)
        coVerify(exactly = 1) { taskRepository.deleteTask(prepTask) }
    }

    @Test
    fun setRecipeForMeal_forbiddenMenuCommit_skipsDeferredPrepDelete() = runTest {
        val menu = weeklyMenu(
            entries = listOf(menuEntry(recipeId = "recipe-long", prepTaskId = "prep-1")),
        )
        val viewModel = createViewModel(menu, listOf(longPrepRecipe, shortPrepRecipe))
        advanceUntilIdle()

        coEvery { familyMemberDao.getById(USER_ID) } returnsMany listOf(
            adminMember(),
            childMember(),
        )
        coEvery { weeklyMenuRepository.upsertMenu(any(), any()) } throws MenuMutationForbiddenException()

        viewModel.setRecipeForMeal(0, MealType.BREAKFAST, "recipe-short")
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
        assertEquals("read-only", viewModel.state.value.errorMessage)
    }

    @Test
    fun setRecipeForMeal_staleMenuCommit_skipsDeferredPrepDelete() = runTest {
        val menu = weeklyMenu(
            entries = listOf(menuEntry(recipeId = "recipe-long", prepTaskId = "prep-1")),
        )
        val viewModel = createViewModel(menu, listOf(longPrepRecipe, shortPrepRecipe))
        advanceUntilIdle()

        coEvery { weeklyMenuRepository.upsertMenu(any(), any()) } throws MenuMutationStaleException()
        coEvery { taskRepository.getTaskById("prep-1") } returns task(id = "prep-1")

        viewModel.setRecipeForMeal(0, MealType.BREAKFAST, "recipe-short")
        advanceUntilIdle()

        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
        assertEquals("error", viewModel.state.value.errorMessage)
    }

    @Test
    fun copyFromWeek_failureHandledOnceWithLocalizedError() = runTest {
        val viewModel = createViewModel(weeklyMenu(), emptyList())
        advanceUntilIdle()

        coEvery { weeklyMenuRepository.getMenu(any()) } returns weeklyMenu()
        coEvery { weeklyMenuRepository.upsertMenu(any(), any()) } throws IllegalStateException("dao boom")

        viewModel.copyFromWeek(WeekUtils.getCurrentWeekStartMillis() - 7)
        advanceUntilIdle()

        assertEquals("error", viewModel.state.value.errorMessage)
        coVerify(exactly = 1) { weeklyMenuRepository.upsertMenu(any(), any()) }
    }

    @Test
    fun freshAuthorizationDaoFailure_showsLocalizedErrorWithoutUncaughtException() = runTest {
        val viewModel = createViewModel(weeklyMenu(), emptyList())
        advanceUntilIdle()

        coEvery { familyMemberDao.getById(USER_ID) } throws IllegalStateException("dao boom")

        viewModel.clearMenu()
        advanceUntilIdle()

        assertEquals("error", viewModel.state.value.errorMessage)
        coVerify(exactly = 0) { weeklyMenuRepository.deleteMenu(any(), any()) }
    }

    private fun createViewModel(menu: WeeklyMenu, recipes: List<Recipe>): WeeklyMenuViewModel {
        val appSettingsDataStore = mockk<AppSettingsDataStore>()
        every { appSettingsDataStore.settingsFlow } returns settingsFlow
        val memberRepository = mockk<FamilyMemberRepository>()
        every { memberRepository.getAllMembers() } returns membersFlow
        val recipeRepository = mockk<RecipeRepository>()
        every { recipeRepository.observeRecipes() } returns flowOf(recipes)
        every { weeklyMenuRepository.observeMenu(any()) } returns flowOf(menu)
        every { weeklyMenuRepository.observeMenuTemplates() } returns flowOf(emptyList())
        every { weeklyMenuRepository.observeMenuHistory() } returns flowOf(emptyList())
        val mealPlanPreferences = mockk<MealPlanPreferencesDataStore>()
        every { mealPlanPreferences.preferencesFlow } returns flowOf(MealPlanPreferences())
        val dailyAssignmentRepository = mockk<DailyAssignmentRepository>()
        every { dailyAssignmentRepository.observeAssignments(any()) } returns flowOf(emptyList())
        val shoppingRepository = mockk<ShoppingRepository>(relaxed = true)
        val idGenerator = mockk<IdGenerator>()
        every { idGenerator.newId() } returns "generated-id"
        val mealPlanShareService = mockk<MealPlanShareService>(relaxed = true)
        val mealPlanNotificationManager = mockk<MealPlanNotificationManager>(relaxed = true)

        return WeeklyMenuViewModel(
            weeklyMenuRepository = weeklyMenuRepository,
            dailyAssignmentRepository = dailyAssignmentRepository,
            recipeRepository = recipeRepository,
            shoppingRepository = shoppingRepository,
            taskRepository = taskRepository,
            memberRepository = memberRepository,
            familyMemberDao = familyMemberDao,
            appSettingsDataStore = appSettingsDataStore,
            mealPlanPreferencesDataStore = mealPlanPreferences,
            idGenerator = idGenerator,
            mealPlanShareService = mealPlanShareService,
            mealPlanNotificationManager = mealPlanNotificationManager,
            context = context,
        )
    }

    private fun weeklyMenu(entries: List<MenuEntry> = emptyList()) = WeeklyMenu(
        id = "menu-1",
        startDateMillis = WeekUtils.getCurrentWeekStartMillis(),
        isAutoGenerated = false,
        createdAt = 1L,
        entries = entries,
    )

    private fun menuEntry(recipeId: String, prepTaskId: String?) = MenuEntry(
        id = "entry-1",
        menuId = "menu-1",
        dayOfWeek = 0,
        mealType = MealType.BREAKFAST,
        recipeId = recipeId,
        customDish = null,
        servings = 4,
        assignedMemberId = null,
        cookingTaskId = null,
        prepTaskId = prepTaskId,
    )

    private fun task(id: String) = Task(
        id = id,
        title = "Prep",
        description = null,
        createdByUserId = USER_ID,
        assignedToUserId = USER_ID,
        dueDate = 1L,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 1L,
        orderIndex = 0,
        isPrivate = false,
        taskType = TaskType.ROUTINE,
        priority = TaskPriority.MEDIUM,
        categoryId = null,
        attachments = emptyList(),
        comments = emptyList(),
    )

    private fun adminMember() = FamilyMemberEntity(
        id = USER_ID,
        displayName = "Parent",
        roleLabel = "Admin",
        avatarColor = "#000000",
        role = MemberRole.ADMIN,
    )

    private fun childMember() = FamilyMemberEntity(
        id = USER_ID,
        displayName = "Child",
        roleLabel = "Child",
        avatarColor = "#000000",
        role = MemberRole.CHILD,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
