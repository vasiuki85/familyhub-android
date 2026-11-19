package com.familyhub.ui.screens.tasks

internal object TaskSearchFilterReducer {
    fun apply(state: TasksViewState): TasksViewState =
        state.copy(
            searchQuery = "",
            filterType = TaskFilterType.ALL,
            selectedCategoryFilterId = null,
        )
}
