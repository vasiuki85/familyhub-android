package com.familyhub.ui.screens.awesomeness

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.AwesomenessEntry
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwesomenessScreen(
    viewModel: AwesomenessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEditorDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AwesomenessEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<AwesomenessEntry?>(null) }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_awesomeness),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        actions = {
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    imageVector = FhIcons.Help,
                    contentDescription = stringResource(R.string.awesomeness_help_cd)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    showEditorDialog = true
                }
            ) {
                Icon(
                    imageVector = FhIcons.Add,
                    contentDescription = stringResource(R.string.awesomeness_add_cd)
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                FhListSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            state.entries.isEmpty() -> {
                FhEmptyState(
                    icon = FhIcons.ThumbUp,
                    title = stringResource(R.string.awesomeness_empty_state),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = FhLayout.listBottomPadding(hasFloatingActionButton = true),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.entries,
                        key = { it.id }
                    ) { entry ->
                        AwesomenessEntryCard(
                            entry = entry,
                            modifier = Modifier.animateItem(),
                            onEdit = {
                                editingEntry = entry
                                showEditorDialog = true
                            },
                            onDelete = { entryToDelete = entry }
                        )
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        AwesomenessEditorDialog(
            isEditing = editingEntry != null,
            initialText = editingEntry?.text.orEmpty(),
            onDismiss = {
                showEditorDialog = false
                editingEntry = null
            },
            onConfirm = { text ->
                if (editingEntry == null) {
                    viewModel.addEntry(text)
                } else {
                    viewModel.updateEntry(editingEntry!!, text)
                }
                showEditorDialog = false
                editingEntry = null
            }
        )
    }

    if (showHelpDialog) {
        AwesomenessHelpDialog(onDismiss = { showHelpDialog = false })
    }

    entryToDelete?.let { entry ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.awesomeness_delete_title),
            message = stringResource(R.string.awesomeness_delete_message),
            onDismiss = { entryToDelete = null },
            onConfirm = {
                viewModel.deleteEntry(entry)
                entryToDelete = null
            }
        )
    }
}

@Composable
private fun AwesomenessEditorDialog(
    isEditing: Boolean,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(isEditing, initialText) { mutableStateOf(initialText) }
    var showError by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.awesomeness_editor_title_edit)
                } else {
                    stringResource(R.string.awesomeness_editor_title_new)
                }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (showError && it.isNotBlank()) {
                            showError = false
                        }
                    },
                    label = { Text(stringResource(R.string.awesomeness_field_text)) },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.awesomeness_error_empty_text),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (text.trim().isBlank()) {
                        showError = true
                    } else {
                        onConfirm(text)
                    }
                }
            ) {
                Text(
                    text = if (isEditing) {
                        stringResource(R.string.awesomeness_editor_save)
                    } else {
                        stringResource(R.string.awesomeness_editor_add)
                    }
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}


@Composable
private fun AwesomenessHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.awesomeness_help_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.awesomeness_help_intro),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.awesomeness_help_body_1),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.awesomeness_help_section_what),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.awesomeness_help_what_items),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.awesomeness_help_section_why),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.awesomeness_help_why_items),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.awesomeness_help_inspiration),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
