package com.familyhub.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.Reward
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhProgressBar
import com.familyhub.ui.components.FhSwitchRow
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard

const val REWARD_REMAINING_POINTS_TAG = "reward_remaining_points"
const val REWARD_ACTIVE_SWITCH_TAG = "reward_active_switch"
const val REWARD_CREATOR_TAG = "reward_creator_label"

@Composable
fun RewardCatalogCard(
    reward: Reward,
    canManage: Boolean,
    canRequest: Boolean,
    currentBalance: Int,
    creatorName: String,
    onRequest: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showManageDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = RewardProgressUiMapper.map(
        currentBalance = currentBalance,
        rewardCost = reward.cost,
        isActive = reward.isActive,
    )
    val rewardColors = FhTheme.modules.reward
    val progressDescription = stringResource(
        R.string.rewards_progress_cd,
        progress.progressBalance,
        reward.cost.coerceAtLeast(0),
    )
    val rewardDescription = stringResource(
        R.string.rewards_catalog_item_cd,
        reward.title,
        reward.cost,
    )
    val remainingText = when {
        !reward.isActive -> stringResource(R.string.rewards_inactive_friendly_hint)
        progress.canRequest -> stringResource(R.string.rewards_progress_ready)
        else -> stringResource(R.string.rewards_progress_remaining, progress.remainingPoints)
    }

    ThemedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = rewardDescription
            },
        tone = if (progress.canRequest) FhCardTone.REWARD else FhCardTone.NEUTRAL,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(rewardColors.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FhIcons.EmojiEvents,
                    contentDescription = null,
                    tint = rewardColors.accent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reward.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        reward.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.rewards_cost_points, reward.cost),
                            style = MaterialTheme.typography.labelLarge,
                            color = rewardColors.accent,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (canManage) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = FhIcons.MoreVert,
                                    contentDescription = stringResource(
                                        R.string.secondary_actions_cd,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rewards_edit_action)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = FhIcons.Edit,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showManageDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        FhProgressBar(
            progress = progress.progress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(CircleShape),
            color = rewardColors.accent,
            trackColor = rewardColors.container,
            contentDescription = progressDescription,
            height = 8.dp,
        )
        Text(
            text = remainingText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (progress.canRequest) {
                rewardColors.accent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(REWARD_REMAINING_POINTS_TAG),
            fontWeight = if (progress.canRequest) FontWeight.SemiBold else FontWeight.Normal,
        )
        Button(
            onClick = onRequest,
            enabled = progress.canRequest && canRequest,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.rewards_request_button))
        }
    }

    if (showManageDialog) {
        RewardManageDialog(
            reward = reward,
            creatorName = creatorName,
            onDismiss = { showManageDialog = false },
            onToggleActive = onToggleActive,
        )
    }
}

@Composable
private fun RewardManageDialog(
    reward: Reward,
    creatorName: String,
    onDismiss: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.rewards_edit_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = reward.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.rewards_created_by, creatorName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(REWARD_CREATOR_TAG),
                )
                FhSwitchRow(
                    checked = reward.isActive,
                    onCheckedChange = onToggleActive,
                    label = stringResource(R.string.rewards_active_label),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag(REWARD_ACTIVE_SWITCH_TAG),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        },
    )
}
