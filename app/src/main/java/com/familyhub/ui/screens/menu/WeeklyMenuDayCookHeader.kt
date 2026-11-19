package com.familyhub.ui.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.components.FhIcons

@Composable
internal fun WeeklyMenuDayCookHeader(
    assignedMemberName: String?,
    canManageMenu: Boolean,
    readOnlyEditCd: String,
    onChangeCook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = assignedMemberName?.let {
        stringResource(R.string.menu_assigned_to, it)
    } ?: stringResource(R.string.menu_assign_placeholder)
    val changeCookCd = stringResource(R.string.menu_change_day_cook_cd)

    TextButton(
        onClick = onChangeCook,
        enabled = canManageMenu,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (canManageMenu) changeCookCd else readOnlyEditCd
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = FhIcons.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
