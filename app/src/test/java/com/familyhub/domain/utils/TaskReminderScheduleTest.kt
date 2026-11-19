package com.familyhub.domain.utils

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskStatus
import com.familyhub.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReminderScheduleTest {
    private val baseDay = TimeUtils.getStartOfDay(1_700_000_000_000L)

    @Test
    fun resolveFireAtMillis_dateOnlyAtTimeMorning_returnsStartOfDayPlusMinutes() {
        val task = task(
            dueDate = baseDay,
            dueDateHasTime = false,
            reminderMode = TaskReminderMode.AT_TIME,
            reminderMinutesOfDay = 8 * 60,
        )

        assertEquals(baseDay + 8 * 60 * 60_000L, TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun resolveFireAtMillis_dateOnlyNone_returnsNull() {
        val task = task(
            dueDate = baseDay,
            dueDateHasTime = false,
            reminderMode = TaskReminderMode.NONE,
        )

        assertNull(TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun resolveFireAtMillis_hasTimeAtDue_returnsDueDate() {
        val dueDate = baseDay + 15 * 60 * 60_000L
        val task = task(
            dueDate = dueDate,
            dueDateHasTime = true,
            reminderMode = TaskReminderMode.AT_DUE,
        )

        assertEquals(dueDate, TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun resolveFireAtMillis_hasTimeOneHourBefore_returnsDueMinusOneHour() {
        val dueDate = baseDay + 15 * 60 * 60_000L
        val task = task(
            dueDate = dueDate,
            dueDateHasTime = true,
            reminderMode = TaskReminderMode.ONE_HOUR_BEFORE,
        )

        assertEquals(dueDate - 60 * 60_000L, TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun resolveFireAtMillis_atDueWithoutTime_returnsNull() {
        val task = task(
            dueDate = baseDay + 15 * 60 * 60_000L,
            dueDateHasTime = false,
            reminderMode = TaskReminderMode.AT_DUE,
        )

        assertNull(TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun resolveFireAtMillis_atTimeInvalidMinutes_returnsNull() {
        val task = task(
            dueDate = baseDay,
            dueDateHasTime = false,
            reminderMode = TaskReminderMode.AT_TIME,
            reminderMinutesOfDay = 2000,
        )

        assertNull(TaskReminderSchedule.resolveFireAtMillis(task))
    }

    @Test
    fun normalizeAfterClearingTime_atDueResetsToNoneAndClearsMinutes() {
        val (mode, minutes) = TaskReminderSchedule.normalizeAfterClearingTime(
            TaskReminderMode.AT_DUE,
            480,
        )

        assertEquals(TaskReminderMode.NONE, mode)
        assertNull(minutes)
    }

    @Test
    fun normalizeAfterClearingTime_atTimeKeepsMinutes() {
        val (mode, minutes) = TaskReminderSchedule.normalizeAfterClearingTime(
            TaskReminderMode.AT_TIME,
            480,
        )

        assertEquals(TaskReminderMode.AT_TIME, mode)
        assertEquals(480, minutes)
    }

    private fun task(
        dueDate: Long?,
        dueDateHasTime: Boolean = false,
        reminderMode: TaskReminderMode = TaskReminderMode.NONE,
        reminderMinutesOfDay: Int? = null,
    ): Task = Task(
        id = "task-1",
        title = "Task",
        description = null,
        createdByUserId = "creator",
        assignedToUserId = "self",
        dueDate = dueDate,
        dueDateHasTime = dueDateHasTime,
        reminderMode = reminderMode,
        reminderMinutesOfDay = reminderMinutesOfDay,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = baseDay,
    )
}
