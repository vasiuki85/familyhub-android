package com.familyhub.ui.screens.home

import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.Task

data class HomeDashboardUiModel(
    val currentMember: FamilyMember?,
    val familyMembers: List<FamilyMember>,
    val priorityTasks: List<Task>,
    val remainingTaskCount: Int,
    val upcomingEvents: List<EventOccurrence>,
)

object HomeDashboardUiMapper {
    private const val PRIORITY_TASK_LIMIT = 3
    private const val FAMILY_AVATAR_LIMIT = 4
    private const val UPCOMING_EVENT_LIMIT = 2

    fun map(state: HomeUiState): HomeDashboardUiModel {
        val currentMember = state.currentUserId
            .takeIf { it.isNotBlank() }
            ?.let { currentUserId ->
                state.members.firstOrNull { it.id == currentUserId }
            }
        val priorityTasks = state.myTasksToday.take(PRIORITY_TASK_LIMIT)

        return HomeDashboardUiModel(
            currentMember = currentMember,
            familyMembers = state.members.take(FAMILY_AVATAR_LIMIT),
            priorityTasks = priorityTasks,
            remainingTaskCount = (state.myTasksToday.size - priorityTasks.size).coerceAtLeast(0),
            upcomingEvents = state.upcomingEvents.take(UPCOMING_EVENT_LIMIT),
        )
    }
}
