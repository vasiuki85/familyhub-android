package com.familyhub.ui.screens.tasks

import com.familyhub.R
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContentUiStateMapperTest {

    @Test
    fun defaultLoadingState_hasNoEmptyStateCtaOrFab() {
        val model = TaskContentUiStateMapper.mapFromState(
            TasksUiState(isLoading = true, selectedTab = TaskTab.AWAITING),
            effectiveTab = TaskTab.MY,
        )

        assertFalse(model.showsEmptyState)
        assertNull(model.primaryAction)
        assertEquals(TaskFabAction.NONE, model.fabAction)
        assertFalse(model.showSearch)
        assertFalse(model.showStatusAndCategoryFilters)
    }

    @Test
    fun loadingWithPopulatedTasks_stillBlocksFab() {
        val tasks = listOf(sampleTask(id = "1", assignedTo = USER))
        val model = map(
            isLoading = true,
            myBase = tasks,
            allBase = tasks,
            myTasks = tasks,
            allTasks = tasks,
        )

        assertEquals(TaskFabAction.NONE, model.fabAction)
        assertNull(model.primaryAction)
        assertFalse(model.showsEmptyState)
    }

    @Test
    fun myTabWithoutBaseTasks_returnsNoMyTasksWithCreate() {
        val model = map(effectiveTab = TaskTab.MY)

        assertEquals(TaskContentEmptyKind.NO_MY_TASKS, model.emptyKind)
        assertEquals(TaskContentAction.CREATE_TASK, model.primaryAction)
        assertEquals(TaskFabAction.CREATE_TASK, model.fabAction)
        assertEquals(R.string.tasks_empty_no_my_title, model.titleResId)
    }

    @Test
    fun allTabWithoutVisibleBaseTasks_returnsNoFamilyTasksWithCreate() {
        val model = map(effectiveTab = TaskTab.ALL)

        assertEquals(TaskContentEmptyKind.NO_FAMILY_TASKS, model.emptyKind)
        assertEquals(TaskContentAction.CREATE_TASK, model.primaryAction)
        assertEquals(R.string.tasks_empty_no_family_title, model.titleResId)
    }

    @Test
    fun awaitingWithoutPending_returnsNoAwaitingWithoutCta() {
        val model = map(effectiveTab = TaskTab.AWAITING)

        assertEquals(TaskContentEmptyKind.NO_AWAITING, model.emptyKind)
        assertNull(model.primaryAction)
        assertNull(model.actionLabelResId)
        assertEquals(TaskFabAction.CREATE_TASK, model.fabAction)
    }

    @Test
    fun myTabDoneOnlyWithTodoFilter_returnsNoResults() {
        val done = sampleTask(id = "1", assignedTo = USER, status = TaskStatus.DONE)
        val model = map(
            effectiveTab = TaskTab.MY,
            myBase = listOf(done),
            myTasks = emptyList(),
            filterType = TaskFilterType.TODO,
        )

        assertEquals(TaskContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(TaskContentAction.RESET_FILTERS, model.primaryAction)
    }

    @Test
    fun allTabCategoryFilterHidingTasks_returnsNoResults() {
        val task = sampleTask(id = "1", assignedTo = USER, categoryId = "cat-a")
        val model = map(
            effectiveTab = TaskTab.ALL,
            allBase = listOf(task),
            allTasks = emptyList(),
            filterType = TaskFilterType.ALL,
            selectedCategoryFilterId = "cat-b",
        )

        assertEquals(TaskContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(TaskContentAction.RESET_FILTERS, model.primaryAction)
    }

    @Test
    fun awaitingSearchHidingPending_returnsNoResults() {
        val pending = sampleTask(
            id = "1",
            assignedTo = USER,
            status = TaskStatus.WAITING_FOR_APPROVAL,
            title = "Clean kitchen",
        )
        val model = map(
            effectiveTab = TaskTab.AWAITING,
            awaitingBase = listOf(pending),
            awaiting = emptyList(),
            searchQuery = "zzz",
        )

        assertEquals(TaskContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(TaskContentAction.RESET_FILTERS, model.primaryAction)
    }

    @Test
    fun nonEmptyDisplayedList_returnsContent() {
        val task = sampleTask(id = "1", assignedTo = USER)
        val model = map(
            effectiveTab = TaskTab.MY,
            myBase = listOf(task),
            myTasks = listOf(task),
            filterType = TaskFilterType.TODO,
        )

        assertFalse(model.showsEmptyState)
        assertNull(model.primaryAction)
        assertEquals(TaskFabAction.CREATE_TASK, model.fabAction)
    }

    @Test
    fun sortTypeAlone_doesNotCreateNoResults() {
        val task = sampleTask(id = "1", assignedTo = USER)
        val model = map(
            effectiveTab = TaskTab.MY,
            myBase = listOf(task),
            myTasks = listOf(task),
            filterType = TaskFilterType.ALL,
        )

        assertFalse(model.showsEmptyState)
        assertNotEquals(TaskContentEmptyKind.NO_RESULTS, model.emptyKind)
    }

    @Test
    fun afterLoadingEmptyMyTab_showsNoMyTasks() {
        val loading = map(isLoading = true, effectiveTab = TaskTab.MY)
        val loaded = map(isLoading = false, effectiveTab = TaskTab.MY)

        assertFalse(loading.showsEmptyState)
        assertEquals(TaskFabAction.NONE, loading.fabAction)
        assertEquals(TaskContentEmptyKind.NO_MY_TASKS, loaded.emptyKind)
        assertEquals(TaskContentAction.CREATE_TASK, loaded.primaryAction)
    }

    @Test
    fun downgradeRequestedAwaitingWithMyTasks_usesEffectiveMyContentNotNoAwaiting() {
        val task = sampleTask(id = "1", assignedTo = USER)
        val requestedAwaiting = TaskTab.AWAITING
        val effectiveTab = TaskTabPolicy.resolveEffectiveTab(
            role = com.familyhub.domain.model.MemberRole.CHILD,
            requestedTab = requestedAwaiting,
        )
        val model = map(
            effectiveTab = effectiveTab,
            myBase = listOf(task),
            myTasks = listOf(task),
            awaitingBase = emptyList(),
            awaiting = emptyList(),
            filterType = TaskFilterType.TODO,
        )

        assertEquals(TaskTab.MY, effectiveTab)
        assertFalse(model.showsEmptyState)
        assertNull(model.emptyKind)
        assertNotEquals(TaskContentEmptyKind.NO_AWAITING, model.emptyKind)
        assertEquals(TaskFabAction.CREATE_TASK, model.fabAction)
    }

    @Test
    fun downgradeRequestedAwaitingWithEmptyMy_usesNoMyTasksNotNoAwaiting() {
        val requestedAwaiting = TaskTab.AWAITING
        val effectiveTab = TaskTabPolicy.resolveEffectiveTab(
            role = com.familyhub.domain.model.MemberRole.CHILD,
            requestedTab = requestedAwaiting,
        )
        val model = map(
            effectiveTab = effectiveTab,
            myBase = emptyList(),
            myTasks = emptyList(),
            awaitingBase = emptyList(),
            awaiting = emptyList(),
        )

        assertEquals(TaskTab.MY, effectiveTab)
        assertEquals(TaskContentEmptyKind.NO_MY_TASKS, model.emptyKind)
        assertNotEquals(TaskContentEmptyKind.NO_AWAITING, model.emptyKind)
    }

    @Test
    fun loadingIgnoresRequestedAndEffectiveTabForFab() {
        val model = map(
            isLoading = true,
            effectiveTab = TaskTab.AWAITING,
            awaitingBase = listOf(
                sampleTask(id = "1", assignedTo = USER, status = TaskStatus.WAITING_FOR_APPROVAL),
            ),
        )

        assertEquals(TaskFabAction.NONE, model.fabAction)
        assertFalse(model.showsEmptyState)
    }

    private fun map(
        isLoading: Boolean = false,
        effectiveTab: TaskTab = TaskTab.MY,
        myBase: List<Task> = emptyList(),
        allBase: List<Task> = emptyList(),
        awaitingBase: List<Task> = emptyList(),
        myTasks: List<Task> = emptyList(),
        allTasks: List<Task> = emptyList(),
        awaiting: List<Task> = emptyList(),
        searchQuery: String = "",
        filterType: TaskFilterType = TaskFilterType.TODO,
        selectedCategoryFilterId: String? = null,
    ): TaskContentUiModel = TaskContentUiStateMapper.map(
        isLoading = isLoading,
        effectiveTab = effectiveTab,
        myBase = myBase,
        allBase = allBase,
        awaitingBase = awaitingBase,
        myTasks = myTasks,
        allTasks = allTasks,
        awaiting = awaiting,
        searchQuery = searchQuery,
        filterType = filterType,
        selectedCategoryFilterId = selectedCategoryFilterId,
    )

    private fun sampleTask(
        id: String,
        assignedTo: String,
        status: TaskStatus = TaskStatus.TODO,
        title: String = "Task $id",
        categoryId: String? = null,
        createdBy: String = "creator",
        isPrivate: Boolean = false,
    ): Task = Task(
        id = id,
        title = title,
        description = null,
        createdByUserId = createdBy,
        assignedToUserId = assignedTo,
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = status,
        createdAt = 1L,
        orderIndex = 0,
        isPrivate = isPrivate,
        taskType = TaskType.ROUTINE,
        priority = TaskPriority.MEDIUM,
        categoryId = categoryId,
    )

    private companion object {
        const val USER = "user-1"
    }
}
