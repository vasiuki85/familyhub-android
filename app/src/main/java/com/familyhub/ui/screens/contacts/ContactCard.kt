package com.familyhub.ui.screens.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.EmergencyContact
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.theme.FhTheme

/** Maximum height budget for a compact contact card at phone width (see [ContactCardLayoutTest]). */
const val CONTACT_CARD_MAX_HEIGHT_DP = 165

/** Semantics content description for the single root surface of [ContactCard]. */
const val CONTACT_CARD_SURFACE_CD = "contact_card_surface"

@Composable
fun ContactCard(
    contact: EmergencyContact,
    modifier: Modifier = Modifier,
    isEmergencyService: Boolean = isEmergencyServiceContact(contact),
    onCall: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    val relationshipLabel = contact.description?.takeIf { it.isNotBlank() }
    val callLabel = stringResource(R.string.contacts_call_name_cd, contact.name)
    val cardShape = RoundedCornerShape(FhTheme.radius.card)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = CONTACT_CARD_SURFACE_CD },
        shape = cardShape,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = if (isEmergencyService) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactTypeAvatar(isEmergencyService = isEmergencyService)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = contact.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                )
                relationshipLabel?.let { label ->
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                    )
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = callLabel },
                    ) {
                        Icon(
                            imageVector = FhIcons.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (onEdit != null || onDelete != null) {
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
                        onEdit?.let { edit ->
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
                                    edit()
                                },
                            )
                        }
                        onDelete?.let { delete ->
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
                                    delete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactTypeAvatar(isEmergencyService: Boolean) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = if (isEmergencyService) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isEmergencyService) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Icon(
            imageVector = if (isEmergencyService) FhIcons.Warning else FhIcons.Person,
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
        )
    }
}
