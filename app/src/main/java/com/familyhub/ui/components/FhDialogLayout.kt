package com.familyhub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.familyhub.ui.theme.FhTheme

/**
 * Shared body for input-heavy dialogs.
 *
 * AlertDialog constrains its surface, but does not make arbitrary form content scrollable.
 * Keeping the scroll and IME handling here prevents the action row from being pushed off-screen.
 */
@Composable
fun FhDialogFormBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.58f)
        .coerceAtLeast(240.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxBodyHeight)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
        content = content,
    )
}

/** Minimum accessible touch height for dialog actions and selectable rows. */
fun Modifier.fhDialogAction(): Modifier = heightIn(min = 48.dp)
