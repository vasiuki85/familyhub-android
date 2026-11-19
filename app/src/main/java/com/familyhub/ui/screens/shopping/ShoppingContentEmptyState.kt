package com.familyhub.ui.screens.shopping

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhIcons

@Composable
fun ShoppingContentEmptyState(
    model: ShoppingContentUiModel,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleResId = model.titleResId ?: return
    val messageResId = model.messageResId ?: return
    val actionLabelResId = model.actionLabelResId
    val hasAction = actionLabelResId != null && model.primaryAction != null

    FhEmptyState(
        icon = FhIcons.ShoppingCart,
        title = stringResource(titleResId),
        description = stringResource(messageResId),
        actionLabel = actionLabelResId?.let { stringResource(it) },
        onAction = if (hasAction) onPrimaryAction else null,
        modifier = modifier,
    )
}
