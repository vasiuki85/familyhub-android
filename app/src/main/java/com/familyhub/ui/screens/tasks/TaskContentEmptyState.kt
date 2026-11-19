package com.familyhub.ui.screens.tasks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.theme.components.FHTonalButton

@Composable
fun TaskContentEmptyState(
    model: TaskContentUiModel,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleResId = model.titleResId ?: return
    val messageResId = model.messageResId ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (model.showsFamilyEmptyIllustration) {
            Image(
                painter = painterResource(R.drawable.fh_illustration_tasks_empty_v2),
                contentDescription = stringResource(R.string.tasks_v2_empty_illustration_cd),
                modifier = Modifier.size(180.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val actionLabelResId = model.actionLabelResId
        if (actionLabelResId != null && model.primaryAction != null) {
            val actionContentDescription = model.actionContentDescriptionResId?.let { stringResource(it) }
                ?: stringResource(actionLabelResId)
            FHTonalButton(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = actionContentDescription },
            ) {
                Text(stringResource(actionLabelResId))
            }
        }
    }
}
