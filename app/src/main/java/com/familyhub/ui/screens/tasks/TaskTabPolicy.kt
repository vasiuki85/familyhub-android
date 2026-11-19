package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole

object TaskTabPolicy {
    fun availableTabs(role: MemberRole): List<TaskTab> = when (role) {
        MemberRole.ADMIN, MemberRole.PARENT -> listOf(TaskTab.MY, TaskTab.ALL, TaskTab.AWAITING)
        MemberRole.CHILD, MemberRole.PENDING -> listOf(TaskTab.MY, TaskTab.ALL)
    }

    fun resolveEffectiveTab(role: MemberRole, requestedTab: TaskTab): TaskTab {
        val available = availableTabs(role)
        return if (requestedTab in available) requestedTab else TaskTab.MY
    }
}
