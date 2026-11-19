package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole

object TaskManualReorderPolicy {
    fun canReorder(
        isLoading: Boolean,
        role: MemberRole,
        effectiveTab: TaskTab,
        sortType: TaskSortType,
        filterType: TaskFilterType,
        searchQuery: String,
        selectedCategoryFilterId: String?,
        displayedCount: Int,
    ): Boolean {
        if (isLoading) return false
        if (role != MemberRole.ADMIN && role != MemberRole.PARENT) return false
        if (effectiveTab != TaskTab.ALL) return false
        if (sortType != TaskSortType.MANUAL) return false
        if (filterType != TaskFilterType.ALL) return false
        if (searchQuery.isNotBlank()) return false
        if (selectedCategoryFilterId != null) return false
        return displayedCount >= 2
    }

    fun showsManualHint(
        sortType: TaskSortType,
        effectiveTab: TaskTab,
    ): Boolean = sortType == TaskSortType.MANUAL && effectiveTab != TaskTab.AWAITING
}
