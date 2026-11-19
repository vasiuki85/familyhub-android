package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManualReorderPolicyTest {

    @Test
    fun allowsAdminParentAllManualNoFilters() {
        assertTrue(
            TaskManualReorderPolicy.canReorder(
                isLoading = false,
                role = MemberRole.ADMIN,
                effectiveTab = TaskTab.ALL,
                sortType = TaskSortType.MANUAL,
                filterType = TaskFilterType.ALL,
                searchQuery = "",
                selectedCategoryFilterId = null,
                displayedCount = 2,
            )
        )
        assertTrue(
            TaskManualReorderPolicy.canReorder(
                isLoading = false,
                role = MemberRole.PARENT,
                effectiveTab = TaskTab.ALL,
                sortType = TaskSortType.MANUAL,
                filterType = TaskFilterType.ALL,
                searchQuery = "",
                selectedCategoryFilterId = null,
                displayedCount = 3,
            )
        )
    }

    @Test
    fun deniesChildAndPending() {
        assertFalse(policy(role = MemberRole.CHILD))
        assertFalse(policy(role = MemberRole.PENDING))
    }

    @Test
    fun deniesMyAndAwaitingTabs() {
        assertFalse(policy(effectiveTab = TaskTab.MY))
        assertFalse(policy(effectiveTab = TaskTab.AWAITING))
    }

    @Test
    fun deniesSearchStatusAndCategoryFilters() {
        assertFalse(policy(searchQuery = "milk"))
        assertFalse(policy(filterType = TaskFilterType.TODO))
        assertFalse(policy(selectedCategoryFilterId = "cat-a"))
        assertFalse(policy(sortType = TaskSortType.DATE_NEWEST))
    }

    @Test
    fun deniesLoadingAndSingleItem() {
        assertFalse(policy(isLoading = true))
        assertFalse(policy(displayedCount = 1))
    }

    private fun policy(
        isLoading: Boolean = false,
        role: MemberRole = MemberRole.ADMIN,
        effectiveTab: TaskTab = TaskTab.ALL,
        sortType: TaskSortType = TaskSortType.MANUAL,
        filterType: TaskFilterType = TaskFilterType.ALL,
        searchQuery: String = "",
        selectedCategoryFilterId: String? = null,
        displayedCount: Int = 2,
    ): Boolean = TaskManualReorderPolicy.canReorder(
        isLoading = isLoading,
        role = role,
        effectiveTab = effectiveTab,
        sortType = sortType,
        filterType = filterType,
        searchQuery = searchQuery,
        selectedCategoryFilterId = selectedCategoryFilterId,
        displayedCount = displayedCount,
    )
}
