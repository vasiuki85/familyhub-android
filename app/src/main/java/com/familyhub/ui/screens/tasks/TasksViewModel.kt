package com.familyhub.ui.screens.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.TaskHistoryRepository
import com.familyhub.data.repository.TaskRotationRepository
import com.familyhub.data.repository.TaskTemplateRepository
import com.familyhub.data.repository.TaskCategoryRepository
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskRotationConfig
import com.familyhub.domain.model.TaskRotationFrequency
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskAttachment
import com.familyhub.domain.model.TaskComment
import com.familyhub.domain.model.TaskTransitionForbiddenException
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.model.TaskCategory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.familyhub.domain.usecase.CompleteTaskUseCase
import com.familyhub.domain.usecase.ConfirmTaskUseCase
import com.familyhub.domain.usecase.RejectTaskUseCase
import com.familyhub.domain.usecase.UndoTaskCompletionUseCase
import com.familyhub.domain.usecase.RecordTaskHistoryUseCase
import com.familyhub.domain.usecase.TaskCompletionFeedback
import com.familyhub.domain.usecase.TaskCompletionFeedbackProvider
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.domain.utils.filterByVisibility
import com.familyhub.domain.utils.canEdit
import com.familyhub.domain.utils.canDelete
import com.familyhub.domain.utils.TaskRotationUtils
import com.familyhub.ui.screens.tasks.filterTasks
import com.familyhub.ui.screens.tasks.sortTasks
import com.familyhub.util.TimeUtils
import com.familyhub.ui.screens.tasks.TaskTab
import com.familyhub.widget.FamilyHubWidgetProvider
import com.familyhub.R
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

import java.util.LinkedHashSet

internal data class TasksViewState(
    val selectedTab: TaskTab = TaskTab.MY,
    val filterType: TaskFilterType = TaskFilterType.TODO,
    val sortType: TaskSortType = TaskSortType.DATE_NEWEST,
    val searchQuery: String = "",
    val selectedCategoryFilterId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val completionFeedback: TaskCompletionFeedback? = null,
    val isLoading: Boolean = false,
    val manualReorderInFlight: Boolean = false,
)

data class TasksUiState(
    val isLoading: Boolean = false,
    val allTasksRaw: List<Task> = emptyList(), // Все задачи без фильтрации/сортировки
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING, // Fail closed until membership is loaded.
    val members: List<FamilyMember> = emptyList(),
    val selectedTab: TaskTab = TaskTab.MY,
    val filterType: TaskFilterType = TaskFilterType.TODO,
    val sortType: TaskSortType = TaskSortType.DATE_NEWEST,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null, // Сообщение об успешной операции для Snackbar
    val completionFeedback: TaskCompletionFeedback? = null,
    val taskRotations: Map<String, TaskRotationConfig> = emptyMap(),
    val taskTemplates: List<TaskTemplate> = emptyList(),
    val taskCategories: List<TaskCategory> = emptyList(),
    val availableCategoryColors: List<Long> = emptyList(),
    val selectedCategoryFilterId: String? = null,
    val awaitingBase: List<Task> = emptyList(),
    val myBase: List<Task> = emptyList(),
    val allBase: List<Task> = emptyList(),
    val awaitingApprovalTasks: List<Task> = emptyList(),
    val myTasks: List<Task> = emptyList(),
    val allTasks: List<Task> = emptyList(),
    val manualReorderInFlight: Boolean = false,
)

data class TaskManualDragSession(
    val draggedTaskId: String,
    val startIndex: Int,
    val currentUserId: String,
    val snapshot: List<com.familyhub.domain.model.TaskOrderSnapshotEntry>,
)

data class TaskRotationEditorData(
    val enabled: Boolean,
    val memberIds: List<String>,
    val frequency: TaskRotationFrequency,
    val startDateTime: Long
)

data class TaskEditorSaveData(
    val title: String,
    val description: String?,
    val dueDate: Long?,
    val dueDateHasTime: Boolean,
    val reminderMode: TaskReminderMode,
    val reminderMinutesOfDay: Int?,
    val repeatRule: RepeatRule,
    val assignedToUserId: String,
    val isPrivate: Boolean,
    val taskType: TaskType,
    val priority: TaskPriority,
    val rewardPoints: Int?,
    val categoryId: String?,
    val attachments: List<TaskAttachment>,
    val comments: List<TaskComment>,
    val rotation: TaskRotationEditorData?,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val memberRepository: FamilyMemberRepository,
    private val taskHistoryRepository: TaskHistoryRepository,
    private val taskRotationRepository: TaskRotationRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskCategoryRepository: TaskCategoryRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val confirmTaskUseCase: ConfirmTaskUseCase,
    private val rejectTaskUseCase: RejectTaskUseCase,
    private val undoTaskCompletionUseCase: UndoTaskCompletionUseCase,
    private val recordTaskHistoryUseCase: RecordTaskHistoryUseCase,
    private val idGenerator: IdGenerator,
    private val notificationScheduler: com.familyhub.data.notification.TaskNotificationScheduler,
    private val completionFeedbackProvider: TaskCompletionFeedbackProvider,
    private val syncChangesUseCase: com.familyhub.domain.usecase.SyncChangesUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "TasksViewModel"
    }
    
    private val categoryColorPalette = listOf(
        0xFFEF5350,
        0xFFAB47BC,
        0xFF5C6BC0,
        0xFF29B6F6,
        0xFF26A69A,
        0xFF9CCC65,
        0xFFFFCA28,
        0xFFFFA726,
        0xFF8D6E63,
        0xFF78909C
    ).map { it.toLong() }

    private val _viewState = MutableStateFlow(TasksViewState())
    private val manualReorderBarrier = TaskManualReorderBarrier()

    val state: StateFlow<TasksUiState> = combine(
        appSettingsDataStore.settingsFlow,
        taskRepository.getAllTasks(),
        memberRepository.getAllMembers(),
        taskRotationRepository.getAllRotations(),
        taskTemplateRepository.getAllTemplates(),
        taskCategoryRepository.getAllCategories(),
        _viewState
    ) { params: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val settings = params[0] as com.familyhub.domain.model.AppSettings
        val tasks = (params[1] as List<Task>).distinctBy { it.id }
        val members = params[2] as List<FamilyMember>
        val rotations = params[3] as List<TaskRotationConfig>
        val templates = params[4] as List<TaskTemplate>
        val categories = params[5] as List<TaskCategory>
        val viewState = params[6] as TasksViewState

        val currentUserId = settings.currentUserId
        val currentMember = members.find { it.id == currentUserId }
        val currentUserRole = currentMember?.role ?: MemberRole.PENDING

        val projection = TaskListProjection.build(
            tasks,
            currentUserId,
            viewState.filterType,
            viewState.sortType,
            viewState.searchQuery,
            viewState.selectedCategoryFilterId
        )

        TasksUiState(
            isLoading = viewState.isLoading,
            allTasksRaw = tasks,
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
            members = members,
            selectedTab = viewState.selectedTab,
            filterType = viewState.filterType,
            sortType = viewState.sortType,
            searchQuery = viewState.searchQuery,
            errorMessage = viewState.errorMessage,
            successMessage = viewState.successMessage,
            completionFeedback = viewState.completionFeedback,
            taskRotations = rotations.associateBy { it.taskId },
            taskTemplates = templates,
            taskCategories = categories,
            availableCategoryColors = categoryColorPalette,
            selectedCategoryFilterId = viewState.selectedCategoryFilterId,
            awaitingBase = projection.awaitingBase,
            myBase = projection.myBase,
            allBase = projection.allBase,
            awaitingApprovalTasks = projection.awaiting,
            myTasks = projection.myTasks,
            allTasks = projection.allTasks,
            manualReorderInFlight = viewState.manualReorderInFlight,
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            combine(
                appSettingsDataStore.settingsFlow,
                taskRepository.getAllTasks(),
            ) { settings, tasks ->
                settings.currentUserId to tasks
            }.distinctUntilChanged().collect { (currentUserId, tasks) ->
                if (currentUserId.isBlank()) return@collect
                tasks.forEach { task ->
                    notificationScheduler.scheduleTaskDeadlineNotification(task, currentUserId)
                }
            }
        }
    }

    // Kept for compatibility if needed, but logic is now in the flow
    fun loadData() { }

    fun confirmTaskCompletion(task: Task) {
        viewModelScope.launch {
            val currentUserId = state.value.currentUserId
            val role = state.value.currentUserRole
            if (currentUserId.isBlank()) return@launch
            if (role != MemberRole.PARENT && role != MemberRole.ADMIN) return@launch
            // if (!task.requiresApproval) return@launch // Условие можно смягчить, если мы хотим подтверждать любые waiting задачи

            runCatching {
                val result = confirmTaskUseCase(task, currentUserId)
                if (!result.mutated) return@runCatching

                recordTaskHistoryUseCase.recordUpdated(
                    task.copy(status = result.newStatus, requiresApproval = false),
                    task,
                    currentUserId,
                    listOf(context.getString(R.string.task_history_change_confirmation))
                )

                val impact = result.scoreImpact
                val feedback = completionFeedbackProvider.buildFeedback(impact)
                if (impact.totalDelta > 0) {
                    _viewState.update { it.copy(completionFeedback = feedback) }
                }

                syncChangesUseCase(force = true)
            }.onFailure { error ->
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.TaskUpdate,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun declineTaskCompletion(task: Task) {
        viewModelScope.launch {
            val currentUserId = state.value.currentUserId
            val role = state.value.currentUserRole
            if (currentUserId.isBlank()) return@launch
            if (role != MemberRole.PARENT && role != MemberRole.ADMIN) return@launch

            runCatching {
                val result = rejectTaskUseCase(task, currentUserId)
                if (!result.mutated) return@runCatching

                recordTaskHistoryUseCase.recordUpdated(
                    task.copy(status = result.newStatus),
                    task,
                    currentUserId,
                    listOf(context.getString(R.string.task_history_change_confirmation))
                )
                syncChangesUseCase(force = true)
            }.onFailure { error ->
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.TaskUpdate,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun toggleTaskStatus(task: Task) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                val actorRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                val effectiveTab = TaskTabPolicy.resolveEffectiveTab(
                    actorRole,
                    state.value.selectedTab,
                )
                if (!TaskRoleCapabilityPolicy.canChangeStatus(
                        role = actorRole,
                        currentUserId = currentUserId,
                        task = task,
                        effectiveTab = effectiveTab,
                    )
                ) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_task_edit_permission))
                    }
                    return@launch
                }

                val result = when (task.status) {
                    TaskStatus.DONE -> undoTaskCompletionUseCase(task, currentUserId)
                    TaskStatus.WAITING_FOR_APPROVAL -> rejectTaskUseCase(task, currentUserId)
                    TaskStatus.TODO -> completeTaskUseCase(task, currentUserId)
                }

                if (!result.mutated) {
                    return@launch
                }

                val impact = result.scoreImpact
                if (impact.totalDelta > 0 && result.newStatus == TaskStatus.DONE) {
                    val feedback = completionFeedbackProvider.buildFeedback(impact)
                    _viewState.update { it.copy(completionFeedback = feedback) }
                    notificationScheduler.cancelTaskDeadlineNotification(task.id)
                } else if (result.newStatus == TaskStatus.WAITING_FOR_APPROVAL) {
                    _viewState.update {
                        it.copy(successMessage = context.getString(R.string.task_sent_for_approval))
                    }
                } else {
                    _viewState.update { it.copy(completionFeedback = null) }
                }

                recordTaskHistoryUseCase.recordStatusChanged(
                    task,
                    result.previousStatus.name,
                    result.newStatus.name,
                    currentUserId
                )
                // Не форсируем синк после reject — облачная WAITING перезапишет локальное TODO.
                // Синхронизация произойдёт при следующем штатном цикле или ручном запуске.
                if (result.previousStatus != TaskStatus.WAITING_FOR_APPROVAL) {
                    syncChangesUseCase(force = true)
                }
            } catch (e: TaskTransitionForbiddenException) {
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskUpdate,
                            tag = TAG,
                        ),
                        completionFeedback = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении задачи", e)
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskUpdate,
                            tag = TAG,
                        ),
                        completionFeedback = null
                    )
                }
            }
        }
    }

    fun createTask(saveData: TaskEditorSaveData) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                if (currentUserId.isBlank()) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_no_current_user))
                    }
                    return@launch
                }
                val actorRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                val capabilities = TaskRoleCapabilityPolicy.resolve(actorRole)
                val guardedAssigneeId = TaskRoleCapabilityPolicy.resolveCreateAssignee(
                    role = actorRole,
                    currentUserId = currentUserId,
                    requestedAssigneeId = saveData.assignedToUserId,
                )
                if (!capabilities.canCreateTask || guardedAssigneeId == null) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.tasks_error_create_permission))
                    }
                    return@launch
                }
                // Убедимся, что создатель и исполнитель присутствуют в БД (иначе Room может выбросить FOREIGN KEY)
                val members = state.value.members
                memberRepository.getMemberById(guardedAssigneeId)
                    ?: members.find { it.id == guardedAssigneeId }?.let { memberRepository.upsertMember(it) }

                // Определяем максимальный orderIndex для установки нового
                val maxOrderIndex = state.value.allTasksRaw.maxOfOrNull { it.orderIndex } ?: -1

                val newTask = Task(
                    id = idGenerator.newId(),
                    title = saveData.title,
                    description = saveData.description,
                    createdByUserId = currentUserId,
                    assignedToUserId = guardedAssigneeId,
                    dueDate = saveData.dueDate,
                    dueDateHasTime = saveData.dueDateHasTime,
                    reminderMode = saveData.reminderMode,
                    reminderMinutesOfDay = saveData.reminderMinutesOfDay,
                    repeatRule = saveData.repeatRule,
                    status = TaskStatus.TODO,
                    createdAt = TimeUtils.now(),
                    orderIndex = maxOrderIndex + 1,
                    isPrivate = saveData.isPrivate,
                    taskType = saveData.taskType,
                    priority = saveData.priority,
                    rewardPoints = saveData.rewardPoints.takeIf { capabilities.canManageTaskConfiguration },
                    categoryId = saveData.categoryId,
                    attachments = saveData.attachments,
                    comments = saveData.comments,
                )
                Timber.d("createTask: Saving task to database. id=%s, title=%s, assignedTo=%s", newTask.id, newTask.title, newTask.assignedToUserId)
                taskRepository.upsertTask(newTask)
                Timber.d("createTask: Task saved successfully. id=%s", newTask.id)
                // Сразу отправляем изменения в облако, чтобы исполнитель получил задачу как можно быстрее
                syncChangesUseCase(force = true)

                saveData.rotation
                    ?.takeIf { capabilities.canManageTaskConfiguration && it.enabled }
                    ?.let { rotationData ->
                    val memberIds = buildRotationMembers(rotationData.memberIds, guardedAssigneeId)
                    if (memberIds.size >= 2) {
                        val currentIndex = memberIds.indexOf(guardedAssigneeId).takeIf { it >= 0 } ?: 0
                        val startDateTime = rotationData.startDateTime
                        val nextRotationAt = TaskRotationUtils.calculateNextRotation(
                            startDateTime,
                            rotationData.frequency,
                            TimeUtils.now()
                        )
                        val config = TaskRotationConfig(
                            taskId = newTask.id,
                            memberIds = memberIds,
                            currentIndex = currentIndex,
                            frequency = rotationData.frequency,
                            startDateTime = startDateTime,
                            nextRotationAt = nextRotationAt,
                            lastRotationAt = null
                        )
                        taskRotationRepository.upsertRotation(config)
                    }
                }

                // Записываем историю создания задачи
                recordTaskHistoryUseCase.recordCreated(newTask, currentUserId)

                // Планируем уведомление о дедлайне
                notificationScheduler.scheduleTaskDeadlineNotification(newTask, currentUserId)

                // Обновляем виджет
                FamilyHubWidgetProvider.updateWidgets(context)

                // Показываем сообщение об успехе
                _viewState.update {
                    it.copy(successMessage = context.getString(R.string.task_created_success))
                }

                // Данные обновятся автоматически через Flow
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при создании задачи", e)
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskCreate,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun saveTaskTemplate(
        title: String,
        description: String?,
        repeatRule: RepeatRule,
        defaultAssigneeId: String?,
        isPrivate: Boolean,
        taskType: TaskType,
        priority: TaskPriority,
        categoryId: String?
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                if (currentUserId.isBlank()) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_no_current_user))
                    }
                    return@launch
                }
                val actorRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                if (!TaskRoleCapabilityPolicy.resolve(actorRole).canManageTaskConfiguration) {
                    _viewState.update {
                        it.copy(
                            errorMessage = context.getString(
                                R.string.tasks_error_configuration_permission,
                            ),
                        )
                    }
                    return@launch
                }

                val template = TaskTemplate(
                    id = idGenerator.newId(),
                    title = title,
                    description = description?.takeIf { it.isNotBlank() },
                    repeatRule = repeatRule,
                    defaultAssigneeId = defaultAssigneeId?.takeIf { it.isNotBlank() },
                    isPrivate = isPrivate,
                    taskType = taskType,
                    createdByUserId = currentUserId,
                    createdAt = TimeUtils.now(),
                    categoryId = categoryId,
                    priority = priority
                )
                taskTemplateRepository.upsert(template)
            } catch (e: Exception) {
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.General,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun deleteTaskTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                val actorRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                if (!TaskRoleCapabilityPolicy.resolve(actorRole).canManageTaskConfiguration) {
                    _viewState.update {
                        it.copy(
                            errorMessage = context.getString(
                                R.string.tasks_error_configuration_permission,
                            ),
                        )
                    }
                    return@launch
                }
                taskTemplateRepository.deleteById(templateId)
            } catch (e: Exception) {
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.General,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun setSelectedTab(tab: TaskTab) {
        _viewState.update { it.copy(selectedTab = tab) }
    }

    fun setFilterType(filterType: TaskFilterType) {
        _viewState.update { it.copy(filterType = filterType) }
    }

    fun setSortType(sortType: TaskSortType) {
        _viewState.update { it.copy(sortType = sortType) }
    }

    fun setSearchQuery(query: String) {
        _viewState.update { it.copy(searchQuery = query) }
    }

    fun resetSearchAndFilters() {
        _viewState.update { TaskSearchFilterReducer.apply(it) }
    }

    fun setCategoryFilter(categoryId: String?) {
        _viewState.update { it.copy(selectedCategoryFilterId = categoryId) }
    }

    fun createTaskCategory(name: String, color: Long): String {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return ""
        }
        if (!TaskRoleCapabilityPolicy.resolve(state.value.currentUserRole).canManageTaskConfiguration) {
            _viewState.update {
                it.copy(
                    errorMessage = context.getString(R.string.tasks_error_configuration_permission),
                )
            }
            return ""
        }
        if (state.value.taskCategories.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return ""
        }

        val now = TimeUtils.now()
        val category = TaskCategory(
            id = idGenerator.newId(),
            name = trimmedName,
            color = color,
            createdAt = now,
            updatedAt = now
        )

        viewModelScope.launch {
            val currentUserId = state.value.currentUserId
            val actorRole = memberRepository.getMemberById(currentUserId)?.role
                ?: MemberRole.PENDING
            if (!TaskRoleCapabilityPolicy.resolve(actorRole).canManageTaskConfiguration) {
                _viewState.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.tasks_error_configuration_permission,
                        ),
                    )
                }
                return@launch
            }
            taskCategoryRepository.upsert(category)
        }

        return category.id
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                val currentUserRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                
                // Проверка прав доступа
                if (!task.canDelete(currentUserId, currentUserRole)) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_task_delete_permission))
                    }
                    return@launch
                }
                
                taskRepository.deleteTask(task)
                
                // Записываем историю удаления задачи
                recordTaskHistoryUseCase.recordDeleted(task, currentUserId)
                
                // Отменяем уведомление о дедлайне
                notificationScheduler.cancelTaskDeadlineNotification(task.id)
                
                // Обновляем виджет
                FamilyHubWidgetProvider.updateWidgets(context)
                
                // Показываем сообщение об успехе
                _viewState.update {
                    it.copy(successMessage = context.getString(R.string.task_deleted_success))
                }
                
                // Данные обновятся автоматически через Flow
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при удалении задачи", e)
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskDelete,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }
    
    fun canEditTask(task: Task): Boolean {
        val currentUserId = state.value.currentUserId
        val currentUserRole = state.value.currentUserRole
        return task.canEdit(currentUserId, currentUserRole)
    }
    
    fun canDeleteTask(task: Task): Boolean {
        val currentUserId = state.value.currentUserId
        val currentUserRole = state.value.currentUserRole
        return task.canDelete(currentUserId, currentUserRole)
    }

    fun updateTask(saveData: TaskEditorSaveData, existingTask: Task) {
        viewModelScope.launch {
            try {
                val currentUserId = state.value.currentUserId
                val currentUserRole = memberRepository.getMemberById(currentUserId)?.role
                    ?: MemberRole.PENDING
                val capabilities = TaskRoleCapabilityPolicy.resolve(currentUserRole)
                val oldTask = taskRepository.getTaskById(existingTask.id)

                // Проверка прав доступа
                if (oldTask == null || !oldTask.canEdit(currentUserId, currentUserRole)) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_task_edit_permission))
                    }
                    return@launch
                }

                val assigneeId = if (capabilities.canChooseAssignee) {
                    saveData.assignedToUserId
                } else {
                    oldTask.assignedToUserId
                }
                val task = oldTask.copy(
                    title = saveData.title,
                    description = saveData.description,
                    dueDate = saveData.dueDate,
                    dueDateHasTime = saveData.dueDateHasTime,
                    reminderMode = saveData.reminderMode,
                    reminderMinutesOfDay = saveData.reminderMinutesOfDay,
                    repeatRule = saveData.repeatRule,
                    assignedToUserId = assigneeId,
                    isPrivate = if (capabilities.canManageTaskConfiguration) {
                        saveData.isPrivate
                    } else {
                        oldTask.isPrivate
                    },
                    taskType = if (capabilities.canManageTaskConfiguration) {
                        saveData.taskType
                    } else {
                        oldTask.taskType
                    },
                    priority = saveData.priority,
                    rewardPoints = if (capabilities.canManageTaskConfiguration) {
                        saveData.rewardPoints
                    } else {
                        oldTask.rewardPoints
                    },
                    categoryId = saveData.categoryId,
                    attachments = saveData.attachments,
                    comments = saveData.comments,
                )

                if (currentUserRole == MemberRole.CHILD &&
                    !TaskRoleCapabilityPolicy.childUpdateKeepsAuthorityFields(oldTask, task)
                ) {
                    _viewState.update {
                        it.copy(errorMessage = context.getString(R.string.error_task_edit_permission))
                    }
                    return@launch
                }

                // Получаем старую версию задачи и ротацию для истории
                val oldRotation = if (capabilities.canManageTaskConfiguration) {
                    taskRotationRepository.getRotationForTask(task.id)
                } else {
                    null
                }

                // Отменяем старое уведомление и планируем новое
                notificationScheduler.cancelTaskDeadlineNotification(task.id)
                taskRepository.upsertTask(task)
                notificationScheduler.scheduleTaskDeadlineNotification(task, currentUserId)

                // Записываем историю изменений
                val changes = mutableListOf<String>()
                val categories = state.value.taskCategories
                fun resolveCategoryName(categoryId: String?): String {
                    return if (categoryId == null) {
                        context.getString(R.string.task_category_none)
                    } else {
                        categories.firstOrNull { it.id == categoryId }?.name
                            ?: context.getString(R.string.task_category_unknown)
                    }
                }
                run {
                    if (oldTask.title != task.title) {
                        changes.add(context.getString(R.string.task_history_change_title))
                    }
                    if (oldTask.description != task.description) {
                        changes.add(context.getString(R.string.task_history_change_description))
                    }
                    if (oldTask.assignedToUserId != task.assignedToUserId) {
                        changes.add(context.getString(R.string.task_history_change_assignee))
                        recordTaskHistoryUseCase.recordAssigned(
                            task,
                            oldTask.assignedToUserId,
                            task.assignedToUserId,
                            currentUserId
                        )
                    }
                    if (oldTask.isPrivate != task.isPrivate) {
                        changes.add(context.getString(R.string.task_history_change_privacy))
                        recordTaskHistoryUseCase.recordPrivacyChanged(
                            task,
                            oldTask.isPrivate,
                            task.isPrivate,
                            currentUserId
                        )
                    }
                    if (
                        oldTask.dueDate != task.dueDate ||
                        oldTask.dueDateHasTime != task.dueDateHasTime ||
                        oldTask.reminderMode != task.reminderMode ||
                        oldTask.reminderMinutesOfDay != task.reminderMinutesOfDay
                    ) {
                        changes.add(context.getString(R.string.task_history_change_deadline))
                    }
                    if (oldTask.repeatRule != task.repeatRule) {
                        changes.add(context.getString(R.string.task_history_change_repeat))
                    }
                    if (oldTask.taskType != task.taskType) {
                        changes.add(context.getString(R.string.task_history_change_type))
                    }
                    if (oldTask.priority != task.priority) {
                        changes.add(context.getString(R.string.task_history_change_priority))
                    }
                    if (oldTask.rewardPoints != task.rewardPoints) {
                        changes.add(context.getString(R.string.task_history_change_reward))
                    }
                    if (oldTask.categoryId != task.categoryId) {
                        val oldLabel = resolveCategoryName(oldTask.categoryId)
                        val newLabel = resolveCategoryName(task.categoryId)
                        changes.add(
                            context.getString(
                                R.string.task_history_change_category,
                                oldLabel,
                                newLabel
                            )
                        )
                    }
                    if (oldTask.attachments != task.attachments) {
                        changes.add(context.getString(R.string.task_history_change_attachments))
                    }
                    if (oldTask.comments != task.comments) {
                        changes.add(context.getString(R.string.task_history_change_comments))
                    }
                }

                var rotationChanged = false
                val rotationData = saveData.rotation?.takeIf {
                    capabilities.canManageTaskConfiguration && it.enabled
                }
                if (capabilities.canManageTaskConfiguration && rotationData != null) {
                    val memberIds = buildRotationMembers(rotationData.memberIds, task.assignedToUserId)
                    if (memberIds.size >= 2) {
                        val currentIndex = memberIds.indexOf(task.assignedToUserId).takeIf { it >= 0 } ?: 0
                        val startDateTime = rotationData.startDateTime
                        val nextRotationAt = TaskRotationUtils.calculateNextRotation(
                            startDateTime,
                            rotationData.frequency,
                            TimeUtils.now()
                        )
                        val updatedRotation = TaskRotationConfig(
                            taskId = task.id,
                            memberIds = memberIds,
                            currentIndex = currentIndex,
                            frequency = rotationData.frequency,
                            startDateTime = startDateTime,
                            nextRotationAt = nextRotationAt,
                            lastRotationAt = oldRotation?.lastRotationAt
                        )
                        taskRotationRepository.upsertRotation(updatedRotation)

                        rotationChanged = oldRotation == null ||
                            oldRotation.memberIds != memberIds ||
                            oldRotation.frequency != rotationData.frequency ||
                            oldRotation.startDateTime != startDateTime
                    } else if (oldRotation != null) {
                        taskRotationRepository.deleteRotation(task.id)
                        rotationChanged = true
                    }
                } else if (capabilities.canManageTaskConfiguration && oldRotation != null) {
                    taskRotationRepository.deleteRotation(task.id)
                    rotationChanged = true
                }

                if (rotationChanged) {
                    changes.add(context.getString(R.string.task_history_change_rotation))
                }

                if (changes.isNotEmpty()) {
                    recordTaskHistoryUseCase.recordUpdated(task, oldTask, currentUserId, changes)
                }

                // Обновляем виджет
                FamilyHubWidgetProvider.updateWidgets(context)

                // Показываем сообщение об успехе
                _viewState.update {
                    it.copy(successMessage = context.getString(R.string.task_updated_success))
                }

                // Данные обновятся автоматически через Flow
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении задачи", e)
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskUpdate,
                            tag = TAG,
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _viewState.update { it.copy(errorMessage = null) }
    }
    
    fun clearSuccessMessage() {
        _viewState.update { it.copy(successMessage = null) }
    }

    fun clearCompletionFeedback() {
        _viewState.update { it.copy(completionFeedback = null) }
    }

    fun beginManualReorder(taskId: String, startIndex: Int) {
        val ui = state.value
        val effectiveTab = TaskTabPolicy.resolveEffectiveTab(ui.currentUserRole, ui.selectedTab)
        val displayedTasks = TaskManualReorderCommitEvaluator.displayedTasksForTab(ui, effectiveTab)
        manualReorderBarrier.begin(
            TaskManualDragSession(
                draggedTaskId = taskId,
                startIndex = startIndex,
                currentUserId = ui.currentUserId,
                snapshot = TaskManualReorderPlanner.snapshotOf(displayedTasks),
            )
        )
    }

    fun cancelManualReorder() {
        manualReorderBarrier.cancel()
    }

    fun commitManualReorder(toIndex: Int) {
        val session = manualReorderBarrier.takeSession() ?: return
        val ui = state.value
        val persist = TaskManualReorderCommitEvaluator.evaluate(
            session = session,
            ui = ui,
            toIndex = toIndex,
        ) ?: return

        if (!manualReorderBarrier.tryAcquireInFlight()) return
        _viewState.update { it.copy(manualReorderInFlight = true) }
        viewModelScope.launch {
            try {
                taskRepository.applyManualOrderChanges(
                    expectedSnapshot = persist.expectedSnapshot,
                    changes = persist.changes,
                    currentUserId = persist.currentUserId,
                )
                FamilyHubWidgetProvider.updateWidgets(context)
            } catch (e: Exception) {
                _viewState.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            e,
                            UserFacingErrorContext.TaskReorder,
                            tag = TAG,
                        )
                    )
                }
            } finally {
                manualReorderBarrier.releaseInFlight()
                _viewState.update { it.copy(manualReorderInFlight = false) }
            }
        }
    }

    private fun buildRotationMembers(selectedIds: List<String>, assignedToUserId: String): List<String> {
        val unique = LinkedHashSet<String>()
        selectedIds.forEach { id ->
            if (id.isNotBlank()) {
                unique.add(id)
            }
        }
        unique.add(assignedToUserId)
        return unique.toList()
    }

    fun getTaskHistory(taskId: String): Flow<List<TaskHistory>> {
        return taskHistoryRepository.getHistoryForTask(taskId)
    }
}
