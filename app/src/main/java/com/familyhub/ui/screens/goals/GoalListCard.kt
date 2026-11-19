package com.familyhub.ui.screens.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhProgressBar
import com.familyhub.ui.theme.FhTheme

/** Maximum height budget for a compact goal list card at phone width (see [GoalListCardLayoutTest]). */
const val GOAL_LIST_CARD_MAX_HEIGHT_DP = 172

fun goalListCardTestTag(goalId: String): String = "goal_list_card_$goalId"

@Composable
fun GoalListCard(
    item: FamilyGoalListItem,
    canManageGoal: Boolean,
    canParticipate: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAddProgress: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goalColors = FhTheme.modules.goals
    val cardModifier = modifier
        .fillMaxWidth()
        .testTag(goalListCardTestTag(item.goal.id))
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = FhIcons.Flag,
                    contentDescription = null,
                    tint = goalColors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = item.goal.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = goalColors.onContainer,
                    modifier = Modifier.weight(1f),
                )
                if (canManageGoal) {
                    GoalOverflowMenu(
                        goal = item.goal,
                        canManageGoal = canManageGoal,
                        hasRemainingSteps = item.goal.status == FamilyGoalStatus.ACTIVE &&
                            canParticipate &&
                            (item.remainingSteps > 0 || item.totalSteps == 0),
                        onEdit = onEdit,
                        onAddProgress = onAddProgress,
                        onComplete = onComplete,
                        onReopen = onReopen,
                        onArchive = onArchive,
                        onDelete = onDelete,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.goals_progress_format,
                    item.completedSteps,
                    item.totalSteps,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = goalColors.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.totalSteps > 0) {
                FhProgressBar(
                    progress = item.progressPercent,
                    modifier = Modifier.fillMaxWidth(),
                    height = 6.dp,
                    color = goalColors.accent,
                    trackColor = goalColors.container,
                )
            }

            item.nextStepTitle?.let { nextStep ->
                Text(
                    text = stringResource(R.string.goals_next_step_label, nextStep),
                    style = MaterialTheme.typography.bodySmall,
                    color = goalColors.onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            item.deadlineLabel?.let { deadline ->
                Text(
                    text = stringResource(R.string.goals_deadline_label, deadline),
                    style = MaterialTheme.typography.bodySmall,
                    color = goalColors.onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Surface(
        modifier = cardModifier,
        color = goalColors.container,
        shape = MaterialTheme.shapes.medium,
        content = cardContent,
    )
}

@Composable
internal fun GoalOverflowMenu(
    goal: FamilyGoal,
    canManageGoal: Boolean,
    hasRemainingSteps: Boolean,
    onEdit: () -> Unit,
    onAddProgress: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }

    if (!canManageGoal) return

    Box {
        IconButton(onClick = { actionsExpanded = true }) {
            Icon(
                imageVector = FhIcons.MoreVert,
                contentDescription = stringResource(R.string.secondary_actions_cd),
            )
        }
        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { actionsExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.goals_action_edit)) },
                leadingIcon = {
                    Icon(imageVector = FhIcons.Edit, contentDescription = null)
                },
                onClick = {
                    actionsExpanded = false
                    onEdit()
                },
            )
            if (goal.status == FamilyGoalStatus.ACTIVE && hasRemainingSteps) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.goals_action_add_progress)) },
                    leadingIcon = {
                        Icon(imageVector = FhIcons.TrendingUp, contentDescription = null)
                    },
                    onClick = {
                        actionsExpanded = false
                        onAddProgress()
                    },
                )
            }
            when (goal.status) {
                FamilyGoalStatus.ACTIVE -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goals_action_mark_complete)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.CheckCircle, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onComplete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goals_action_archive)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.Archive, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onArchive()
                        },
                    )
                }
                FamilyGoalStatus.COMPLETED -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goals_action_reopen)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.Refresh, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onReopen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goals_action_archive)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.Archive, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onArchive()
                        },
                    )
                }
                FamilyGoalStatus.ARCHIVED -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goals_action_reopen)) },
                        leadingIcon = {
                            Icon(imageVector = FhIcons.Refresh, contentDescription = null)
                        },
                        onClick = {
                            actionsExpanded = false
                            onReopen()
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.secondary_action_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = FhIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    actionsExpanded = false
                    onDelete()
                },
            )
        }
    }
}
