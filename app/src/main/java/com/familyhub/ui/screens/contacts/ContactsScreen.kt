package com.familyhub.ui.screens.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.EmergencyContact
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_contacts),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (!state.isLoading && state.canManageContacts) {
                FloatingActionButton(
                    onClick = {
                        editingContact = null
                        showEditorDialog = true
                    }
                ) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.contacts_add_cd)
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

            state.contacts.isEmpty() -> {
                FhEmptyState(
                    icon = FhIcons.ContactPhone,
                    title = stringResource(R.string.contacts_empty_state),
                    description = stringResource(
                        if (state.canManageContacts) {
                            R.string.contacts_empty_manage_hint
                        } else {
                            R.string.contacts_read_only_hint
                        }
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                val emergencyServices = state.contacts.filter(::isEmergencyServiceContact)
                val personalContacts = state.contacts.filterNot(::isEmergencyServiceContact)
                val emergencySectionLabel = stringResource(R.string.contacts_section_emergency_services)
                val personalSectionLabel = stringResource(R.string.contacts_section_personal)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = FhLayout.listBottomPadding(state.canManageContacts),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (emergencyServices.isNotEmpty()) {
                        item(key = "emergency-header") {
                            Text(
                                text = emergencySectionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = emergencySectionLabel },
                            )
                        }
                        items(
                            items = emergencyServices,
                            key = { "emergency-${it.id}" },
                        ) { contact ->
                            ContactCard(
                                contact = contact,
                                isEmergencyService = true,
                                modifier = Modifier.animateItem(),
                                onCall = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${contact.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                },
                                onEdit = if (state.canManageContacts) {
                                    {
                                        editingContact = contact
                                        showEditorDialog = true
                                    }
                                } else {
                                    null
                                },
                                onDelete = if (state.canManageContacts) {
                                    { contactToDelete = contact }
                                } else {
                                    null
                                },
                            )
                        }
                    }

                    if (personalContacts.isNotEmpty()) {
                        item(key = "personal-header") {
                            Text(
                                text = personalSectionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (emergencyServices.isNotEmpty()) 4.dp else 0.dp),
                            )
                        }
                        items(
                            items = personalContacts,
                            key = { "personal-${it.id}" },
                        ) { contact ->
                            ContactCard(
                                contact = contact,
                                isEmergencyService = false,
                                modifier = Modifier.animateItem(),
                                onCall = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${contact.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                },
                                onEdit = if (state.canManageContacts) {
                                    {
                                        editingContact = contact
                                        showEditorDialog = true
                                    }
                                } else {
                                    null
                                },
                                onDelete = if (state.canManageContacts) {
                                    { contactToDelete = contact }
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

    if (showEditorDialog) {
        ContactEditorDialog(
            isEditing = editingContact != null,
            initialName = editingContact?.name.orEmpty(),
            initialPhone = editingContact?.phoneNumber.orEmpty(),
            initialDescription = editingContact?.description.orEmpty(),
            onDismiss = {
                showEditorDialog = false
                editingContact = null
            },
            onConfirm = { name, phone, description ->
                if (editingContact == null) {
                    viewModel.addContact(name, phone, description)
                } else {
                    viewModel.updateContact(editingContact!!, name, phone, description)
                }
                showEditorDialog = false
                editingContact = null
            }
        )
    }

    contactToDelete?.let { contact ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.contacts_delete_title),
            message = stringResource(R.string.contacts_delete_message, contact.name),
            onDismiss = { contactToDelete = null },
            onConfirm = {
                viewModel.deleteContact(contact)
                contactToDelete = null
            }
        )
    }
}

@Composable
private fun ContactEditorDialog(
    isEditing: Boolean,
    initialName: String,
    initialPhone: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember(isEditing, initialName) { mutableStateOf(initialName) }
    var phone by remember(isEditing, initialPhone) { mutableStateOf(initialPhone) }
    var description by remember(isEditing, initialDescription) { mutableStateOf(initialDescription) }
    var showNameError by remember { mutableStateOf(false) }
    var showPhoneError by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.contacts_editor_title_edit)
                } else {
                    stringResource(R.string.contacts_editor_title_new)
                }
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (showNameError && it.trim().isNotBlank()) {
                            showNameError = false
                        }
                    },
                    label = { Text(stringResource(R.string.contacts_field_name)) },
                    isError = showNameError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showNameError) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.contacts_error_name_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (showPhoneError && it.trim().isNotBlank()) {
                            showPhoneError = false
                        }
                    },
                    label = { Text(stringResource(R.string.contacts_field_phone)) },
                    isError = showPhoneError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showPhoneError) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.contacts_error_phone_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.contacts_field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    var hasError = false
                    if (name.trim().isBlank()) {
                        showNameError = true
                        hasError = true
                    }
                    if (phone.trim().isBlank()) {
                        showPhoneError = true
                        hasError = true
                    }
                    if (!hasError) {
                        onConfirm(name, phone, description)
                    }
                }
            ) {
                Text(
                    text = if (isEditing) {
                        stringResource(R.string.contacts_editor_save)
                    } else {
                        stringResource(R.string.contacts_editor_add)
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
