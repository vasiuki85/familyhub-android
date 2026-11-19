package com.familyhub.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SyncHealthCard(
    model: SyncHealthUiModel,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!model.visible || model.messageResId == null) {
        return
    }

    val colors = when (model.severity) {
        SyncHealthSeverity.ERROR -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        SyncHealthSeverity.WARNING -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SyncHealthSeverity.INFO -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

    val bannerDescription = model.contentDescriptionResId?.let { stringResource(it) }
        ?: stringResource(model.messageResId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = bannerDescription
            },
        colors = colors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(model.messageResId),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (model.showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val actionLabelResId = model.actionLabelResId
            if (actionLabelResId != null && model.action != SyncHealthAction.NONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onActionClick,
                        enabled = model.actionEnabled,
                    ) {
                        Text(text = stringResource(actionLabelResId))
                    }
                }
            }
        }
    }
}
