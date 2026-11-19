package com.familyhub.ui.screens.penalties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhub.R
import com.familyhub.domain.model.Penalty
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.components.runFormValidation
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton

private enum class PenaltyFilter {
    ACTIVE,
    HISTORY,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PenaltiesScreen(
    viewModel: PenaltiesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canManage = PenaltyEligibilityPolicy.canManage(state.currentUserRole)
    val eligibleMembers = remember(state.members) {
        PenaltyEligibilityPolicy.eligibleTargets(state.members)
    }
    var showDialog by remember { mutableStateOf(false) }
    var penaltyToCancel by remember { mutableStateOf<Penalty?>(null) }
    var filter by rememberSaveable { mutableStateOf(PenaltyFilter.ACTIVE) }

    LaunchedEffect(canManage) {
        if (!canManage) {
            showDialog = false
            penaltyToCancel = null
        }
    }

    val filteredPenalties = remember(state.penalties, filter) {
        when (filter) {
            PenaltyFilter.ACTIVE -> state.penalties.filter { !it.canceled }
            PenaltyFilter.HISTORY -> state.penalties.filter { it.canceled }
        }
    }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_penalties),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (!state.isLoading && canManage) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.penalties_add),
                    )
                }
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> FhListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            state.penalties.isEmpty() -> FhEmptyState(
                icon = FhIcons.Warning,
                title = stringResource(R.string.penalties_empty_title),
                description = stringResource(
                    if (canManage) {
                        R.string.penalties_empty_body
                    } else {
                        R.string.penalties_read_only_hint
                    },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    PenaltyFilterTabRow(
                        selected = filter,
                        onSelected = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (filteredPenalties.isEmpty()) {
                        FhEmptyState(
                            icon = FhIcons.History,
                            title = stringResource(
                                if (filter == PenaltyFilter.ACTIVE) {
                                    R.string.penalties_empty_title
                                } else {
                                    R.string.penalties_filter_history
                                },
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = FhLayout.listBottomPadding(canManage),
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filteredPenalties, key = { it.id }) { penalty ->
                                PenaltyListCard(
                                    penalty = penalty,
                                    member = state.members.find { it.id == penalty.userId },
                                    issuerName = state.members.find { it.id == penalty.issuedByUserId }
                                        ?.displayLabel()
                                        ?: penalty.issuedByUserId,
                                    modifier = Modifier.animateItem(),
                                    onCancel = if (!penalty.canceled && canManage) {
                                        { penaltyToCancel = penalty }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        PenaltyDialog(
            members = eligibleMembers,
            onDismiss = { showDialog = false },
            onConfirm = { userId, amountInput, comment ->
                viewModel.createPenalty(userId, amountInput, comment)
                showDialog = false
            },
        )
    }

    penaltyToCancel?.let { penalty ->
        AlertDialog(
            onDismissRequest = { penaltyToCancel = null },
            title = { Text(stringResource(R.string.penalties_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.penalties_cancel_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelPenalty(penalty.id)
                        penaltyToCancel = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.penalties_cancel))
                }
            },
            dismissButton = {
                FHTonalButton(onClick = { penaltyToCancel = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PenaltyFilterTabRow(
    selected: PenaltyFilter,
    onSelected: (PenaltyFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = PenaltyFilter.entries
    val selectedIndex = filters.indexOf(selected).coerceAtLeast(0)
    val tabLabels = filters.map { penaltyFilter ->
        when (penaltyFilter) {
            PenaltyFilter.ACTIVE -> stringResource(R.string.penalties_filter_active)
            PenaltyFilter.HISTORY -> stringResource(R.string.penalties_filter_history)
        }
    }

    com.familyhub.ui.components.FhPillTabRow(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> onSelected(filters[index]) },
        tabLabels = tabLabels,
        palette = FhTheme.modules.reward,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PenaltyDialog(
    members: List<com.familyhub.domain.model.FamilyMember>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
) {
    var userId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var memberError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    val memberRequiredMessage = stringResource(R.string.validation_penalty_member_required)
    val amountRequiredMessage = stringResource(R.string.validation_penalty_amount_required)
    val memberFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(members, userId) {
        if (members.none { it.id == userId }) {
            userId = members.firstOrNull()?.id.orEmpty()
        }
        if (members.isEmpty()) expanded = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.penalties_add)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (members.isEmpty()) {
                    Text(
                        text = stringResource(R.string.penalties_no_eligible_members),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val selectedName = members.firstOrNull { it.id == userId }
                        ?.displayLabel()
                        .orEmpty()
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.penalties_member_label)) },
                            singleLine = true,
                            isError = memberError != null,
                            supportingText = memberError?.let { error -> { Text(error) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(memberFocusRequester)
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = expanded,
                                )
                            },
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.displayLabel()) },
                                    onClick = {
                                        userId = member.id
                                        memberError = null
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { ch -> ch.isDigit() || ch == '-' }
                        amountError = null
                    },
                    label = { Text(stringResource(R.string.penalties_amount_label)) },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { error -> { Text(error) } },
                    modifier = Modifier.focusRequester(amountFocusRequester),
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.penalties_comment_label)) },
                )
            }
        },
        confirmButton = {
            FHButton(
                onClick = {
                    runFormValidation(
                        validators = listOf(
                            {
                                if (userId.isBlank()) memberRequiredMessage else null
                            } to { memberError = it },
                            {
                                if (amount.isBlank()) amountRequiredMessage else null
                            } to { amountError = it },
                        ),
                        focusRequesters = listOf(memberFocusRequester, amountFocusRequester),
                    ) {
                        onConfirm(userId, amount, comment.ifBlank { null })
                    }
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            FHTonalButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
