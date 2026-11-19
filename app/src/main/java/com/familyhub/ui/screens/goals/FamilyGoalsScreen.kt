@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.familyhub.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.ui.components.FhCheckboxRow
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.navigation.NavRoutes
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGoalsScreen(
    navController: NavController,
    viewModel: FamilyGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isEditorVisible by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<FamilyGoalListItem?>(null) }
    var progressDialogGoal by remember { mutableStateOf<FamilyGoalListItem?>(null) }
    var goalToDelete by remember { mutableStateOf<FamilyGoalListItem?>(null) }
    var showInspiration by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccess()
        }
    }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_goals),
        snackbarHostState = snackbarHostState,
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        actions = {
            IconButton(onClick = { showInspiration = true }) {
                Icon(
                    imageVector = FhIcons.Lightbulb,
                    contentDescription = stringResource(R.string.goals_inspiration_cta_button),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        floatingActionButton = {
            if (!state.isLoading && state.canCreateGoals) {
                val goalColors = FhTheme.modules.goals
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        isEditorVisible = true
                    },
                    containerColor = goalColors.accent,
                    contentColor = goalColors.onAccent,
                    elevation = FloatingActionButtonDefaults.elevation(),
                ) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.goals_fab_add_cd)
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
                    GoalStatusTabRow(
                        selected = state.filter,
                        onSelected = viewModel::setFilter,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.filteredGoals.isEmpty()) {
                        val emptyMessageRes = when (state.filter) {
                            FamilyGoalFilter.ACTIVE -> R.string.goals_empty_active
                            FamilyGoalFilter.COMPLETED -> R.string.goals_empty_completed
                            FamilyGoalFilter.ARCHIVED -> R.string.goals_empty_archived
                            FamilyGoalFilter.ALL -> R.string.goals_empty_all
                        }
                        FhEmptyState(
                            icon = FhIcons.Flag,
                            title = stringResource(emptyMessageRes),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = FhLayout.listBottomPadding(state.canCreateGoals),
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = state.filteredGoals,
                                key = { it.goal.id }
                            ) { item ->
                                GoalListCard(
                                    item = item,
                                    canManageGoal = state.canManageGoal(item.goal),
                                    canParticipate = state.canCreateGoals,
                                    onOpen = {
                                        navController.navigate(NavRoutes.goalDetailDestination(item.goal.id))
                                    },
                                    onEdit = {
                                        editingItem = item
                                        isEditorVisible = true
                                    },
                                    onAddProgress = { progressDialogGoal = item },
                                    onComplete = { viewModel.markCompleted(item) },
                                    onReopen = { viewModel.reopen(item.goal) },
                                    onArchive = { viewModel.archive(item.goal) },
                                    onDelete = { goalToDelete = item },
                                    modifier = Modifier.animateItem(),
                                )
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
            GoalEditorDialog(
                isEditing = editingItem != null,
                initialTitle = editingItem?.goal?.title.orEmpty(),
                initialDescription = editingItem?.goal?.description.orEmpty(),
                initialDeadline = editingItem?.goal?.deadlineAt,
                initialStages = editingItem?.toStageInputs() ?: emptyList(),
                initialIsPersonal = editingItem?.goal?.isPersonal ?: false,
                onDismiss = {
                    isEditorVisible = false
                    editingItem = null
                },
                onConfirm = { title, description, deadline, stages, isPersonal ->
                    val item = editingItem
                    if (item == null) {
                        viewModel.createGoal(title, description, deadline, stages, isPersonal)
                    } else {
                        viewModel.updateGoal(item, title, description, deadline, stages, isPersonal)
                    }
                    isEditorVisible = false
                    editingItem = null
                }
            )
        }
    }

    progressDialogGoal?.let { item ->
        GoalProgressDialog(
            goalTitle = item.goal.title,
            onDismiss = { progressDialogGoal = null },
            onConfirm = { amount ->
                viewModel.addProgress(item, amount)
                progressDialogGoal = null
            }
        )
    }

    goalToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.goals_delete_title),
            message = stringResource(R.string.goals_delete_message, item.goal.title),
            onDismiss = { goalToDelete = null },
            onConfirm = {
                viewModel.delete(item.goal)
                goalToDelete = null
            }
        )
    }

    if (showInspiration) {
        GoalInspirationBottomSheet(onDismiss = { showInspiration = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalStatusTabRow(
    selected: FamilyGoalFilter,
    onSelected: (FamilyGoalFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = FamilyGoalFilter.entries
    val selectedIndex = filters.indexOf(selected).coerceAtLeast(0)
    val tabLabels = filters.map { filter ->
        when (filter) {
            FamilyGoalFilter.ACTIVE -> stringResource(R.string.goals_filter_active)
            FamilyGoalFilter.COMPLETED -> stringResource(R.string.goals_filter_completed)
            FamilyGoalFilter.ARCHIVED -> stringResource(R.string.goals_filter_archived)
            FamilyGoalFilter.ALL -> stringResource(R.string.goals_filter_all)
        }
    }

    com.familyhub.ui.components.FhPillTabRow(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> onSelected(filters[index]) },
        tabLabels = tabLabels,
        palette = FhTheme.modules.goals,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorDialog(
    isEditing: Boolean,
    initialTitle: String,
    initialDescription: String,
    initialDeadline: Long?,
    initialStages: List<GoalStageInput>,
    initialIsPersonal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long?, List<GoalStageInput>, Boolean) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDeadline?.let(TimeUtils::getStartOfDay)
    )
    var deadline by rememberSaveable { mutableStateOf(initialDeadline) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    val stageDrafts = remember { mutableStateListOf<GoalStageDraft>() }
    var isPersonal by rememberSaveable(initialIsPersonal) { mutableStateOf(initialIsPersonal) }

    LaunchedEffect(initialStages, isEditing) {
        stageDrafts.clear()
        if (initialStages.isNotEmpty()) {
            stageDrafts.addAll(initialStages.map { it.toDraft() })
        } else {
            stageDrafts.add(GoalStageDraft(id = null))
        }
    }

    val totalSteps = stageDrafts.sumOf { draft -> draft.steps.count { it.title.isNotBlank() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.goals_dialog_edit_title)
                } else {
                    stringResource(R.string.goals_dialog_create_title)
                }
            )
        },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.goals_field_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.goals_field_description)) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                FhCheckboxRow(
                    checked = isPersonal,
                    onCheckedChange = { isPersonal = it },
                    label = stringResource(R.string.goals_personal_checkbox),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.goals_personal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = deadline?.let { stringResource(R.string.goals_deadline_label, TimeUtils.formatDate(it)) }
                            ?: stringResource(R.string.goals_deadline_not_set),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FHTonalButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(R.string.goals_action_pick_deadline))
                        }
                        if (deadline != null) {
                            FHTonalButton(onClick = { deadline = null }) {
                                Text(stringResource(R.string.goals_action_clear_deadline))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.goals_plan_section_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.goals_plan_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    stageDrafts.forEachIndexed { index, draft ->
                ThemedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = draft.title,
                                    onValueChange = { draft.title = it },
                                    label = { Text(stringResource(R.string.goals_stage_name_hint)) },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                )
                                OutlinedTextField(
                                    value = draft.description,
                                    onValueChange = { draft.description = it },
                                    label = { Text(stringResource(R.string.goals_stage_description_hint)) },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                )
                                draft.steps.forEachIndexed { stepIndex, step ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = step.title,
                                            onValueChange = { step.title = it },
                                            label = { Text(stringResource(R.string.goals_step_name_hint)) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                        )
                                        IconButton(onClick = {
                                            if (draft.steps.size > 1) {
                                                draft.steps.removeAt(stepIndex)
                                            } else {
                                                draft.steps[stepIndex].title = ""
                                            }
                                        }) {
                                            Icon(
                                                imageVector = FhIcons.Delete,
                                                contentDescription = stringResource(R.string.goals_step_remove)
                                            )
                                        }
                                    }
                                }
                                FHTonalButton(onClick = { draft.steps.add(GoalStepDraft(id = null)) }) {
                                    Icon(imageVector = FhIcons.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.goals_step_add))
                                }
                                if (stageDrafts.size > 1) {
                                    FHTonalButton(onClick = {
                                        stageDrafts.removeAt(index)
                                        if (stageDrafts.isEmpty()) {
                                            stageDrafts.add(GoalStageDraft(id = null))
                                        }
                                    }) {
                                        Icon(imageVector = FhIcons.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.goals_stage_remove))
                                    }
                                }
                            }
                        }
                    }

                    FHTonalButton(onClick = { stageDrafts.add(GoalStageDraft(id = null)) }) {
                        Icon(imageVector = FhIcons.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.goals_stage_add))
                    }

                    Text(
                        text = stringResource(R.string.goals_plan_total_steps, totalSteps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                inputError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            FHButton(
                onClick = {
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isBlank()) {
                        inputError = context.getString(R.string.goals_error_title_required)
                        return@FHButton
                    }
                    val sanitizedStages = stageDrafts
                        .map { it.toInput() }
                        .filter { stage ->
                            stage.title.trim().isNotEmpty() || stage.steps.any { it.title.trim().isNotEmpty() }
                        }
                            onConfirm(trimmedTitle, description, deadline, sanitizedStages, isPersonal)
                }
            ) {
                Text(stringResource(R.string.goals_action_save))
            }
        },
        dismissButton = {
            FHTonalButton(onClick = onDismiss) {
                Text(stringResource(R.string.goals_action_cancel))
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        deadline = selected
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.goals_action_select_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.goals_action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}


private class GoalStageDraft(
    val id: String?,
    title: String = "",
    description: String = "",
    initialSteps: List<GoalStepDraft> = listOf(GoalStepDraft(id = null))
) {
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    val steps = mutableStateListOf<GoalStepDraft>().apply {
        if (initialSteps.isEmpty()) {
            add(GoalStepDraft(id = null))
        } else {
            addAll(initialSteps)
        }
    }
}

private class GoalStepDraft(
    val id: String?,
    title: String = "",
    isCompleted: Boolean = false
) {
    var title by mutableStateOf(title)
    var isCompleted by mutableStateOf(isCompleted)
}

private fun GoalStageDraft.toInput(): GoalStageInput = GoalStageInput(
    id = id,
    title = title,
    description = description.takeIf { it.isNotBlank() },
    steps = steps.mapNotNull { it.toInput() }
)

private fun GoalStepDraft.toInput(): GoalStepInput? {
    val trimmed = title.trim()
    return if (trimmed.isEmpty()) {
        null
    } else {
        GoalStepInput(id = id, title = trimmed, isCompleted = isCompleted)
    }
}

private fun GoalStageInput.toDraft(): GoalStageDraft = GoalStageDraft(
    id = id,
    title = title,
    description = description.orEmpty(),
    initialSteps = steps.map { GoalStepDraft(it.id, it.title, it.isCompleted) }
)

private fun FamilyGoalListItem.toStageInputs(): List<GoalStageInput> =
    stages.sortedBy(FamilyGoalStage::position).map { stage ->
        GoalStageInput(
            id = stage.id,
            title = stage.title,
            description = stage.description,
            steps = stage.steps.sortedBy(FamilyGoalStep::position).map { step ->
                GoalStepInput(id = step.id, title = step.title, isCompleted = step.isCompleted)
            }
        )
    }


@Composable
private fun GoalProgressDialog(
    goalTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val invalidProgressMessage = stringResource(R.string.goals_error_progress_invalid)
    var progressInput by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_progress_dialog_title, goalTitle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = progressInput,
                    onValueChange = {
                        if (it.all(Char::isDigit)) {
                            progressInput = it
                            inputError = null
                        }
                    },
                    label = { Text(stringResource(R.string.goals_field_progress)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                inputError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            FHButton(onClick = {
                val amount = progressInput.toIntOrNull()
                if (amount == null || amount <= 0) {
                    inputError = invalidProgressMessage
                    return@FHButton
                }
                onConfirm(amount)
            }) {
                Text(stringResource(R.string.goals_action_add_progress))
            }
        },
        dismissButton = {
            FHTonalButton(onClick = onDismiss) {
                Text(stringResource(R.string.goals_action_cancel))
            }
        }
    )
}

@Composable
private fun GoalInspirationCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedCard(
        modifier = modifier.clickable(onClick = onClick),
        tone = FhCardTone.ACCENT
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = FhIcons.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.goals_inspiration_cta_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.goals_inspiration_cta_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            FHTonalButton(onClick = onClick) {
                Text(stringResource(R.string.goals_inspiration_cta_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalInspirationBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        GoalInspirationContent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun GoalInspirationContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.goals_inspiration_sheet_title),
            style = MaterialTheme.typography.titleLarge
        )
        goalInspirationCategories.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(category.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                category.suggestions.forEach { suggestion ->
                    GoalInspirationSuggestionCard(suggestion)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GoalInspirationSuggestionCard(suggestion: GoalInspirationSuggestion) {
    ThemedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(suggestion.titleRes),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(suggestion.summaryRes),
                style = MaterialTheme.typography.bodyMedium
            )
            suggestion.steps.forEach { stepRes ->
                Text(
                    text = "• " + stringResource(stepRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class GoalInspirationCategory(
    val titleRes: Int,
    val descriptionRes: Int,
    val suggestions: List<GoalInspirationSuggestion>
)

private data class GoalInspirationSuggestion(
    val titleRes: Int,
    val summaryRes: Int,
    val steps: List<Int>
)

private val goalInspirationCategories = listOf(
    GoalInspirationCategory(
        titleRes = R.string.goal_ideas_self_discovery_title,
        descriptionRes = R.string.goal_ideas_self_discovery_desc,
        suggestions = listOf(
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_values_title,
                summaryRes = R.string.goal_ideas_values_summary,
                steps = listOf(
                    R.string.goal_ideas_values_step1,
                    R.string.goal_ideas_values_step2,
                    R.string.goal_ideas_values_step3
                )
            ),
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_strengths_title,
                summaryRes = R.string.goal_ideas_strengths_summary,
                steps = listOf(
                    R.string.goal_ideas_strengths_step1,
                    R.string.goal_ideas_strengths_step2,
                    R.string.goal_ideas_strengths_step3
                )
            )
        )
    ),
    GoalInspirationCategory(
        titleRes = R.string.goal_ideas_direction_title,
        descriptionRes = R.string.goal_ideas_direction_desc,
        suggestions = listOf(
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_life_wheel_title,
                summaryRes = R.string.goal_ideas_life_wheel_summary,
                steps = listOf(
                    R.string.goal_ideas_life_wheel_step1,
                    R.string.goal_ideas_life_wheel_step2,
                    R.string.goal_ideas_life_wheel_step3
                )
            ),
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_future_title,
                summaryRes = R.string.goal_ideas_future_summary,
                steps = listOf(
                    R.string.goal_ideas_future_step1,
                    R.string.goal_ideas_future_step2,
                    R.string.goal_ideas_future_step3
                )
            )
        )
    ),
    GoalInspirationCategory(
        titleRes = R.string.goal_ideas_action_title,
        descriptionRes = R.string.goal_ideas_action_desc,
        suggestions = listOf(
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_progress_title,
                summaryRes = R.string.goal_ideas_progress_summary,
                steps = listOf(
                    R.string.goal_ideas_progress_step1,
                    R.string.goal_ideas_progress_step2,
                    R.string.goal_ideas_progress_step3
                )
            ),
            GoalInspirationSuggestion(
                titleRes = R.string.goal_ideas_support_title,
                summaryRes = R.string.goal_ideas_support_summary,
                steps = listOf(
                    R.string.goal_ideas_support_step1,
                    R.string.goal_ideas_support_step2,
                    R.string.goal_ideas_support_step3
                )
            )
        )
    )
)
