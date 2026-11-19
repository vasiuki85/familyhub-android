package com.familyhub.ui.screens.home

import android.content.Context
import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.familyhub.domain.utils.WeekUtils
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
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
class HomeViewModelTileCountsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val itemsFlow = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val menuFlow = MutableStateFlow<WeeklyMenu?>(null)
    private val syncStateFlow = MutableStateFlow(com.familyhub.domain.model.SyncState())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observeTileCounts_startsWithNullCountsBeforeFirstEmission() = runTest {
        val viewModel = createViewModel()

        assertEquals(null, viewModel.state.value.shoppingItemsRemaining)
        assertEquals(null, viewModel.state.value.todayMealsCount)
    }

    @Test
    fun observeTileCounts_emitsShoppingAndFoodCountsWithoutBlockingMainContent() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(emptyList<com.familyhub.domain.model.Task>(), viewModel.state.value.myTasksToday)

        itemsFlow.value = listOf(
            shoppingItem("milk", isBought = false),
            shoppingItem("bread", isBought = true),
            shoppingItem("eggs", isBought = false),
        )
        menuFlow.value = weeklyMenuWithTodayMeals(2)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.shoppingItemsRemaining)
        assertEquals(2, viewModel.state.value.todayMealsCount)
    }

    private fun createViewModel(): HomeViewModel {
        val shoppingRepository = mockk<ShoppingRepository>(relaxed = true) {
            every { observeLists() } returns flowOf(
                listOf(
                    ShoppingList(
                        id = ShoppingRepository.DEFAULT_LIST_ID,
                        title = "Family",
                        colorHex = "#000000",
                        iconName = "cart",
                        createdAt = 1L,
                        isArchived = false,
                    ),
                ),
            )
            every { observeItems(ShoppingRepository.DEFAULT_LIST_ID) } returns itemsFlow
        }
        val weeklyMenuRepository = mockk<WeeklyMenuRepository>(relaxed = true) {
            every { observeMenu(any()) } returns menuFlow
        }

        return HomeViewModel(
            taskRepository = mockk<TaskRepository>(relaxed = true) {
                every { getAllTasks() } returns flowOf(emptyList())
            },
            eventRepository = mockk<FamilyEventRepository>(relaxed = true) {
                every { getAllEvents() } returns flowOf(emptyList())
            },
            memberRepository = mockk<FamilyMemberRepository>(relaxed = true) {
                every { getAllMembers() } returns flowOf(emptyList())
            },
            scoreRepository = mockk<ScoreRepository>(relaxed = true) {
                every { getScoresForWeek(any()) } returns flowOf(emptyList())
            },
            shoppingRepository = shoppingRepository,
            weeklyMenuRepository = weeklyMenuRepository,
            appSettingsDataStore = mockk<AppSettingsDataStore>(relaxed = true) {
                every { settingsFlow } returns flowOf(AppSettings(currentUserId = "user-1"))
            },
            completeTaskUseCase = mockk(relaxed = true),
            undoTaskCompletionUseCase = mockk(relaxed = true),
            deleteGoogleCalendarEventUseCase = mockk(relaxed = true),
            completionFeedbackProvider = mockk(relaxed = true),
            idGenerator = mockk(relaxed = true),
            eventReminderScheduler = mockk(relaxed = true),
            observeSyncStateUseCase = mockk<ObserveSyncStateUseCase>(relaxed = true) {
                every { this@mockk() } returns syncStateFlow
            },
            performSyncNowUseCase = mockk<PerformSyncNowUseCase>(relaxed = true),
            authenticatedMemberBootstrapCoordinator = mockk(relaxed = true) {
                coEvery { ensureAuthenticatedMemberExists() } returns
                    com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator.Outcome.Skipped
            },
            firebaseAuth = mockk<FirebaseAuth>(relaxed = true) {
                every { currentUser } returns null
            },
            activationAnalytics = mockk<ActivationAnalytics>(relaxed = true),
            context = mockk<Context>(relaxed = true),
        )
    }

    private fun shoppingItem(title: String, isBought: Boolean) = ShoppingItem(
        id = title,
        listId = ShoppingRepository.DEFAULT_LIST_ID,
        title = title,
        createdByUserId = "user-1",
        isBought = isBought,
        createdAt = 1L,
    )

    private fun weeklyMenuWithTodayMeals(mealCount: Int): WeeklyMenu {
        val weekStart = WeekUtils.getCurrentWeekStartMillis()
        val todayStart = com.familyhub.util.TimeUtils.getStartOfDay(com.familyhub.util.TimeUtils.now())
        val dayOffset = ((todayStart - weekStart) / (24 * 60 * 60 * 1000L)).toInt().coerceIn(0, 6)
        val entries = (1..mealCount).map { index ->
            MenuEntry(
                id = "meal-$index",
                menuId = "menu-1",
                dayOfWeek = dayOffset,
                mealType = MealType.entries[index % MealType.entries.size],
                recipeId = null,
                customDish = "Dish $index",
                servings = 2,
                assignedMemberId = null,
                cookingTaskId = null,
                prepTaskId = null,
            )
        }
        return WeeklyMenu(
            id = "menu-1",
            startDateMillis = weekStart,
            isAutoGenerated = false,
            createdAt = 1L,
            entries = entries,
        )
    }
}
