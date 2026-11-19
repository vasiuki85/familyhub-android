package com.familyhub.ui.screens.penalties

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
import androidx.compose.material3.Text
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
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhMemberAvatar
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import com.familyhub.util.TimeUtils
import kotlin.math.abs

const val PENALTY_LIST_CARD_MAX_HEIGHT_DP = 120

@Composable
fun PenaltyListCard(
    penalty: Penalty,
    member: FamilyMember?,
    issuerName: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val userName = member?.displayLabel() ?: penalty.userId
    val reason = penalty.comment?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.penalties_reason_unknown)
    val issuedLine = stringResource(
        R.string.penalties_issued_line,
        issuerName,
        TimeUtils.formatDate(penalty.createdAt),
    )

    ThemedCard(
        tone = if (penalty.canceled) FhCardTone.NEUTRAL else FhCardTone.ATTENTION,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (member != null) {
                FhMemberAvatar(
                    member = member,
                    size = 36.dp,
                    decorative = true,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.penalties_points_deduction,
                            abs(penalty.amount),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.penalties_reason_label, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = issuedLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (penalty.canceled) {
                    Text(
                        text = stringResource(R.string.penalties_status_canceled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (onCancel != null) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = FhIcons.MoreVert,
                            contentDescription = stringResource(R.string.secondary_actions_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.penalties_cancel)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = FhIcons.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onCancel()
                            },
                        )
                    }
                }
            }
        }
    }
}
