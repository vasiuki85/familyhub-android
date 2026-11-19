package com.familyhub.data.notification

import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskReminderMode
import com.familyhub.domain.model.TaskStatus
import com.familyhub.util.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDeadlineNotificationPolicyTest {
  private val now = 1_000_000L
  private val windowEnd = now + 24 * 60 * 60 * 1000L
  private val oneHour = 60 * 60 * 1000L
  private val baseDay = TimeUtils.getStartOfDay(now + 12 * oneHour)

  @Test
  fun shouldNotify_otherAssignee_returnsFalse() {
    val task = task(
      assignedTo = "other-user",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = 8 * 60,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_selfAssigneeFireAtInWindow_returnsTrue() {
    val fireAt = now + 60_000L
    val task = task(
      assignedTo = "self",
      dueDate = TimeUtils.getStartOfDay(fireAt),
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = ((fireAt - TimeUtils.getStartOfDay(fireAt)) / 60_000L).toInt(),
    )

    assertTrue(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_done_returnsFalse() {
    val task = task(
      assignedTo = "self",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = 8 * 60,
      status = TaskStatus.DONE,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_waitingForApproval_returnsFalse() {
    val task = task(
      assignedTo = "self",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = 8 * 60,
      status = TaskStatus.WAITING_FOR_APPROVAL,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_noDueDate_returnsFalse() {
    val task = task(assignedTo = "self", dueDate = null)

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_noReminderConfigured_returnsFalse() {
    val task = task(
      assignedTo = "self",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.NONE,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_blankCurrentUserId_returnsFalse() {
    val task = task(
      assignedTo = "self",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = 8 * 60,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_privateTaskAssignedToSelf_returnsTrue() {
    val fireAt = now + 60_000L
    val task = task(
      assignedTo = "self",
      dueDate = TimeUtils.getStartOfDay(fireAt),
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = ((fireAt - TimeUtils.getStartOfDay(fireAt)) / 60_000L).toInt(),
      isPrivate = true,
    )

    assertTrue(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_privateTaskAssignedToOther_returnsFalse() {
    val task = task(
      assignedTo = "other-user",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = 8 * 60,
      isPrivate = true,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldNotify_fireAtOutsideWindow_returnsFalse() {
    val fireAt = windowEnd + oneHour
    val task = task(
      assignedTo = "self",
      dueDate = TimeUtils.getStartOfDay(fireAt),
      reminderMode = TaskReminderMode.AT_TIME,
      reminderMinutesOfDay = ((fireAt - TimeUtils.getStartOfDay(fireAt)) / 60_000L).toInt(),
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldNotify(
        task = task,
        currentUserId = "self",
        now = now,
        windowEnd = windowEnd,
      ),
    )
  }

  @Test
  fun shouldScheduleOneTimeNotification_selfAssigneeFutureFireAt_returnsTrue() {
    val dueDate = now + 2 * oneHour
    val task = task(
      assignedTo = "self",
      dueDate = dueDate,
      dueDateHasTime = true,
      reminderMode = TaskReminderMode.AT_DUE,
    )

    assertTrue(
      TaskDeadlineNotificationPolicy.shouldScheduleOneTimeNotification(
        task = task,
        currentUserId = "self",
        now = now,
      ),
    )
  }

  @Test
  fun shouldScheduleOneTimeNotification_otherAssignee_returnsFalse() {
    val task = task(
      assignedTo = "other-user",
      dueDate = now + 2 * oneHour,
      dueDateHasTime = true,
      reminderMode = TaskReminderMode.AT_DUE,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldScheduleOneTimeNotification(
        task = task,
        currentUserId = "self",
        now = now,
      ),
    )
  }

  @Test
  fun shouldScheduleOneTimeNotification_fireAtAlreadyPassed_returnsFalse() {
    val dueDate = now + oneHour / 2
    val task = task(
      assignedTo = "self",
      dueDate = dueDate,
      dueDateHasTime = true,
      reminderMode = TaskReminderMode.ONE_HOUR_BEFORE,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldScheduleOneTimeNotification(
        task = task,
        currentUserId = "self",
        now = now,
      ),
    )
  }

  @Test
  fun shouldScheduleOneTimeNotification_dateOnlyWithoutReminder_returnsFalse() {
    val task = task(
      assignedTo = "self",
      dueDate = baseDay,
      reminderMode = TaskReminderMode.NONE,
    )

    assertFalse(
      TaskDeadlineNotificationPolicy.shouldScheduleOneTimeNotification(
        task = task,
        currentUserId = "self",
        now = now,
      ),
    )
  }

  private fun task(
    assignedTo: String,
    dueDate: Long?,
    dueDateHasTime: Boolean = false,
    reminderMode: TaskReminderMode = TaskReminderMode.NONE,
    reminderMinutesOfDay: Int? = null,
    status: TaskStatus = TaskStatus.TODO,
    isPrivate: Boolean = false,
  ): Task = Task(
    id = "task-1",
    title = "Task",
    description = null,
    createdByUserId = "creator",
    assignedToUserId = assignedTo,
    dueDate = dueDate,
    dueDateHasTime = dueDateHasTime,
    reminderMode = reminderMode,
    reminderMinutesOfDay = reminderMinutesOfDay,
    repeatRule = RepeatRule.NONE,
    status = status,
    createdAt = now,
    isPrivate = isPrivate,
  )
}
