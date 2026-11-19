package com.familyhub.ui.screens.awesomeness

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.AwesomenessEntry
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard

const val AWESOMENESS_ENTRY_MIN_HEIGHT_DP = 88
const val AWESOMENESS_ENTRY_COLLAPSED_MAX_LINES = 3

@Composable
fun AwesomenessEntryCard(
    entry: AwesomenessEntry,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var textOverflowing by remember { mutableStateOf(false) }

    ThemedCard(
        modifier = modifier.fillMaxWidth(),
        tone = FhCardTone.REWARD,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AWESOMENESS_ENTRY_MIN_HEIGHT_DP.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = FhIcons.ThumbUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (textExpanded) Int.MAX_VALUE else AWESOMENESS_ENTRY_COLLAPSED_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
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
            }
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
                        text = { Text(stringResource(R.string.secondary_action_edit)) },
                        leadingIcon = {
                            Icon(
                                imageVector = FhIcons.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onEdit()
                        },
                    )
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
}
