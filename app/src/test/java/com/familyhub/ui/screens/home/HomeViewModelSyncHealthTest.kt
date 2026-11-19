package com.familyhub.ui.screens.home

import android.content.Context
import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
class HomeViewModelSyncHealthTest {

    private val testDispatcher = StandardTestDispatcher()
    private val performSyncNowUseCase: PerformSyncNowUseCase = mockk(relaxed = true)
    private val syncStateFlow = MutableStateFlow(
        SyncState(accountId = "family-a", isEnabled = true, isHydrated = true)
    )
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val eventsFlow = MutableStateFlow(emptyList<com.familyhub.domain.model.FamilyEvent>())
    private val scoresFlow = MutableStateFlow(emptyList<com.familyhub.domain.model.UserScore>())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { performSyncNowUseCase() } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retrySync_doesNotInvokeUseCaseWhileSyncing() = runTest {
        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            isSyncing = true,
            lastError = "stale",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        advanceUntilIdle()

        coVerify(exactly = 0) { performSyncNowUseCase() }
    }

    @Test
    fun onSyncHealthAction_blocksRetryWhileSyncing() = runTest {
        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            isSyncing = true,
            lastError = "stale",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSyncHealthAction()
        advanceUntilIdle()

        coVerify(exactly = 0) { performSyncNowUseCase() }
    }

    @Test
    fun retrySync_invokesUseCaseWhenNotSyncing() = runTest {
        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            isSyncing = false,
            lastError = "network",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        advanceUntilIdle()

        coVerify(exactly = 1) { performSyncNowUseCase() }
    }

    @Test
    fun twoFastRetrySync_invokesUseCaseExactlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { performSyncNowUseCase() } coAnswers {
            gate.await()
            Result.success(Unit)
        }

        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            lastError = "network",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        viewModel.retrySync()
        advanceUntilIdle()

        coVerify(exactly = 1) { performSyncNowUseCase() }
        assertTrue(viewModel.state.value.syncRetryInFlight)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.syncRetryInFlight)
    }

    @Test
    fun suspendedUseCase_holdsRetryGuardUntilCompletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { performSyncNowUseCase() } coAnswers {
            gate.await()
            Result.success(Unit)
        }

        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            lastError = "network",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.syncRetryInFlight)
        assertFalse(viewModel.currentDisplaySyncHealth().actionEnabled)

        viewModel.retrySync()
        advanceUntilIdle()
        coVerify(exactly = 1) { performSyncNowUseCase() }

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.syncRetryInFlight)
    }

    @Test
    fun retrySync_allowsNewCallAfterPreviousCompletes() = runTest {
        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            lastError = "network",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        advanceUntilIdle()
        viewModel.retrySync()
        advanceUntilIdle()

        coVerify(exactly = 2) { performSyncNowUseCase() }
    }

    @Test
    fun contentEmission_preservesSyncHealthBanner() = runTest {
        syncStateFlow.value = SyncState(
            accountId = "family-a",
            isEnabled = true,
            lastError = "network",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(R.string.home_sync_health_error, viewModel.state.value.syncHealth.messageResId)

        tasksFlow.value = listOf(sampleTask())
        eventsFlow.value = listOf(
            com.familyhub.domain.model.FamilyEvent(
                id = "event-1",
                title = "Party",
                startDateTime = 1_700_000_100_000L,
                createdByUserId = "user-1",
                durationMinutes = 60,
                recurrence = EventRecurrence.NONE,
                reminderMinutesBefore = null,
            )
        )
        scoresFlow.value = listOf(
            com.familyhub.domain.model.UserScore(
                userId = "user-1",
                weekStartDate = 1L,
                score = 42,
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(R.string.home_sync_health_error, state.syncHealth.messageResId)
        assertEquals(1, state.myTasksToday.size)
        assertEquals(42, state.myScoreThisWeek)
    }

    private fun createViewModel(): HomeViewModel {
        val observeSyncStateUseCase = mockk<ObserveSyncStateUseCase> {
            every { this@mockk() } returns syncStateFlow
        }
        val appSettingsDataStore = mockk<AppSettingsDataStore>(relaxed = true) {
            every { settingsFlow } returns flowOf(AppSettings(currentUserId = "user-1"))
        }
        val memberRepository = mockk<FamilyMemberRepository>(relaxed = true) {
            every { getAllMembers() } returns flowOf(emptyList())
        }
        val taskRepository = mockk<TaskRepository>(relaxed = true) {
            every { getAllTasks() } returns tasksFlow
        }
        val eventRepository = mockk<FamilyEventRepository>(relaxed = true) {
            every { getAllEvents() } returns eventsFlow
        }
        val scoreRepository = mockk<ScoreRepository>(relaxed = true) {
            every { getScoresForWeek(any()) } returns scoresFlow
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
            performSyncNowUseCase = performSyncNowUseCase,
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

private fun HomeViewModel.currentDisplaySyncHealth(): SyncHealthUiModel {
    val current = state.value
    return if (current.syncRetryInFlight) {
        current.syncHealth.copy(actionEnabled = false)
    } else {
        current.syncHealth
    }
}
