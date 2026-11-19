package com.familyhub.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.components.FhIcons

@Composable
fun FamilyActivationChecklistCard(
    model: FamilyActivationChecklist,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!model.visible) return
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(model.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(model.messageRes), style = MaterialTheme.typography.bodyMedium)
            ChecklistRow(R.string.home_activation_step_connected, true)
            ChecklistRow(
                R.string.home_activation_step_member,
                model.memberStepComplete,
            )
            ChecklistRow(R.string.home_activation_step_task, model.taskStepComplete)
            TextButton(onClick = onAction) {
                Text(
                    stringResource(
                        if (model.action == FamilyActivationAction.SETTINGS) {
                            R.string.home_activation_open_family
                        } else {
                            R.string.home_activation_create_task
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(@androidx.annotation.StringRes labelRes: Int, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (complete) FhIcons.CheckCircle else FhIcons.AccessTime,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(20.dp),
            tint = if (complete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(stringResource(labelRes))
    }
}
