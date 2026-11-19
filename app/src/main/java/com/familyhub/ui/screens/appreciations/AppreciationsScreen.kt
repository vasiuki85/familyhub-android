package com.familyhub.ui.screens.appreciations

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold

@Composable
fun AppreciationsScreen(
    viewModel: AppreciationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var isDialogVisible by remember { mutableStateOf(false) }
    var selectedRecipientId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    var appreciationToDelete by remember { mutableStateOf<AppreciationListItem?>(null) }

    LaunchedEffect(state.members, state.currentUserId) {
        if (selectedRecipientId == null || state.members.none { it.id == selectedRecipientId }) {
            selectedRecipientId = state.members.firstOrNull { it.id != state.currentUserId }?.id
                ?: state.members.firstOrNull()?.id
        }
    }

    LaunchedEffect(state.canMutateFamily) {
        if (!state.canMutateFamily) {
            isDialogVisible = false
            appreciationToDelete = null
        }
    }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_appreciations),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (!state.isLoading && state.members.isNotEmpty() && state.canMutateFamily) {
                FloatingActionButton(onClick = {
                    if (selectedRecipientId == null) {
                        selectedRecipientId = state.members.firstOrNull { it.id != state.currentUserId }?.id
                            ?: state.members.first().id
                    }
                    isDialogVisible = true
                }) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.appreciations_add_fab_cd)
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

            state.appreciations.isEmpty() -> {
                FhEmptyState(
                    icon = FhIcons.Favorite,
                    title = stringResource(R.string.appreciations_empty_state),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                AppreciationsList(
                    items = state.appreciations,
                    currentUserId = state.currentUserId,
                    canMutateFamily = state.canMutateFamily,
                    hasFloatingActionButton = state.members.isNotEmpty() && state.canMutateFamily,
                    onDelete = { appreciationToDelete = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }

    if (isDialogVisible && state.canMutateFamily) {
        AppreciationDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            selectedRecipientId = selectedRecipientId,
            message = message,
            onRecipientChange = { selectedRecipientId = it },
            onMessageChange = { message = it },
            onDismiss = {
                isDialogVisible = false
            },
            onConfirm = {
                if (viewModel.sendAppreciation(selectedRecipientId, message)) {
                    isDialogVisible = false
                    message = ""
                }
            }
        )
    }

    appreciationToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.appreciations_delete_confirm_title),
            message = stringResource(
                R.string.appreciations_delete_confirm_message,
                item.recipientName,
            ),
            onDismiss = { appreciationToDelete = null },
            onConfirm = {
                viewModel.deleteAppreciation(item.appreciation)
                appreciationToDelete = null
            },
        )
    }
}

@Composable
private fun AppreciationsList(
    items: List<AppreciationListItem>,
    currentUserId: String,
    canMutateFamily: Boolean,
    hasFloatingActionButton: Boolean,
    onDelete: (AppreciationListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = com.familyhub.ui.theme.FhLayout.listBottomPadding(hasFloatingActionButton),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.appreciation.id }) { item ->
            AppreciationCard(
                item = item,
                onDelete = if (canMutateFamily && item.appreciation.senderId == currentUserId) {
                    { onDelete(item) }
                } else {
                    null
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppreciationDialog(
    members: List<FamilyMember>,
    currentUserId: String,
    selectedRecipientId: String?,
    message: String,
    onRecipientChange: (String?) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    var messageError by remember { mutableStateOf<String?>(null) }
    var recipientError by remember { mutableStateOf<String?>(null) }
    val messageRequired = stringResource(R.string.validation_appreciation_message_empty)
    val memberRequired = stringResource(R.string.validation_member_not_selected)

    LaunchedEffect(members, currentUserId, selectedRecipientId) {
        when {
            members.isEmpty() -> {
                onRecipientChange(null)
                isMenuExpanded = false
            }
            selectedRecipientId == null || members.none { it.id == selectedRecipientId } -> {
                onRecipientChange(
                    members.firstOrNull { it.id != currentUserId }?.id
                        ?: members.first().id
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.appreciations_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ExposedDropdownMenuBox(
                    expanded = isMenuExpanded,
                    onExpandedChange = { isMenuExpanded = !isMenuExpanded }
                ) {
                    val selectedMemberName = members.firstOrNull { it.id == selectedRecipientId }?.displayLabel()
                        ?: ""
                    OutlinedTextField(
                        value = selectedMemberName,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.appreciations_recipient_label)) },
                        isError = recipientError != null,
                        supportingText = recipientError?.let { error -> { Text(error) } },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuExpanded)
                        }
                    )

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        members.filter { it.id != currentUserId || members.size == 1 }.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.displayLabel()) },
                                onClick = {
                                    onRecipientChange(member.id)
                                    recipientError = null
                                    isMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        onMessageChange(it)
                        messageError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    label = { Text(stringResource(R.string.appreciations_dialog_message_label)) },
                    placeholder = { Text(text = stringResource(R.string.appreciations_message_hint)) },
                    isError = messageError != null,
                    supportingText = messageError?.let { error -> { Text(error) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    recipientError = if (selectedRecipientId == null) memberRequired else null
                    messageError = if (message.isBlank()) messageRequired else null
                    if (recipientError == null && messageError == null) {
                        onConfirm()
                    }
                },
            ) {
                Text(text = stringResource(R.string.appreciations_send_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.appreciations_dialog_cancel))
            }
        }
    )
}
