package com.familyhub.ui.screens.tasks

import com.familyhub.ui.components.FhPillTabRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskTemplate
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.screens.tasks.TaskTab
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.TaskCompletionFeedbackCard
import com.familyhub.ui.components.TaskItemCard
import com.familyhub.ui.components.DraggableTaskItem
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FhCardTone

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val availableTabs = remember(state.currentUserRole) {
        TaskTabPolicy.availableTabs(state.currentUserRole)
    }
    val effectiveTab = remember(state.currentUserRole, state.selectedTab) {
        TaskTabPolicy.resolveEffectiveTab(state.currentUserRole, state.selectedTab)
    }
    val taskCapabilities = remember(state.currentUserRole) {
        TaskRoleCapabilityPolicy.resolve(state.currentUserRole)
    }
    val contentUi = TaskContentUiStateMapper.mapFromState(state, effectiveTab).let { model ->
        if (taskCapabilities.canCreateTask) {
            model
        } else {
            model.copy(
                actionLabelResId = model.actionLabelResId
                    ?.takeUnless { model.primaryAction == TaskContentAction.CREATE_TASK },
                primaryAction = model.primaryAction
                    ?.takeUnless { it == TaskContentAction.CREATE_TASK },
                actionContentDescriptionResId = model.actionContentDescriptionResId
                    ?.takeUnless { model.primaryAction == TaskContentAction.CREATE_TASK },
                fabAction = TaskFabAction.NONE,
            )
        }
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var templateToDelete by remember { mutableStateOf<TaskTemplate?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskIdForHistory by remember { mutableStateOf<String?>(null) }
    var taskToApprove by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(effectiveTab) {
        if (state.selectedTab != effectiveTab) {
            viewModel.setSelectedTab(effectiveTab)
        }
    }
    LaunchedEffect(taskCapabilities.canCreateTask) {
        if (!taskCapabilities.canCreateTask) {
            showCreateDialog = false
            taskToEdit = null
        }
    }
    LaunchedEffect(taskCapabilities.canManageTaskConfiguration) {
        if (!taskCapabilities.canManageTaskConfiguration) {
            templateToDelete = null
            taskToApprove = null
        }
    }
    val showCreateFab = contentUi.fabAction == TaskFabAction.CREATE_TASK &&
        taskCapabilities.canCreateTask &&
        TaskFabVisibilityPolicy.shouldShow(
            fabAction = contentUi.fabAction,
            primaryAction = contentUi.primaryAction,
        )

    Box(modifier = Modifier.fillMaxSize()) {
    FhScreenScaffold(
        title = stringResource(R.string.nav_label_tasks),
        errorMessage = state.errorMessage,
        onErrorDismiss = { viewModel.clearError() },
        floatingActionButton = {
            if (showCreateFab) {
                val taskColors = FhTheme.modules.task
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = taskColors.accent,
                    contentColor = taskColors.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(),
                ) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.tasks_empty_create_task_cd)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Отображение результата начисления баллов
                state.completionFeedback?.let { feedback ->
                    TaskCompletionFeedbackCard(
                        feedback = feedback,
                        onDismiss = { viewModel.clearCompletionFeedback() }
                    )
                }

                val selectedTabIndex = availableTabs.indexOf(effectiveTab).coerceAtLeast(0)
                val taskColors = FhTheme.modules.task
                val awaitingCount = state.awaitingApprovalTasks.size

                val tabLabels = availableTabs.map { tab ->
                    if (tab == TaskTab.AWAITING && awaitingCount > 0) {
                        stringResource(
                            R.string.tasks_tab_awaiting_badge,
                            stringResource(tab.titleRes),
                            awaitingCount,
                        )
                    } else {
                        stringResource(tab.titleRes)
                    }
                }

                FhPillTabRow(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index -> viewModel.setSelectedTab(availableTabs[index]) },
                    tabLabels = tabLabels,
                    palette = taskColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Search bar is moved into the LazyColumn so it scrolls with filters/tasks.

                val tasksToShow = when (effectiveTab) {
                    TaskTab.MY -> state.myTasks
                    TaskTab.ALL -> state.allTasks
                    TaskTab.AWAITING -> state.awaitingApprovalTasks
                }
                val canReorder = TaskManualReorderPolicy.canReorder(
                    isLoading = state.isLoading || state.manualReorderInFlight,
                    role = state.currentUserRole,
                    effectiveTab = effectiveTab,
                    sortType = state.sortType,
                    filterType = state.filterType,
                    searchQuery = state.searchQuery,
                    selectedCategoryFilterId = state.selectedCategoryFilterId,
                    displayedCount = tasksToShow.size,
                )
                val showManualHint = TaskManualReorderPolicy.showsManualHint(
                    sortType = state.sortType,
                    effectiveTab = effectiveTab,
                )
                val density = LocalDensity.current
                val estimatedItemExtentPx = remember(density) {
                    with(density) { 72.dp.toPx() }
                }
                val membersMap = state.members.associateBy { it.id }
                val taskSections = remember(tasksToShow, state.sortType) {
                    TaskListSectionMapper.map(
                        tasks = tasksToShow,
                        sortType = state.sortType,
                    )
                }
                val sectionByFirstTaskId = remember(taskSections) {
                    taskSections.associateBy { it.tasks.first().id }
                }
                val displayedTasks = remember(tasksToShow, taskSections) {
                    if (taskSections.isEmpty()) {
                        tasksToShow
                    } else {
                        taskSections.flatMap { it.tasks }
                    }
                }

                var draggedTaskIndex by remember { mutableStateOf<Int?>(null) }
                var draggedOverIndex by remember { mutableStateOf<Int?>(null) }

                if (contentUi.showSearch || contentUi.showStatusAndCategoryFilters) {
                    TaskFilterCompactBar(
                        filterType = state.filterType,
                        sortType = state.sortType,
                        categories = state.taskCategories,
                        selectedCategoryId = state.selectedCategoryFilterId,
                        searchQuery = state.searchQuery,
                        showSearch = contentUi.showSearch,
                        onFilterChange = viewModel::setFilterType,
                        onSortChange = viewModel::setSortType,
                        onCategorySelected = viewModel::setCategoryFilter,
                        onSearchQueryChange = viewModel::setSearchQuery,
                    )
                    if (contentUi.showStatusAndCategoryFilters && showManualHint) {
                        Text(
                            text = stringResource(
                                if (canReorder) {
                                    R.string.tasks_manual_reorder_hint_enabled
                                } else {
                                    R.string.tasks_manual_reorder_hint_disabled
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }

                if (state.isLoading) {
                    FhListSkeleton(modifier = Modifier.fillMaxSize())
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = FhLayout.listBottomPadding(showCreateFab),
                    ),
                ) {
                    if (!taskCapabilities.canCreateTask) {
                        item(key = "pending_read_only") {
                            com.familyhub.ui.theme.components.ThemedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                tone = FhCardTone.INFO,
                            ) {
                                Text(
                                    text = stringResource(R.string.tasks_pending_read_only_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    when {
                        contentUi.showsEmptyState -> {
                            item {
                                TaskContentEmptyState(
                                    model = contentUi,
                                    onPrimaryAction = {
                                        when (contentUi.primaryAction) {
                                            TaskContentAction.CREATE_TASK -> showCreateDialog = true
                                            TaskContentAction.RESET_FILTERS -> viewModel.resetSearchAndFilters()
                                            null -> Unit
                                        }
                                    },
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(
                                items = displayedTasks,
                                key = { _, it -> it.id }
                            ) { taskIndex, task ->
                            sectionByFirstTaskId[task.id]?.let { section ->
                                TaskListSectionHeader(section)
                            }
                            val isDragging = draggedTaskIndex == taskIndex
                            val isDraggedOver = draggedOverIndex == taskIndex && draggedTaskIndex != null && draggedTaskIndex != taskIndex

                            val assignedToName = membersMap[task.assignedToUserId]?.displayLabel()
                                ?: stringResource(R.string.tasks_member_unknown)

                            val moveUpLabel = stringResource(R.string.tasks_manual_reorder_move_up)
                            val moveDownLabel = stringResource(R.string.tasks_manual_reorder_move_down)

                            DraggableTaskItem(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canReorder,
                                dragHandleContentDescription = if (canReorder) {
                                    stringResource(R.string.tasks_manual_reorder_drag_handle_cd)
                                } else {
                                    null
                                },
                                moveUpLabel = if (canReorder && taskIndex > 0) moveUpLabel else null,
                                moveDownLabel = if (canReorder && taskIndex < displayedTasks.lastIndex) {
                                    moveDownLabel
                                } else {
                                    null
                                },
                                onMoveUp = if (canReorder && taskIndex > 0) {
                                    {
                                        viewModel.beginManualReorder(task.id, taskIndex)
                                        viewModel.commitManualReorder(taskIndex - 1)
                                    }
                                } else {
                                    null
                                },
                                onMoveDown = if (canReorder && taskIndex < displayedTasks.lastIndex) {
                                    {
                                        viewModel.beginManualReorder(task.id, taskIndex)
                                        viewModel.commitManualReorder(taskIndex + 1)
                                    }
                                } else {
                                    null
                                },
                                onDragStart = {
                                    draggedTaskIndex = taskIndex
                                    draggedOverIndex = taskIndex
                                    viewModel.beginManualReorder(
                                        taskId = task.id,
                                        startIndex = taskIndex,
                                    )
                                },
                                onDrag = { cumulativeOffsetY ->
                                    val startIndex = draggedTaskIndex ?: taskIndex
                                    draggedOverIndex = TaskDragIndexCalculator.targetIndex(
                                        startIndex = startIndex,
                                        cumulativeOffsetPx = cumulativeOffsetY,
                                        estimatedItemExtentPx = estimatedItemExtentPx,
                                        itemCount = displayedTasks.size,
                                    )
                                },
                                onDragEnd = {
                                    val toIndex = draggedOverIndex
                                    if (canReorder && toIndex != null) {
                                        viewModel.commitManualReorder(toIndex = toIndex)
                                    } else {
                                        viewModel.cancelManualReorder()
                                    }
                                    draggedTaskIndex = null
                                    draggedOverIndex = null
                                },
                                onDragCancel = {
                                    viewModel.cancelManualReorder()
                                    draggedTaskIndex = null
                                    draggedOverIndex = null
                                },
                                content = { reorderModifier ->
                                val categoryColor = task.categoryId?.let { categoryId ->
                                    state.taskCategories.firstOrNull { it.id == categoryId }?.color
                                }?.let { Color(it) }
                                TaskItemCard(
                                    task = task,
                                    assignedToName = assignedToName,
                                    categoryColor = categoryColor,
                                    onStatusChange = if (
                                        TaskRoleCapabilityPolicy.canChangeStatus(
                                            role = state.currentUserRole,
                                            currentUserId = state.currentUserId,
                                            task = task,
                                            effectiveTab = effectiveTab,
                                        )
                                    ) {
                                        { viewModel.toggleTaskStatus(task) }
                                    } else {
                                        null
                                    },
                                    onEdit = if (viewModel.canEditTask(task)) { { taskToEdit = task } } else null,
                                    onDelete = if (viewModel.canDeleteTask(task)) { { taskToDelete = task } } else null,
                                    onHistory = { taskIdForHistory = task.id },
                                    onConfirmCompletion = if (effectiveTab == TaskTab.AWAITING) { { taskToApprove = task } } else null,
                                    onDeclineCompletion = if (effectiveTab == TaskTab.AWAITING) { { viewModel.declineTaskCompletion(task) } } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isDragging) Modifier.alpha(0.5f) else Modifier)
                                        .then(
                                            if (isDraggedOver) {
                                                Modifier.background(
                                                    taskColors.container.copy(alpha = 0.5f),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    titleSemanticsModifier = reorderModifier,
                                )
                            }
                            )
                        }
                        }
                    }
                }
                }
            }
        }
    }

    if (showCreateDialog && taskCapabilities.canCreateTask) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
        ) {
        TaskEditorDialog(
                members = state.members,
                templates = state.taskTemplates,
                categories = state.taskCategories,
                availableCategoryColors = state.availableCategoryColors,
                currentUserId = state.currentUserId,
                currentUserRole = state.currentUserRole,
                task = null,
                rotationConfig = null,
                onDismiss = { showCreateDialog = false },
                onConfirm = { saveData ->
                    viewModel.createTask(saveData)
                    showCreateDialog = false
                },
            onSaveTemplate = { title, description, repeatRule, defaultAssigneeId, isPrivate, taskType, priority, categoryId ->
                viewModel.saveTaskTemplate(
                    title,
                    description,
                    repeatRule,
                    defaultAssigneeId,
                    isPrivate,
                    taskType,
                    priority,
                    categoryId
                )
            },
                onDeleteTemplate = { templateId ->
                    templateToDelete = state.taskTemplates.firstOrNull { it.id == templateId }
                },
                onCreateCategory = { name, color -> viewModel.createTaskCategory(name, color) }
        )
        }
    }

    taskToEdit?.let { task ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
        ) {
        TaskEditorDialog(
            members = state.members,
            templates = state.taskTemplates,
            categories = state.taskCategories,
            availableCategoryColors = state.availableCategoryColors,
            currentUserId = state.currentUserId,
            currentUserRole = state.currentUserRole,
            task = task,
            rotationConfig = state.taskRotations[task.id],
            onDismiss = { taskToEdit = null },
            onConfirm = { saveData ->
                viewModel.updateTask(saveData, task)
                taskToEdit = null
            },
            onSaveTemplate = { title, description, repeatRule, defaultAssigneeId, isPrivate, taskType, priority, categoryId ->
                viewModel.saveTaskTemplate(
                    title,
                    description,
                    repeatRule,
                    defaultAssigneeId,
                    isPrivate,
                    taskType,
                    priority,
                    categoryId
                )
            },
            onDeleteTemplate = { templateId ->
                templateToDelete = state.taskTemplates.firstOrNull { it.id == templateId }
            },
            onCreateCategory = { name, color -> viewModel.createTaskCategory(name, color) }
        )
        }
    }
    }

    taskToDelete?.let { task ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_task_title),
            message = stringResource(R.string.delete_task_message, task.title),
            onDismiss = { taskToDelete = null },
            onConfirm = { viewModel.deleteTask(task) }
        )
    }

    templateToDelete?.let { template ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.tasks_template_delete_title),
            message = stringResource(R.string.tasks_template_delete_message, template.title),
            onDismiss = { templateToDelete = null },
            onConfirm = {
                viewModel.deleteTaskTemplate(template.id)
                templateToDelete = null
            },
        )
    }

    taskToApprove?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToApprove = null },
            title = { Text(stringResource(R.string.task_confirm_completion_title)) },
            text = { Text(stringResource(R.string.task_confirm_completion_message, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmTaskCompletion(task)
                        taskToApprove = null
                    }
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { taskToApprove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    taskIdForHistory?.let { taskId ->
        val task = state.allTasksRaw.find { it.id == taskId }
        val history by viewModel.getTaskHistory(taskId).collectAsStateWithLifecycle(initialValue = emptyList())
        
        if (task != null) {
            TaskHistoryDialog(
                taskTitle = task.title,
                history = history,
                onDismiss = { taskIdForHistory = null }
            )
        }
    }
}

@Composable
private fun TaskListSectionHeader(section: TaskListSection) {
    val moduleColors = FhTheme.modules.task
    val titleRes = when (section.key) {
        TaskListSectionKey.OVERDUE -> R.string.tasks_group_overdue
        TaskListSectionKey.TODAY -> R.string.tasks_group_today
        TaskListSectionKey.TOMORROW -> R.string.tasks_group_tomorrow
        TaskListSectionKey.LATER -> R.string.tasks_group_later
        TaskListSectionKey.NO_DATE -> R.string.tasks_group_no_date
        TaskListSectionKey.TODO -> R.string.tasks_group_todo
        TaskListSectionKey.WAITING -> R.string.tasks_group_waiting
        TaskListSectionKey.DONE -> R.string.tasks_group_done
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(moduleColors.container)
            .semantics { heading() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = moduleColors.onContainer,
        )
        Text(
            text = stringResource(R.string.tasks_group_count, section.tasks.size),
            style = MaterialTheme.typography.labelLarge,
            color = moduleColors.accent,
        )
    }
}
