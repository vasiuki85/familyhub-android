package com.familyhub.ui.screens.rules

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.familyhub.domain.model.MemberRole
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhSearchField
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: RulesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isEditorVisible by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<HouseRuleListItem?>(null) }
    var itemToDelete by remember { mutableStateOf<HouseRuleListItem?>(null) }

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(4000)
            viewModel.clearError()
        }
    }

    val canManageRules = state.currentUserRole == MemberRole.PARENT ||
        state.currentUserRole == MemberRole.ADMIN

    FhScreenScaffold(
        title = stringResource(R.string.nav_rules),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (canManageRules) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        isEditorVisible = true
                    }
                ) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.rules_add_fab_cd)
                    )
                }
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

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    FhSearchField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = stringResource(R.string.rules_search_hint),
                        searchContentDescription = stringResource(R.string.rules_search_cd),
                        clearContentDescription = stringResource(R.string.rules_search_clear_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (state.groupedRules.isEmpty()) {
                        FhEmptyState(
                            icon = FhIcons.Rule,
                            title = if (state.searchQuery.isEmpty()) {
                                stringResource(R.string.rules_empty_state)
                            } else {
                                stringResource(R.string.search_no_results)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                bottom = FhLayout.listBottomPadding(canManageRules),
                            ),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                        ) {
                            state.groupedRules.forEach { (category, rules) ->
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.background
                                    ) {
                                        Text(
                                            text = category ?: stringResource(R.string.rules_category_uncategorized),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                items(
                                    items = rules,
                                    key = { it.rule.id }
                                ) { item ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem(),
                                    ) {
                                        HouseRuleCard(
                                            item = item,
                                            canEdit = state.currentUserRole == MemberRole.PARENT ||
                                                state.currentUserRole == MemberRole.ADMIN,
                                            onEdit = {
                                                editingItem = item
                                                isEditorVisible = true
                                            },
                                            onDelete = { itemToDelete = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isEditorVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        if (isEditorVisible) {
            RuleEditorDialog(
                isEditing = editingItem != null,
                initialTitle = editingItem?.rule?.title.orEmpty(),
                initialDescription = editingItem?.rule?.description.orEmpty(),
                initialCategory = editingItem?.rule?.category.orEmpty(),
                onDismiss = {
                    isEditorVisible = false
                    editingItem = null
                },
                onConfirm = { title, description, category ->
                    val current = editingItem?.rule
                    if (current == null) {
                        viewModel.createRule(title, description, category)
                    } else {
                        viewModel.updateRule(current, title, description, category)
                    }
                    isEditorVisible = false
                    editingItem = null
                }
            )
        }
    }

    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.rules_delete_title),
            message = stringResource(R.string.rules_delete_message, item.rule.title),
            onDismiss = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteRule(item.rule)
                itemToDelete = null
            }
        )
    }
}

@Composable
private fun RuleEditorDialog(
    isEditing: Boolean,
    initialTitle: String,
    initialDescription: String,
    initialCategory: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by rememberSaveable(isEditing, initialTitle) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(isEditing, initialDescription) { mutableStateOf(initialDescription) }
    var category by rememberSaveable(isEditing, initialCategory) { mutableStateOf(initialCategory) }
    var showTitleError by rememberSaveable(isEditing) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.rules_editor_title_edit)
                } else {
                    stringResource(R.string.rules_editor_title_new)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (showTitleError && it.isNotBlank()) {
                            showTitleError = false
                        }
                    },
                    label = { Text(stringResource(R.string.rules_field_title)) },
                    isError = showTitleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                if (showTitleError) {
                    Text(
                        text = stringResource(R.string.error_rules_title_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.rules_field_description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.rules_field_category)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onConfirm(title, description, category)
                        onDismiss()
                    }
                }
            ) {
                Text(
                    text = if (isEditing) {
                        stringResource(R.string.rules_editor_update)
                    } else {
                        stringResource(R.string.rules_editor_save)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
