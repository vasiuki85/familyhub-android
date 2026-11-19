package com.familyhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskPriority
import com.familyhub.domain.model.TaskStatus
import com.familyhub.util.TimeUtils
import com.familyhub.ui.theme.FhTheme

/** Maximum height budget for a two-line title at phone width (see [TaskItemCardLayoutTest]). */
const val TASK_LIST_ROW_MAX_HEIGHT_DP = 120

const val TASK_CATEGORY_STRIPE_TEST_TAG = "task_category_stripe"
const val TASK_PENDING_STATUS_TEST_TAG = "task_pending_status"
const val TASK_CONFIRM_COMPLETION_ACTION_TEST_TAG = "task_confirm_completion_action"
const val TASK_DECLINE_COMPLETION_ACTION_TEST_TAG = "task_decline_completion_action"

@Composable
fun TaskItemCard(
    task: Task,
    assignedToName: String,
    onStatusChange: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onConfirmCompletion: (() -> Unit)? = null,
    onDeclineCompletion: (() -> Unit)? = null,
    categoryColor: Color? = null,
    modifier: Modifier = Modifier,
    titleSemanticsModifier: Modifier = Modifier,
) {
    val toggleCompletionLabel = stringResource(R.string.task_toggle_completion_cd, task.title)
    val taskColors = FhTheme.modules.task
    val isAwaitingApproval = task.status == TaskStatus.WAITING_FOR_APPROVAL
    val isComplete = task.status == TaskStatus.DONE || isAwaitingApproval
    val showApprovalActions = onConfirmCompletion != null || onDeclineCompletion != null
    var showActionsMenu by remember { mutableStateOf(false) }
    val hasMenuActions = onEdit != null || onHistory != null || onDelete != null ||
        onConfirmCompletion != null || onDeclineCompletion != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .then(
                if (onEdit != null) {
                    Modifier.clickable(onClick = onEdit)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(FhTheme.radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            categoryColor?.let { stripeColor ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(stripeColor)
                        .testTag(TASK_CATEGORY_STRIPE_TEST_TAG),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (categoryColor != null) 0.dp else 2.dp,
                        end = 2.dp,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
            Checkbox(
                checked = isComplete,
                onCheckedChange = onStatusChange?.let { action ->
                    { _: Boolean -> action() }
                },
                enabled = onStatusChange != null,
                colors = CheckboxDefaults.colors(
                    checkedColor = if (isAwaitingApproval) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        taskColors.accent
                    },
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkmarkColor = if (isAwaitingApproval) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        taskColors.onAccent
                    },
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = toggleCompletionLabel },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
                    .then(titleSemanticsModifier),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isComplete) TextDecoration.LineThrough else null,
                )
                Text(
                    text = buildTaskMetaLine(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isAwaitingApproval) {
                    Text(
                        text = stringResource(R.string.task_confirmation_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = taskColors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(TASK_PENDING_STATUS_TEST_TAG),
                    )
                }
                val now = remember { TimeUtils.now() }
                val isOverdue = task.dueDate != null && task.dueDate < now &&
                    task.status == TaskStatus.TODO
                if (isOverdue) {
                    Text(
                        text = stringResource(R.string.task_overdue_badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildTaskAssigneeDueLine(task, assignedToName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasMenuActions) {
                        Box {
                            IconButton(
                                onClick = { showActionsMenu = true },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = FhIcons.MoreVert,
                                    contentDescription = stringResource(
                                        R.string.task_more_actions_cd,
                                        task.title,
                                    ),
                                )
                            }
                            TaskOverflowMenu(
                                expanded = showActionsMenu,
                                onDismiss = { showActionsMenu = false },
                                onEdit = onEdit,
                                onHistory = onHistory,
                                onConfirmCompletion = onConfirmCompletion,
                                onDeclineCompletion = onDeclineCompletion,
                                onDelete = onDelete,
                            )
                        }
                    }
                }
                if (showApprovalActions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        onConfirmCompletion?.let { confirm ->
                            TextButton(
                                onClick = confirm,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag(TASK_CONFIRM_COMPLETION_ACTION_TEST_TAG),
                            ) {
                                Text(
                                    text = stringResource(R.string.task_confirm_completion_cd),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        onDeclineCompletion?.let { decline ->
                            TextButton(
                                onClick = decline,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag(TASK_DECLINE_COMPLETION_ACTION_TEST_TAG),
                            ) {
                                Text(
                                    text = stringResource(R.string.task_decline_completion_cd),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun TaskOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onHistory: (() -> Unit)?,
    onConfirmCompletion: (() -> Unit)?,
    onDeclineCompletion: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val taskColors = FhTheme.modules.task
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        onEdit?.let { edit ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_edit_cd)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.Edit,
                        contentDescription = null,
                        tint = taskColors.accent,
                    )
                },
                onClick = {
                    onDismiss()
                    edit()
                },
            )
        }
        onHistory?.let { history ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_repeat)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.History,
                        contentDescription = null,
                        tint = taskColors.accent,
                    )
                },
                onClick = {
                    onDismiss()
                    history()
                },
            )
        }
        onConfirmCompletion?.let { confirm ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_confirm_completion_cd)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.Check,
                        contentDescription = null,
                        tint = taskColors.accent,
                    )
                },
                onClick = {
                    onDismiss()
                    confirm()
                },
            )
        }
        onDeclineCompletion?.let { decline ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_decline_completion_cd)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onDismiss()
                    decline()
                },
            )
        }
        onDelete?.let { delete ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_delete_cd)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onDismiss()
                    delete()
                },
            )
        }
    }
}

@Composable
private fun buildTaskMetaLine(task: Task): String {
    val typeLabel = stringResource(task.taskType.titleRes)
    val parts = mutableListOf(typeLabel)
    if (task.priority != TaskPriority.MEDIUM) {
        parts += stringResource(task.priority.titleRes)
    }
    return parts.joinToString(separator = stringResource(R.string.task_meta_separator))
}

@Composable
private fun buildTaskAssigneeDueLine(task: Task, assignedToName: String): String {
    if (task.dueDate != null) {
        val dueText = if (task.dueDateHasTime) {
            TimeUtils.formatDateTime(task.dueDate)
        } else {
            TimeUtils.formatDate(task.dueDate)
        }
        return stringResource(
            R.string.task_list_assignee_due_format,
            assignedToName,
            stringResource(R.string.task_due_label, dueText),
        )
    }
    return assignedToName
}

data class TaskRotationSummary(
    val membersText: String,
    val nextAssignmentText: String,
)
