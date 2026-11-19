package com.familyhub.ui.screens.home

import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDashboardUiMapperTest {

    @Test
    fun map_prefersAuthenticatedMemberAndKeepsFamilyOrder() {
        val members = listOf(
            member("parent"),
            member("child"),
            member("guest"),
        )

        val result = HomeDashboardUiMapper.map(
            HomeUiState(
                currentUserId = "child",
                members = members,
            ),
        )

        assertEquals("child", result.currentMember?.id)
        assertEquals(members, result.familyMembers)
    }

    @Test
    fun map_limitsDashboardContentAndReportsRemainingTasks() {
        val tasks = (1..5).map { task("task-$it") }
        val events = (1..3).map { occurrence("event-$it", it.toLong()) }

        val result = HomeDashboardUiMapper.map(
            HomeUiState(
                myTasksToday = tasks,
                upcomingEvents = events,
            ),
        )

        assertEquals(tasks.take(3), result.priorityTasks)
        assertEquals(2, result.remainingTaskCount)
        assertEquals(events.take(2), result.upcomingEvents)
    }

    @Test
    fun map_emptyStateHasNoCurrentMemberAndNoNegativeRemainingCount() {
        val result = HomeDashboardUiMapper.map(HomeUiState())

        assertNull(result.currentMember)
        assertEquals(emptyList<Task>(), result.priorityTasks)
        assertEquals(0, result.remainingTaskCount)
        assertEquals(emptyList<EventOccurrence>(), result.upcomingEvents)
    }

    @Test
    fun map_missingAuthenticatedMemberDoesNotFallBackToAnotherProfile() {
        val result = HomeDashboardUiMapper.map(
            HomeUiState(
                currentUserId = "missing-user",
                members = listOf(member("parent"), member("child")),
            ),
        )

        assertNull(result.currentMember)
        assertEquals(listOf(member("parent"), member("child")), result.familyMembers)
    }

    private fun member(id: String) = FamilyMember(
        id = id,
        displayName = id,
        roleLabel = "",
        avatarColor = "",
        role = MemberRole.CHILD,
    )

    private fun task(id: String) = Task(
        id = id,
        title = id,
        description = null,
        createdByUserId = "parent",
        assignedToUserId = "child",
        dueDate = null,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 1L,
    )

    private fun occurrence(id: String, startTime: Long) = EventOccurrence(
        event = FamilyEvent(
            id = id,
            title = id,
            startDateTime = startTime,
            createdByUserId = "parent",
            durationMinutes = 30,
            recurrence = EventRecurrence.NONE,
            reminderMinutesBefore = null,
        ),
        startTime = startTime,
    )
}
