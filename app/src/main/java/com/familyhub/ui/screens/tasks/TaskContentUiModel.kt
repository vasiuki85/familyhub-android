package com.familyhub.ui.screens.tasks

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.domain.model.Task

enum class TaskContentEmptyKind {
    NO_MY_TASKS,
    NO_FAMILY_TASKS,
    NO_AWAITING,
    NO_RESULTS,
}

enum class TaskContentAction {
    CREATE_TASK,
    RESET_FILTERS,
}

enum class TaskFabAction {
    NONE,
    CREATE_TASK,
}

data class TaskContentUiModel(
    val emptyKind: TaskContentEmptyKind? = null,
    @StringRes val titleResId: Int? = null,
    @StringRes val messageResId: Int? = null,
    @StringRes val actionLabelResId: Int? = null,
    val primaryAction: TaskContentAction? = null,
    @StringRes val actionContentDescriptionResId: Int? = null,
    val showSearch: Boolean = true,
    val showStatusAndCategoryFilters: Boolean = true,
    val fabAction: TaskFabAction = TaskFabAction.CREATE_TASK,
) {
    val showsEmptyState: Boolean
        get() = emptyKind != null

    val showsFamilyEmptyIllustration: Boolean
        get() = emptyKind == TaskContentEmptyKind.NO_MY_TASKS ||
            emptyKind == TaskContentEmptyKind.NO_FAMILY_TASKS

    companion object {
        fun content(effectiveTab: TaskTab): TaskContentUiModel = TaskContentUiModel(
            showStatusAndCategoryFilters = effectiveTab != TaskTab.AWAITING,
        )

        fun loading(): TaskContentUiModel = TaskContentUiModel(
            showSearch = false,
            showStatusAndCategoryFilters = false,
            fabAction = TaskFabAction.NONE,
        )
    }
}

object TaskContentUiStateMapper {
    fun mapFromState(
        state: TasksUiState,
        effectiveTab: TaskTab,
    ): TaskContentUiModel = map(
        isLoading = state.isLoading,
        effectiveTab = effectiveTab,
        myBase = state.myBase,
        allBase = state.allBase,
        awaitingBase = state.awaitingBase,
        myTasks = state.myTasks,
        allTasks = state.allTasks,
        awaiting = state.awaitingApprovalTasks,
        searchQuery = state.searchQuery,
        filterType = state.filterType,
        selectedCategoryFilterId = state.selectedCategoryFilterId,
    )

    fun map(
        isLoading: Boolean,
        effectiveTab: TaskTab,
        myBase: List<Task>,
        allBase: List<Task>,
        awaitingBase: List<Task>,
        myTasks: List<Task>,
        allTasks: List<Task>,
        awaiting: List<Task>,
        searchQuery: String,
        filterType: TaskFilterType,
        selectedCategoryFilterId: String?,
    ): TaskContentUiModel {
        if (isLoading) {
            return TaskContentUiModel.loading()
        }

        val (base, displayed) = when (effectiveTab) {
            TaskTab.MY -> myBase to myTasks
            TaskTab.ALL -> allBase to allTasks
            TaskTab.AWAITING -> awaitingBase to awaiting
        }

        if (base.isEmpty()) {
            return when (effectiveTab) {
                TaskTab.MY -> emptyState(
                    kind = TaskContentEmptyKind.NO_MY_TASKS,
                    titleResId = R.string.tasks_empty_no_my_title,
                    messageResId = R.string.tasks_empty_no_my_message,
                    actionLabelResId = R.string.tasks_create,
                    primaryAction = TaskContentAction.CREATE_TASK,
                    actionContentDescriptionResId = R.string.tasks_empty_create_task_cd,
                    effectiveTab = effectiveTab,
                )
                TaskTab.ALL -> emptyState(
                    kind = TaskContentEmptyKind.NO_FAMILY_TASKS,
                    titleResId = R.string.tasks_empty_no_family_title,
                    messageResId = R.string.tasks_empty_no_family_message,
                    actionLabelResId = R.string.tasks_create,
                    primaryAction = TaskContentAction.CREATE_TASK,
                    actionContentDescriptionResId = R.string.tasks_empty_create_task_cd,
                    effectiveTab = effectiveTab,
                )
                TaskTab.AWAITING -> emptyState(
                    kind = TaskContentEmptyKind.NO_AWAITING,
                    titleResId = R.string.tasks_empty_no_awaiting_title,
                    messageResId = R.string.tasks_empty_no_awaiting_message,
                    actionLabelResId = null,
                    primaryAction = null,
                    actionContentDescriptionResId = null,
                    effectiveTab = effectiveTab,
                )
            }
        }

        if (
            displayed.isEmpty() &&
            isRestrictionActive(effectiveTab, searchQuery, filterType, selectedCategoryFilterId)
        ) {
            return emptyState(
                kind = TaskContentEmptyKind.NO_RESULTS,
                titleResId = R.string.tasks_empty_no_results_title,
                messageResId = R.string.tasks_empty_no_results_message,
                actionLabelResId = R.string.tasks_empty_no_results_action,
                primaryAction = TaskContentAction.RESET_FILTERS,
                actionContentDescriptionResId = R.string.tasks_empty_no_results_action_cd,
                effectiveTab = effectiveTab,
            )
        }

        return TaskContentUiModel.content(effectiveTab)
    }

    internal fun isRestrictionActive(
        effectiveTab: TaskTab,
        searchQuery: String,
        filterType: TaskFilterType,
        selectedCategoryFilterId: String?,
    ): Boolean = when (effectiveTab) {
        TaskTab.AWAITING -> searchQuery.isNotBlank()
        TaskTab.MY, TaskTab.ALL ->
            searchQuery.isNotBlank() ||
                filterType != TaskFilterType.ALL ||
                selectedCategoryFilterId != null
    }

    private fun emptyState(
        kind: TaskContentEmptyKind,
        @StringRes titleResId: Int,
        @StringRes messageResId: Int,
        @StringRes actionLabelResId: Int?,
        primaryAction: TaskContentAction?,
        @StringRes actionContentDescriptionResId: Int?,
        effectiveTab: TaskTab,
    ): TaskContentUiModel = TaskContentUiModel(
        emptyKind = kind,
        titleResId = titleResId,
        messageResId = messageResId,
        actionLabelResId = actionLabelResId,
        primaryAction = primaryAction,
        actionContentDescriptionResId = actionContentDescriptionResId,
        showSearch = true,
        showStatusAndCategoryFilters = effectiveTab != TaskTab.AWAITING,
        fabAction = TaskFabAction.CREATE_TASK,
    )
}
