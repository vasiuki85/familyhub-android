package com.familyhub.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.analytics.ActivationAnalytics
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.AuthenticatedMemberBootstrapCoordinator
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ScoreRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.usecase.CompleteTaskUseCase
import com.familyhub.domain.usecase.UndoTaskCompletionUseCase
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.SyncState
import com.familyhub.domain.model.TaskTransitionForbiddenException
import com.familyhub.domain.usecase.ObserveSyncStateUseCase
import com.familyhub.domain.usecase.PerformSyncNowUseCase
import com.google.firebase.auth.FirebaseAuth
import com.familyhub.domain.usecase.DeleteGoogleCalendarEventUseCase
import com.familyhub.domain.usecase.TaskCompletionFeedback
import com.familyhub.domain.usecase.TaskCompletionFeedbackProvider
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.domain.utils.getNextOccurrence
import com.familyhub.domain.model.FamilyMember
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.domain.utils.filterByVisibility
import com.familyhub.util.TimeUtils
import com.familyhub.widget.FamilyHubWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal data class HomeContentSnapshot(
    val isLoading: Boolean = false,
    val myTasksToday: List<Task> = emptyList(),
    val upcomingEvents: List<EventOccurrence> = emptyList(),
    val myScoreThisWeek: Int = 0,
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val members: List<FamilyMember> = emptyList(),
    val hasAnyTask: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val myTasksToday: List<Task> = emptyList(),
    val upcomingEvents: List<EventOccurrence> = emptyList(),
    val myScoreThisWeek: Int = 0,
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val members: List<FamilyMember> = emptyList(),
    val hasAnyTask: Boolean = false,
    val errorMessage: String? = null,
    val completionFeedback: TaskCompletionFeedback? = null,
    val syncHealth: SyncHealthUiModel = SyncHealthUiModel.hidden(),
    val syncState: SyncState = SyncState(),
    val syncRetryInFlight: Boolean = false,
    val familyOnboarding: FamilyOnboardingUiModel = FamilyOnboardingUiModel.hidden(),
    val familyActivation: FamilyActivationChecklist = FamilyActivationChecklistMapper.map(
        isHydrated = false,
        accountId = null,
        currentUserId = "",
        currentUserRole = MemberRole.PENDING,
        members = emptyList(),
        hasAnyTask = false,
    ),
    val shoppingItemsRemaining: Int? = null,
    val todayMealsCount: Int? = null,
) {
    val canMutateFamily: Boolean
        get() = currentUserRole != MemberRole.PENDING
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val eventRepository: FamilyEventRepository,
    private val memberRepository: FamilyMemberRepository,
    private val scoreRepository: ScoreRepository,
    private val shoppingRepository: ShoppingRepository,
    private val weeklyMenuRepository: WeeklyMenuRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val undoTaskCompletionUseCase: UndoTaskCompletionUseCase,
    private val deleteGoogleCalendarEventUseCase: DeleteGoogleCalendarEventUseCase,
    private val completionFeedbackProvider: TaskCompletionFeedbackProvider,
    private val idGenerator: IdGenerator,
    private val eventReminderScheduler: EventReminderScheduler,
    private val observeSyncStateUseCase: ObserveSyncStateUseCase,
    private val performSyncNowUseCase: PerformSyncNowUseCase,
    private val authenticatedMemberBootstrapCoordinator: AuthenticatedMemberBootstrapCoordinator,
    private val firebaseAuth: FirebaseAuth,
    private val activationAnalytics: ActivationAnalytics,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private val syncRetryInFlightGuard = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            authenticatedMemberBootstrapCoordinator.ensureAuthenticatedMemberExists()
        }
        loadData()
        observeTileCounts()
        observeSyncHealth()
        observeFamilyOnboarding()
    }

    private fun observeTileCounts() {
        viewModelScope.launch {
            combine(
                appSettingsDataStore.settingsFlow,
                shoppingRepository.observeLists(),
            ) { settings, lists ->
                resolveActiveShoppingListId(settings.activeShoppingListId, lists.filterNot { it.isArchived })
            }
                .distinctUntilChanged()
                .flatMapLatest { listId ->
                    shoppingRepository.observeItems(listId).map { items ->
                        items.count { !it.isBought }
                    }
                }
                .collect { count ->
                    _state.update { current -> current.copy(shoppingItemsRemaining = count) }
                }
        }
        viewModelScope.launch {
            weeklyMenuRepository.observeMenu(WeekUtils.getCurrentWeekStartMillis())
                .map { menu -> countMealsForToday(menu) }
                .collect { count ->
                    _state.update { current -> current.copy(todayMealsCount = count) }
                }
        }
    }

    private fun resolveActiveShoppingListId(
        preferredListId: String,
        activeLists: List<ShoppingList>,
    ): String {
        if (preferredListId.isNotBlank() && activeLists.any { it.id == preferredListId }) {
            return preferredListId
        }
        return activeLists.firstOrNull()?.id ?: ShoppingRepository.DEFAULT_LIST_ID
    }

    private fun countMealsForToday(menu: com.familyhub.domain.model.WeeklyMenu?): Int {
        if (menu == null) return 0
        val todayStart = TimeUtils.getStartOfDay(TimeUtils.now())
        val weekStart = WeekUtils.getWeekStartMillis(todayStart)
        if (menu.startDateMillis != weekStart) return 0
        val dayOffset = ((todayStart - weekStart) / DAY_MILLIS).toInt()
        if (dayOffset !in 0..6) return 0
        return menu.entries.count { it.dayOfWeek == dayOffset }
    }

    private fun observeFirebaseAuthenticated() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser != null)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun observeFamilyOnboarding() {
        viewModelScope.launch {
            combine(
                observeFirebaseAuthenticated(),
                observeSyncStateUseCase(),
                appSettingsDataStore.settingsFlow,
            ) { isAuthenticated, syncState, settings ->
                FamilyOnboardingUiMapper.map(
                    isFirebaseAuthenticated = isAuthenticated,
                    accountId = syncState.accountId,
                    familyOnboardingDismissed = settings.familyOnboardingDismissed,
                    isHydrated = syncState.isHydrated,
                )
            }.collect { onboarding ->
                _state.update { current -> current.copy(familyOnboarding = onboarding) }
            }
        }
    }

    private fun observeSyncHealth() {
        viewModelScope.launch {
            combine(
                observeSyncStateUseCase(),
                memberRepository.getAllMembers(),
                appSettingsDataStore.settingsFlow
            ) { syncState, members, settings ->
                val authUserId = firebaseAuth.currentUser?.uid?.takeIf { it.isNotBlank() }
                val currentUserId = authUserId ?: settings.currentUserId
                val memberRole = members.find { it.id == currentUserId }?.role ?: MemberRole.PENDING
                val syncHealth = SyncHealthUiMapper.map(
                    syncState = syncState,
                    memberRole = memberRole,
                )
                syncState to syncHealth
            }.collect { (syncState, syncHealth) ->
                _state.update { current ->
                    current.copy(
                        syncState = syncState,
                        syncHealth = syncHealth,
                        familyActivation = FamilyActivationChecklistMapper.map(
                            isHydrated = syncState.isHydrated,
                            accountId = syncState.accountId,
                            currentUserId = current.currentUserId,
                            currentUserRole = current.currentUserRole,
                            members = current.members,
                            hasAnyTask = current.hasAnyTask,
                        ),
                    )
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                val weekStart = WeekUtils.getCurrentWeekStartMillis()
                
                combine(
                    appSettingsDataStore.settingsFlow,
                    taskRepository.getAllTasks(),
                    eventRepository.getAllEvents(),
                    memberRepository.getAllMembers(),
                    scoreRepository.getScoresForWeek(weekStart)
                ) { settings, allTasks, allEvents, members, scores ->
                    val authUser = firebaseAuth.currentUser
                    val authUserId = authUser?.uid
                    var currentUserId = authUserId?.takeIf { it.isNotBlank() } ?: settings.currentUserId
                    val resolvedMembers = members

                    if (authUserId != null && settings.currentUserId != authUserId) {
                        viewModelScope.launch { appSettingsDataStore.setCurrentUser(authUserId) }
                        currentUserId = authUserId
                    }
                    if (authUserId == null && currentUserId.isBlank() && resolvedMembers.isNotEmpty()) {
                        val fallback = resolvedMembers.first()
                        currentUserId = fallback.id
                        viewModelScope.launch { appSettingsDataStore.setCurrentUser(fallback.id) }
                    }

                    // Мои задачи сегодня (невыполненные)
                    val myTasksToday = allTasks
                        .filterByVisibility(currentUserId)
                        .filter { it.assignedToUserId == currentUserId && it.status == TaskStatus.TODO }
                        .take(5)

                    // Ближайшие события (2 штуки)
                    val now = TimeUtils.now()
                    val upcomingEvents = allEvents
                        .mapNotNull { event ->
                            val next = event.getNextOccurrence(now)
                            next?.let { EventOccurrence(event, it) }
                        }
                        .sortedBy { it.startTime }
                        .take(2)

                    // Баллы за неделю
                    val myScore = scores.firstOrNull { it.userId == currentUserId }?.score ?: 0

                    HomeContentSnapshot(
                        isLoading = false,
                        myTasksToday = myTasksToday,
                        upcomingEvents = upcomingEvents,
                        myScoreThisWeek = myScore,
                        currentUserId = currentUserId,
                        currentUserRole = resolvedMembers
                            .firstOrNull { it.id == currentUserId }
                            ?.role
                            ?: MemberRole.PENDING,
                        members = resolvedMembers,
                        hasAnyTask = allTasks.isNotEmpty(),
                    )
                }.collect { content ->
                    _state.update { current ->
                        current.copy(
                            isLoading = content.isLoading,
                            myTasksToday = content.myTasksToday,
                            upcomingEvents = content.upcomingEvents,
                            myScoreThisWeek = content.myScoreThisWeek,
                            currentUserId = content.currentUserId,
                            currentUserRole = content.currentUserRole,
                            members = content.members,
                            hasAnyTask = content.hasAnyTask,
                            familyActivation = FamilyActivationChecklistMapper.map(
                                isHydrated = current.syncState.isHydrated,
                                accountId = current.syncState.accountId,
                                currentUserId = content.currentUserId,
                                currentUserRole = content.currentUserRole,
                                members = content.members,
                                hasAnyTask = content.hasAnyTask,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.LoadData,
                            tag = "HomeViewModel",
                        ),
                    )
                }
            }
        }
    }

    fun toggleTaskStatus(task: Task) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val currentUserId = _state.value.currentUserId

                val result = when (task.status) {
                    TaskStatus.TODO -> completeTaskUseCase(task, currentUserId)
                    TaskStatus.DONE -> undoTaskCompletionUseCase(task, currentUserId)
                    TaskStatus.WAITING_FOR_APPROVAL -> return@launch
                }

                if (!result.mutated) {
                    return@launch
                }

                if (result.newStatus == TaskStatus.DONE) {
                    activationAnalytics.taskLoopCompleted()
                }

                val impact = result.scoreImpact
                if (impact.totalDelta > 0 && result.newStatus == TaskStatus.DONE) {
                    val feedback = completionFeedbackProvider.buildFeedback(impact)
                    _state.value = _state.value.copy(
                        completionFeedback = feedback,
                        errorMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(completionFeedback = null, errorMessage = null)
                }

                FamilyHubWidgetProvider.updateWidgets(context)
            } catch (e: TaskTransitionForbiddenException) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.TaskUpdate,
                        tag = "HomeViewModel",
                    ),
                    completionFeedback = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.TaskUpdate,
                        tag = "HomeViewModel",
                    ),
                    completionFeedback = null
                )
            }
        }
    }

    fun createEvent(
        title: String,
        startDateTime: Long,
        durationMinutes: Int,
        recurrence: com.familyhub.domain.model.EventRecurrence,
        reminderMinutesBefore: Int?
    ) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                val currentUserId = _state.value.currentUserId
                if (currentUserId.isBlank()) {
                    _state.value = _state.value.copy(
                        errorMessage = context.getString(R.string.error_no_current_user)
                    )
                    return@launch
                }
                
                val newEvent = com.familyhub.domain.model.FamilyEvent(
                    id = idGenerator.newId(),
                    title = title,
                    startDateTime = startDateTime,
                    createdByUserId = currentUserId,
                    durationMinutes = durationMinutes,
                    recurrence = recurrence,
                    reminderMinutesBefore = reminderMinutesBefore
                )
                eventRepository.upsertEvent(newEvent)
                eventReminderScheduler.scheduleReminder(newEvent)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.EventCreate,
                        tag = "HomeViewModel",
                    )
                )
            }
        }
    }

    fun updateEvent(event: com.familyhub.domain.model.FamilyEvent) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                eventRepository.upsertEvent(event)
                if (event.reminderMinutesBefore != null) {
                    eventReminderScheduler.scheduleReminder(event)
                } else {
                    eventReminderScheduler.cancelReminder(event.id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.EventUpdate,
                        tag = "HomeViewModel",
                    )
                )
            }
        }
    }

    fun deleteEvent(event: com.familyhub.domain.model.FamilyEvent) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            try {
                event.googleCalendarEventId?.let { googleId ->
                    // Attempt to delete remote Google Calendar event if linked
                    deleteGoogleCalendarEventUseCase(googleId)
                }
                eventRepository.deleteEvent(event)
                eventReminderScheduler.cancelReminder(event.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.EventDelete,
                        tag = "HomeViewModel",
                    )
                )
            }
        }
    }

    fun setCurrentUser(userId: String) {
        viewModelScope.launch {
            try {
                val authId = firebaseAuth.currentUser?.uid ?: return@launch
                if (userId != authId) return@launch
                appSettingsDataStore.setCurrentUser(authId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = UserFacingErrorMapper.message(
                        context,
                        e,
                        UserFacingErrorContext.UserSwitch,
                        tag = "HomeViewModel",
                    )
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearCompletionFeedback() {
        _state.value = _state.value.copy(completionFeedback = null)
    }

    fun onSyncHealthAction() {
        val health = _state.value.syncHealth
        if (!health.actionEnabled || health.action == SyncHealthAction.NONE) {
            return
        }
        if (_state.value.syncState.isSyncing || _state.value.syncRetryInFlight) {
            return
        }
        when (health.action) {
            SyncHealthAction.RETRY,
            SyncHealthAction.SYNC_NOW -> retrySync()
            SyncHealthAction.CHECK -> {
                activationAnalytics.approvalChecked()
                retrySync()
            }
            SyncHealthAction.SETTINGS,
            SyncHealthAction.NONE -> Unit
        }
    }

    fun retrySync() {
        if (_state.value.syncState.isSyncing) {
            return
        }
        if (!syncRetryInFlightGuard.compareAndSet(false, true)) {
            return
        }
        _state.update { it.copy(syncRetryInFlight = true) }
        viewModelScope.launch {
            try {
                performSyncNowUseCase()
            } finally {
                syncRetryInFlightGuard.set(false)
                _state.update { it.copy(syncRetryInFlight = false) }
            }
        }
    }

    fun dismissFamilyOnboarding() {
        activationAnalytics.onboardingChoice(ActivationAnalytics.Choice.LOCAL)
        viewModelScope.launch {
            appSettingsDataStore.setFamilyOnboardingDismissed(true)
        }
    }

    fun recordFamilyOnboardingChoice(choice: ActivationAnalytics.Choice) {
        activationAnalytics.onboardingChoice(choice)
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
