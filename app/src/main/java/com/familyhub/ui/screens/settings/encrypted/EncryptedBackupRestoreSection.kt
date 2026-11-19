package com.familyhub.ui.screens.settings.encrypted

import com.familyhub.ui.components.FhIcons
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhub.R
import com.familyhub.data.backup.EncryptedBackupFormat
import com.familyhub.data.restore.RestoreCollectionCounts
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton
import java.text.DateFormat
import java.util.Date

@Composable
fun EncryptedBackupRestoreSection(
    viewModel: EncryptedBackupRestoreViewModel,
    onLaunchShareIntent: (android.content.Intent) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onRestoreDocumentSelected(uri) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EncryptedBackupRestoreUiEvent.ShareBackup -> onLaunchShareIntent(event.intent)
            }
        }
    }

    BackHandler(enabled = state.blocksDismiss) { /* block */ }

    EncryptedBackupRestoreSectionContent(
        state = state,
        onCreateClicked = { viewModel.onCreateBackupClicked() },
        onRestoreClicked = {
            openDocument.launch(
                arrayOf(
                    EncryptedBackupFormat.FHB_MIME_TYPE,
                    "application/octet-stream",
                    "*/*"
                )
            )
        },
        onAddProfile = { viewModel.addLocalProfile() }
    )

    EncryptedBackupRestoreDialogHost(
        phase = state.phase,
        onConfirmCreate = { password, confirm -> viewModel.confirmCreateBackup(password, confirm) },
        onCancelCreate = { viewModel.dismissCreatePasswordDialog() },
        onConfirmInspect = { viewModel.confirmInspectPassword(it) },
        onCancelInspect = { viewModel.dismissInspectPasswordDialog() },
        onContinuePreview = { viewModel.onRestoreClicked() },
        onCancelPreview = { viewModel.cancelPreview() },
        onConfirmRestore = { password, destructive, uidConfirm ->
            viewModel.confirmRestore(password, destructive, uidConfirm)
        },
        onCancelConfirmRestore = { viewModel.dismissRestoreConfirmDialog() },
        onAddProfile = { viewModel.addLocalProfile() },
        onAcknowledgeSuccess = { viewModel.acknowledgeSuccess() },
        onAcknowledgeError = { viewModel.acknowledgeError() }
    )
}

/**
 * Renders at most one AlertDialog derived from [phase]. Exposed for Compose tests.
 */
@Composable
fun EncryptedBackupRestoreDialogHost(
    phase: EncryptedBackupRestorePhase,
    onConfirmCreate: (CharArray, CharArray) -> Unit,
    onCancelCreate: () -> Unit,
    onConfirmInspect: (CharArray) -> Unit,
    onCancelInspect: () -> Unit,
    onContinuePreview: () -> Unit,
    onCancelPreview: () -> Unit,
    onConfirmRestore: (CharArray, Boolean, Boolean) -> Unit,
    onCancelConfirmRestore: () -> Unit,
    onAddProfile: () -> Unit,
    onAcknowledgeSuccess: () -> Unit,
    onAcknowledgeError: () -> Unit
) {
    when (phase) {
        is EncryptedBackupRestorePhase.CreatePassword -> {
            CreateEncryptedBackupPasswordDialog(
                error = phase.error,
                onConfirm = onConfirmCreate,
                onCancel = onCancelCreate
            )
        }
        is EncryptedBackupRestorePhase.AwaitingInspectPassword -> {
            InspectPasswordDialog(
                error = phase.error,
                onConfirm = onConfirmInspect,
                onCancel = onCancelInspect
            )
        }
        is EncryptedBackupRestorePhase.Preview -> {
            RestorePreviewDialog(
                preview = phase,
                onRestore = onContinuePreview,
                onCancel = onCancelPreview
            )
        }
        is EncryptedBackupRestorePhase.ConfirmRestore -> {
            RestoreConfirmDialog(
                preview = phase.preview,
                requiresUidConfirmation = phase.requiresUidConfirmation,
                error = phase.error,
                onConfirm = onConfirmRestore,
                onCancel = onCancelConfirmRestore
            )
        }
        is EncryptedBackupRestorePhase.Success -> {
            RestoreSuccessDialog(
                needsLocalProfile = phase.needsLocalProfile,
                onAddProfile = onAddProfile,
                onDone = onAcknowledgeSuccess
            )
        }
        is EncryptedBackupRestorePhase.FatalError -> {
            EncryptedBackupErrorDialog(
                error = phase.error,
                onDismiss = onAcknowledgeError
            )
        }
        is EncryptedBackupRestorePhase.AddingLocalProfile -> {
            AddingLocalProfileDialog()
        }
        else -> Unit
    }
}

@Composable
fun EncryptedBackupRestoreSectionContent(
    state: EncryptedBackupRestoreUiState,
    onCreateClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    onAddProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EncryptedBackupRestoreSectionCard(
            state = state,
            onCreateClicked = onCreateClicked,
            onRestoreClicked = onRestoreClicked
        )

        if (state.needsLocalProfileRecovery) {
            NeedsLocalProfileRecoveryCard(
                error = state.addLocalProfileError,
                isAdding = state.phase is EncryptedBackupRestorePhase.AddingLocalProfile,
                onAddProfile = onAddProfile
            )
        }
    }
}

@Composable
fun EncryptedBackupRestoreSectionCard(
    state: EncryptedBackupRestoreUiState,
    onCreateClicked: () -> Unit,
    onRestoreClicked: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.encrypted_backup_section_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.encrypted_backup_section_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val createA11y = stringResource(R.string.encrypted_backup_a11y_create_button)
            FHButton(
                onClick = onCreateClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = createA11y },
                enabled = state.allowsPrimaryActions && !state.blocksDismiss
            ) {
                if (state.phase is EncryptedBackupRestorePhase.CreatingBackup) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = stringResource(R.string.encrypted_backup_creating),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Icon(FhIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.encrypted_backup_create_button),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            val restoreA11y = stringResource(R.string.encrypted_backup_a11y_restore_button)
            FHTonalButton(
                onClick = onRestoreClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = restoreA11y },
                enabled = state.allowsPrimaryActions && !state.blocksDismiss
            ) {
                when (state.phase) {
                    is EncryptedBackupRestorePhase.Hydrating -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.encrypted_backup_hydrating),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    is EncryptedBackupRestorePhase.Staging,
                    is EncryptedBackupRestorePhase.Inspecting,
                    is EncryptedBackupRestorePhase.Restoring,
                    is EncryptedBackupRestorePhase.AddingLocalProfile -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(
                                when (state.phase) {
                                    is EncryptedBackupRestorePhase.Restoring ->
                                        R.string.encrypted_backup_restoring
                                    is EncryptedBackupRestorePhase.AddingLocalProfile ->
                                        R.string.encrypted_backup_adding_profile
                                    else -> R.string.encrypted_backup_inspecting
                                }
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    else -> {
                        Text(text = stringResource(R.string.encrypted_backup_restore_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateEncryptedBackupPasswordDialog(
    error: EncryptedBackupRestoreError?,
    onConfirm: (CharArray, CharArray) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val passwordA11y = stringResource(R.string.encrypted_backup_a11y_password_field)
    val confirmA11y = stringResource(R.string.encrypted_backup_a11y_password_confirm_field)

    DisposableEffect(Unit) {
        onDispose {
            password = ""
            confirm = ""
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_password_title)) },
        text = {
            FhDialogFormBody {
                Text(stringResource(R.string.encrypted_backup_password_unrecoverable_warning))
                BackupPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.encrypted_backup_password_label)) },
                    fieldContentDescription = passwordA11y,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                )
                BackupPasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.encrypted_backup_password_confirm_label)) },
                    fieldContentDescription = confirmA11y,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.clearFocus() },
                )
                if (error != null) {
                    Text(
                        text = stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = password.toCharArray()
                    val c = confirm.toCharArray()
                    password = ""
                    confirm = ""
                    onConfirm(p, c)
                },
                enabled = password.isNotEmpty() && confirm.isNotEmpty(),
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_password_continue))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_password_cancel))
            }
        }
    )
}

@Composable
private fun InspectPasswordDialog(
    error: EncryptedBackupRestoreError?,
    onConfirm: (CharArray) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val passwordA11y = stringResource(R.string.encrypted_backup_a11y_password_field)
    DisposableEffect(Unit) {
        onDispose { password = "" }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_inspect_password_title)) },
        text = {
            FhDialogFormBody {
                BackupPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.encrypted_backup_password_label)) },
                    fieldContentDescription = passwordA11y,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.clearFocus() },
                )
                if (error != null) {
                    Text(
                        text = stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = password.toCharArray()
                    password = ""
                    onConfirm(p)
                },
                enabled = password.isNotEmpty(),
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_password_continue))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_password_cancel))
            }
        }
    )
}

@Composable
private fun RestorePreviewDialog(
    preview: EncryptedBackupRestorePhase.Preview,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    val date = remember(preview.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(preview.createdAt))
    }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_preview_title)) },
        text = {
            FhDialogFormBody {
                Text(stringResource(R.string.encrypted_backup_preview_created_at, date))
                Text(
                    if (preview.sourceFamilyId.isNullOrBlank()) {
                        stringResource(R.string.encrypted_backup_preview_local_copy)
                    } else {
                        stringResource(
                            R.string.encrypted_backup_preview_source_family,
                            preview.sourceFamilyId
                        )
                    }
                )
                RestoreCountsGrid(preview.counts)
                Text(
                    text = stringResource(R.string.encrypted_backup_preview_replace_warning),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.encrypted_backup_preview_sync_warning),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!preview.containsAuthenticatedUser) {
                    Text(
                        text = stringResource(R.string.encrypted_backup_preview_uid_missing_warning),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRestore,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_preview_continue))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_preview_cancel))
            }
        }
    )
}

@Composable
private fun RestoreCountsGrid(counts: RestoreCollectionCounts) {
    val items = listOf(
        stringResource(R.string.encrypted_backup_preview_count_members) to counts.members,
        stringResource(R.string.encrypted_backup_preview_count_tasks) to counts.tasks,
        stringResource(R.string.encrypted_backup_preview_count_shopping_lists) to counts.shoppingLists,
        stringResource(R.string.encrypted_backup_preview_count_shopping_items) to counts.shoppingItems,
        stringResource(R.string.encrypted_backup_preview_count_events) to counts.events,
        stringResource(R.string.encrypted_backup_preview_count_recipes) to counts.recipes,
        stringResource(R.string.encrypted_backup_preview_count_goals) to counts.familyGoals,
        stringResource(R.string.encrypted_backup_preview_count_rewards) to counts.rewards,
        stringResource(R.string.encrypted_backup_preview_count_penalties) to counts.penalties,
        stringResource(R.string.encrypted_backup_preview_count_menus) to counts.weeklyMenus,
        stringResource(R.string.encrypted_backup_preview_count_templates) to counts.menuTemplates,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, count) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(FhTheme.radius.tile),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    preview: EncryptedBackupRestorePhase.Preview,
    requiresUidConfirmation: Boolean,
    error: EncryptedBackupRestoreError?,
    onConfirm: (CharArray, Boolean, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var destructive by remember { mutableStateOf(false) }
    var uidConfirm by remember { mutableStateOf(false) }
    val needsUid = requiresUidConfirmation
    val focusManager = LocalFocusManager.current
    val passwordA11y = stringResource(R.string.encrypted_backup_a11y_password_field)
    DisposableEffect(Unit) {
        onDispose { password = "" }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_restore_confirm_title)) },
        text = {
            FhDialogFormBody {
                BackupPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.encrypted_backup_restore_password_again)) },
                    fieldContentDescription = passwordA11y,
                    imeAction = ImeAction.Done,
                    onImeAction = { focusManager.clearFocus() },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = destructive,
                            role = Role.Checkbox,
                            onValueChange = { destructive = it },
                        )
                        .fhDialogAction(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = destructive, onCheckedChange = null)
                    Text(stringResource(R.string.encrypted_backup_restore_confirm_checkbox))
                }
                if (needsUid) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = uidConfirm,
                                role = Role.Checkbox,
                                onValueChange = { uidConfirm = it },
                            )
                            .fhDialogAction(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = uidConfirm, onCheckedChange = null)
                        Text(stringResource(R.string.encrypted_backup_restore_uid_checkbox))
                    }
                }
                if (error != null) {
                    Text(
                        text = stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = password.toCharArray()
                    password = ""
                    onConfirm(p, destructive, if (needsUid) uidConfirm else true)
                },
                enabled = password.isNotEmpty() &&
                    destructive &&
                    (!needsUid || uidConfirm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_restore_confirm_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_password_cancel))
            }
        }
    )
}

@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    fieldContentDescription: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val visibilityDescription = stringResource(
        if (passwordVisible) {
            R.string.password_hide_cd
        } else {
            R.string.password_show_cd
        }
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) {
                        FhIcons.VisibilityOff
                    } else {
                        FhIcons.Visibility
                    },
                    contentDescription = visibilityDescription,
                )
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = fieldContentDescription },
    )
}

@Composable
private fun RestoreSuccessDialog(
    needsLocalProfile: Boolean,
    onAddProfile: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_success_title)) },
        text = { Text(stringResource(R.string.encrypted_backup_success_body)) },
        confirmButton = {
            if (needsLocalProfile) {
                Button(
                    onClick = onAddProfile,
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.encrypted_backup_success_add_profile))
                }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.encrypted_backup_success_done))
                }
            }
        },
        dismissButton = {
            if (needsLocalProfile) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.encrypted_backup_success_done))
                }
            }
        }
    )
}

@Composable
private fun AddingLocalProfileDialog() {
    AlertDialog(
        onDismissRequest = { /* blocked while adding */ },
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_adding_profile_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.encrypted_backup_adding_profile))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun NeedsLocalProfileRecoveryCard(
    error: EncryptedBackupRestoreError?,
    isAdding: Boolean,
    onAddProfile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.encrypted_backup_needs_local_profile_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.encrypted_backup_needs_local_profile_body),
                style = MaterialTheme.typography.bodySmall
            )
            if (error != null) {
                Text(
                    text = stringResource(error.messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FHButton(
                onClick = onAddProfile,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAdding
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = stringResource(R.string.encrypted_backup_adding_profile),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Text(stringResource(R.string.encrypted_backup_needs_local_profile_action))
                }
            }
        }
    }
}

@Composable
private fun EncryptedBackupErrorDialog(
    error: EncryptedBackupRestoreError,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.encrypted_backup_error_title)) },
        text = { Text(stringResource(error.messageRes)) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.encrypted_backup_success_done))
            }
        }
    )
}
