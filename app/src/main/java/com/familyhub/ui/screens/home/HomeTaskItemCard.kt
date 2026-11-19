package com.familyhub.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskCategory
import com.familyhub.util.TimeUtils
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.TaskRotationSummary

@Composable
fun HomeTaskItemCard(
    task: Task,
    assignedToName: String,
    createdByName: String,
    category: TaskCategory? = null,
    onStatusChange: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    rotationSummary: TaskRotationSummary? = null,
    resolveMemberName: (String) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val attachmentOpenTitle = stringResource(R.string.task_attachment_open_title)
    val attachmentOpenError = stringResource(R.string.task_attachment_open_error)
    val isChecked = task.status == com.familyhub.domain.model.TaskStatus.DONE ||
        task.status == com.familyhub.domain.model.TaskStatus.WAITING_FOR_APPROVAL

    Card(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = isChecked,
                    role = Role.Checkbox,
                    onValueChange = { onStatusChange() },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isChecked) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    }
                )
                category?.let { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color))
                        )
                        Text(
                            text = stringResource(R.string.task_category_display, cat.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                val typeLabel = stringResource(task.taskType.titleRes)
                val pointsLabel = task.rewardPoints?.let {
                    stringResource(R.string.task_points_format, it)
                } ?: stringResource(R.string.task_points_none)
                val priorityLabel = stringResource(task.priority.shortTitleRes)
                val priorityColor = when (task.priority) {
                    com.familyhub.domain.model.TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                    com.familyhub.domain.model.TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                    com.familyhub.domain.model.TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
                }
                Text(
                    text = stringResource(R.string.task_type_with_points, typeLabel, pointsLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.task_priority_display, priorityLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = priorityColor
                )
                if (task.description != null) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(
                        R.string.task_assignment_format,
                        assignedToName,
                        createdByName
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.dueDate != null) {
                    Text(
                        text = TimeUtils.formatDate(task.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val isOverdue = task.dueDate < TimeUtils.now() &&
                        task.status == com.familyhub.domain.model.TaskStatus.TODO
                    if (isOverdue) {
                        Text(
                            text = stringResource(R.string.task_overdue_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                rotationSummary?.let { summary ->
                    Text(
                        text = summary.membersText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.nextAssignmentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.attachments.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.task_attachments_section_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    task.attachments.forEach { attachment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    val uri = Uri.parse(attachment.uri)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, attachment.mimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(intent, attachmentOpenTitle))
                                    }.onFailure {
                                        Toast.makeText(context, attachmentOpenError, Toast.LENGTH_LONG).show()
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = FhIcons.AttachFile,
                                contentDescription = stringResource(R.string.task_attachment_icon_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            ) {
                                Text(
                                    text = attachment.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                attachment.mimeType?.let { mime ->
                                    Text(
                                        text = stringResource(R.string.task_attachment_type_label, mime),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                if (task.comments.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.task_comments_section_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.comments.sortedBy { it.createdAt }.forEach { comment ->
                            val authorName = resolveMemberName(comment.authorId)
                                ?: stringResource(R.string.task_comment_unknown_author)
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = FhIcons.Chat,
                                    contentDescription = stringResource(R.string.task_comment_icon_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(
                                            R.string.task_comment_author_message_format,
                                            authorName,
                                            comment.message
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.task_comment_timestamp,
                                            TimeUtils.formatDateTime(comment.createdAt)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (onEdit != null || onDelete != null || onHistory != null) {
                Row {
                    if (onHistory != null) {
                        IconButton(onClick = onHistory) {
                            Icon(
                                imageVector = FhIcons.History,
                                contentDescription = stringResource(R.string.task_history_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = FhIcons.Edit,
                                contentDescription = stringResource(R.string.task_edit_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = FhIcons.Delete,
                                contentDescription = stringResource(R.string.task_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
