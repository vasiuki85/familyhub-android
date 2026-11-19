package com.familyhub.ui.screens.appreciations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhMemberAvatar
import com.familyhub.ui.screens.awesomeness.AWESOMENESS_ENTRY_COLLAPSED_MAX_LINES
import com.familyhub.ui.theme.FhTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface

/** Maximum height budget for a compact appreciation card at phone width (see [AppreciationCardLayoutTest]). */
const val APPRECIATION_CARD_MAX_HEIGHT_DP = 300

@Composable
fun AppreciationCard(
    item: AppreciationListItem,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var textOverflowing by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FhTheme.radius.card),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppreciationDirectionRow(
                    sender = item.sender,
                    senderName = item.senderName,
                    recipient = item.recipient,
                    recipientName = item.recipientName,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { actionsExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
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
            }
            Text(
                text = item.appreciation.message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (textExpanded) Int.MAX_VALUE else AWESOMENESS_ENTRY_COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                onTextLayout = { result ->
                    if (!textExpanded) {
                        textOverflowing = result.hasVisualOverflow
                    }
                },
            )
            if (textOverflowing || textExpanded) {
                TextButton(
                    onClick = { textExpanded = !textExpanded },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (textExpanded) {
                                R.string.awesomeness_show_less
                            } else {
                                R.string.awesomeness_show_more
                            },
                        ),
                    )
                }
            }
            Text(
                text = item.createdAtText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppreciationDirectionRow(
    sender: FamilyMember?,
    senderName: String,
    recipient: FamilyMember?,
    recipientName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sender != null) {
            FhMemberAvatar(member = sender, size = 28.dp, decorative = true)
        }
        Text(
            text = sender?.displayLabel() ?: senderName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = FhIcons.Forward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        if (recipient != null) {
            FhMemberAvatar(member = recipient, size = 28.dp, decorative = true)
        }
        Text(
            text = recipient?.displayLabel() ?: recipientName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
