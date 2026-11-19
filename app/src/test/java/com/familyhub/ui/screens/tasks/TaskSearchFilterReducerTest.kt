package com.familyhub.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSearchFilterReducerTest {

    @Test
    fun apply_clearsSearchStatusAndCategory_preservesSortAndTab() {
        val state = TasksViewState(
            selectedTab = TaskTab.ALL,
            filterType = TaskFilterType.TODO,
            sortType = TaskSortType.PRIORITY,
            searchQuery = "milk",
            selectedCategoryFilterId = "cat-a",
        )

        val result = TaskSearchFilterReducer.apply(state)

        assertTrue(result.searchQuery.isEmpty())
        assertEquals(TaskFilterType.ALL, result.filterType)
        assertNull(result.selectedCategoryFilterId)
        assertEquals(TaskSortType.PRIORITY, result.sortType)
        assertEquals(TaskTab.ALL, result.selectedTab)
    }
}
