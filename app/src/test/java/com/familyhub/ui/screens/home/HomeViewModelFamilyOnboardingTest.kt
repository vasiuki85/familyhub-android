package com.familyhub.ui.screens.home

import android.content.Context
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFamilyOnboardingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val syncStateFlow = MutableStateFlow(SyncState(accountId = null, isHydrated = true))
    private val appSettingsFlow = MutableStateFlow(
        AppSettings(currentUserId = "user-1", familyOnboardingDismissed = false)
    )
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contentEmission_preservesFamilyOnboardingBanner() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.familyOnboarding.visible)

        tasksFlow.value = listOf(sampleTask())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.familyOnboarding.visible)
    }

    @Test
    fun unhydratedSyncState_hidesFamilyOnboardingUntilHydrated() = runTest {
        syncStateFlow.value = SyncState(accountId = null, isHydrated = false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.familyOnboarding.visible)

        syncStateFlow.value = SyncState(accountId = null, isHydrated = true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.familyOnboarding.visible)
    }

    @Test
    fun dismissFamilyOnboarding_keepsCompactConnectionEntry() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissFamilyOnboarding()
        advanceUntilIdle()

        appSettingsFlow.value = appSettingsFlow.value.copy(familyOnboardingDismissed = true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.familyOnboarding.visible)
        assertTrue(viewModel.state.value.familyOnboarding.compact)
    }

    private fun createViewModel(): HomeViewModel {
        val firebaseUser = mockk<FirebaseUser> {
            every { uid } returns "user-1"
        }
        val observeSyncStateUseCase = mockk<ObserveSyncStateUseCase> {
            every { this@mockk() } returns syncStateFlow
        }
        val appSettingsDataStore = mockk<AppSettingsDataStore>(relaxed = true) {
            every { settingsFlow } returns appSettingsFlow
            coEvery { setFamilyOnboardingDismissed(true) } coAnswers {
                appSettingsFlow.value = appSettingsFlow.value.copy(familyOnboardingDismissed = true)
            }
        }
        val memberRepository = mockk<FamilyMemberRepository>(relaxed = true) {
            every { getAllMembers() } returns flowOf(emptyList())
        }
        val taskRepository = mockk<TaskRepository>(relaxed = true) {
            every { getAllTasks() } returns tasksFlow
        }
        val eventRepository = mockk<FamilyEventRepository>(relaxed = true) {
            every { getAllEvents() } returns flowOf(emptyList())
        }
        val scoreRepository = mockk<ScoreRepository>(relaxed = true) {
            every { getScoresForWeek(any()) } returns flowOf(emptyList())
        }
        val shoppingRepository = mockk<com.familyhub.data.repository.ShoppingRepository>(relaxed = true) {
            every { observeLists() } returns flowOf(emptyList())
            every { observeItems(any()) } returns flowOf(emptyList())
        }
        val weeklyMenuRepository = mockk<com.familyhub.data.repository.WeeklyMenuRepository>(relaxed = true) {
            every { observeMenu(any()) } returns flowOf(null)
        }

        return HomeViewModel(
            taskRepository = taskRepository,
            eventRepository = eventRepository,
            memberRepository = memberRepository,
            scoreRepository = scoreRepository,
            shoppingRepository = shoppingRepository,
            weeklyMenuRepository = weeklyMenuRepository,
            appSettingsDataStore = appSettingsDataStore,
            completeTaskUseCase = mockk(relaxed = true),
            undoTaskCompletionUseCase = mockk(relaxed = true),
            deleteGoogleCalendarEventUseCase = mockk(relaxed = true),
            completionFeedbackProvider = mockk(relaxed = true),
            idGenerator = mockk(relaxed = true),
            eventReminderScheduler = mockk(relaxed = true),
            observeSyncStateUseCase = observeSyncStateUseCase,
            performSyncNowUseCase = mockk(relaxed = true),
            authenticatedMemberBootstrapCoordinator = mockk(relaxed = true) {
                coEvery { ensureAuthenticatedMemberExists() } returns
                    com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator.Outcome.Skipped
            },
            firebaseAuth = mockk<FirebaseAuth>(relaxed = true) {
                every { currentUser } returns firebaseUser
            },
            activationAnalytics = mockk<ActivationAnalytics>(relaxed = true),
            context = mockk<Context>(relaxed = true),
        )
    }

    private fun sampleTask() = Task(
        id = "task-1",
        title = "Dishes",
        description = null,
        createdByUserId = "user-1",
        assignedToUserId = "user-1",
        dueDate = null,
        status = TaskStatus.TODO,
        taskType = TaskType.ROUTINE,
        priority = TaskPriority.MEDIUM,
        repeatRule = RepeatRule.NONE,
        createdAt = 1L,
    )
}
