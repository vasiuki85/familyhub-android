package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.TaskType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskItemCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun denseTaskRow_meetsHeightBudgetAtPhoneWidth() {
        val task = sampleTask(
            title = "Prepare potato soup with dumplings for the whole family dinner",
        )
        var rowHeightPx = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    TaskItemCard(
                        task = task,
                        assignedToName = "Ivan",
                        onStatusChange = {},
                        onEdit = {},
                        onDelete = {},
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            rowHeightPx = coordinates.size.height
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = rowHeightPx / composeRule.density.density
        assertTrue(
            "Task row height ${heightDp}dp exceeds budget ${TASK_LIST_ROW_MAX_HEIGHT_DP}dp",
            heightDp <= TASK_LIST_ROW_MAX_HEIGHT_DP,
        )
    }

    @Test
    fun categorisedTask_exposesCategoryStripeTestTag() {
        val task = sampleTask(title = "Vacuum living room").copy(categoryId = "cat-home")

        composeRule.setContent {
            MaterialTheme {
                TaskItemCard(
                    task = task,
                    assignedToName = "Ivan",
                    onStatusChange = {},
                    categoryColor = Color(0xFF4CAF50),
                )
            }
        }

        composeRule.onNodeWithTag(TASK_CATEGORY_STRIPE_TEST_TAG).assertExists()
    }

    @Test
    fun uncategorisedTask_hidesCategoryStripeTestTag() {
        val task = sampleTask(title = "Vacuum living room")

        composeRule.setContent {
            MaterialTheme {
                TaskItemCard(
                    task = task,
                    assignedToName = "Ivan",
                    onStatusChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(TASK_CATEGORY_STRIPE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun deleteOverflowAction_requiresConfirmationBeforeDeleting() {
        val task = sampleTask(title = "Wash dishes")

        composeRule.setContent {
            var showDeleteConfirmation by remember { mutableStateOf(false) }
            MaterialTheme {
                TaskItemCard(
                    task = task,
                    assignedToName = "Ivan",
                    onStatusChange = {},
                    onEdit = {},
                    onDelete = { showDeleteConfirmation = true },
                )
                if (showDeleteConfirmation) {
                    Text(
                        text = "delete-confirmation",
                        modifier = Modifier.testTag(DELETE_CONFIRM_TAG),
                    )
                }
            }
        }

        val moreActionsLabel = composeRule.activity.getString(
            R.string.task_more_actions_cd,
            task.title,
        )
        composeRule.onNodeWithContentDescription(moreActionsLabel).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_delete_cd),
        ).performClick()

        composeRule.onNodeWithTag(DELETE_CONFIRM_TAG).assertExists()
    }

    @Test
    fun waitingForApprovalTask_showsPendingStatusLine() {
        val task = sampleTask(title = "Clean room").copy(
            status = TaskStatus.WAITING_FOR_APPROVAL,
        )

        composeRule.setContent {
            MaterialTheme {
                TaskItemCard(
                    task = task,
                    assignedToName = "Ivan",
                    onStatusChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(TASK_PENDING_STATUS_TEST_TAG).assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_confirmation_pending),
        ).assertExists()
    }

    @Test
    fun waitingForApprovalTask_exposesConfirmAndDeclineActionsWhenCallbacksProvided() {
        val task = sampleTask(title = "Clean room").copy(
            status = TaskStatus.WAITING_FOR_APPROVAL,
        )
        var confirmed = false
        var declined = false

        composeRule.setContent {
            MaterialTheme {
                TaskItemCard(
                    task = task,
                    assignedToName = "Ivan",
                    onStatusChange = {},
                    onConfirmCompletion = { confirmed = true },
                    onDeclineCompletion = { declined = true },
                )
            }
        }

        composeRule.onNodeWithTag(TASK_CONFIRM_COMPLETION_ACTION_TEST_TAG).performClick()
        composeRule.onNodeWithTag(TASK_DECLINE_COMPLETION_ACTION_TEST_TAG).performClick()
        assertTrue(confirmed)
        assertTrue(declined)
    }

    private fun sampleTask(
        title: String,
        // Future due date: the height budget covers the standard dense row
        // (two-line title + meta + assignee line). A past due date adds the
        // overdue badge as an extra text line — a different, taller variant.
        dueDate: Long? = 4_102_444_800_000L,
        priority: TaskPriority = TaskPriority.HIGH,
    ): Task = Task(
        id = "task-1",
        title = title,
        description = null,
        createdByUserId = "creator",
        assignedToUserId = "assignee",
        dueDate = dueDate,
        repeatRule = RepeatRule.NONE,
        status = TaskStatus.TODO,
        createdAt = 1L,
        taskType = TaskType.ROUTINE,
        priority = priority,
    )

    private companion object {
        const val PHONE_WIDTH_DP = 411
        const val DELETE_CONFIRM_TAG = "task_delete_confirmation"
    }
}
