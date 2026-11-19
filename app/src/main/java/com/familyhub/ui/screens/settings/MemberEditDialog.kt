package com.familyhub.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.familyhub.util.avatarColorOrFallback
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.FamilyAvatarPicker
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.theme.FhTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Preset avatar colors remain separate from app-theme customization.
val AVATAR_COLORS = listOf(
    "#2196F3",
    "#E91E63",
    "#4CAF50",
    "#FF9800",
    "#9C27B0",
    "#F44336",
    "#00BCD4",
    "#FFEB3B",
    "#795548",
    "#607D8B",
    "#FF5722",
    "#3F51B5"
)

@Composable
fun MemberEditDialog(
    member: FamilyMember?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, MemberRole, String?, String?, String?, String?) -> Unit,
    defaultRole: MemberRole = MemberRole.CHILD,
    showCredentials: Boolean = false,
    initialEmail: String = "",
    showRoleSelector: Boolean = true
) {
    var displayName by remember { mutableStateOf(member?.displayName ?: "") }
    var displayNameError by remember { mutableStateOf<String?>(null) }
    val displayNameRequired = stringResource(R.string.validation_member_name_empty)
    val displayNameFocusRequester = remember { FocusRequester() }
    val birthDateFocusRequester = remember { FocusRequester() }
    var selectedColor by remember { mutableStateOf(member?.avatarColor ?: AVATAR_COLORS[0]) }
    var selectedRole by remember { mutableStateOf(member?.role ?: defaultRole) }
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf(member?.birthDate ?: "") }
    var selectedPhotoUrl by remember { mutableStateOf(member?.photoUrl) }
    val isBirthDateError = remember(birthDate) { birthDate.isNotBlank() && !birthDate.isValidBirthDate() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = {
            Text(
                text = if (member == null) {
                    stringResource(R.string.settings_member_dialog_add_title)
                } else {
                    stringResource(R.string.settings_member_dialog_edit_title)
                }
            )
        },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        displayNameError = null
                    },
                    label = { Text(stringResource(R.string.settings_member_dialog_name_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(displayNameFocusRequester),
                    singleLine = true,
                    isError = displayNameError != null,
                    supportingText = displayNameError?.let { error -> { Text(error) } },
                )

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it.formatBirthDateInput() },
                    label = { Text(stringResource(R.string.settings_member_dialog_birthdate_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(birthDateFocusRequester),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.common_date_hint)) },
                    isError = isBirthDateError,
                    supportingText = {
                        if (isBirthDateError) {
                            Text(
                                text = stringResource(R.string.error_invalid_date),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )

                if (showCredentials) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.settings_member_dialog_email_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.settings_member_dialog_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }

                if (showRoleSelector) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_member_dialog_type_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val availableRoles = listOf(MemberRole.ADMIN, MemberRole.PARENT, MemberRole.CHILD)
                        availableRoles.forEach { role ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedRole == role,
                                        role = Role.RadioButton,
                                        onClick = { selectedRole = role },
                                    )
                                    .fhDialogAction(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRole == role,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = when (role) {
                                        MemberRole.ADMIN -> stringResource(R.string.settings_member_dialog_role_admin)
                                        MemberRole.PARENT -> stringResource(R.string.settings_member_dialog_role_parent)
                                        MemberRole.CHILD -> stringResource(R.string.settings_member_dialog_role_child)
                                        MemberRole.PENDING -> stringResource(R.string.settings_member_dialog_role_child)
                                    },
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                FamilyAvatarPicker(
                    selectedToken = selectedPhotoUrl,
                    onSelected = { selectedPhotoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_member_dialog_avatar_color),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(AVATAR_COLORS) { index, color ->
                            val optionDescription = stringResource(
                                R.string.settings_member_dialog_avatar_color_option,
                                index + 1,
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarColorOrFallback(color))
                                    .border(
                                        width = if (selectedColor == color) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .selectable(
                                        selected = selectedColor == color,
                                        role = Role.RadioButton,
                                        onClick = { selectedColor = color },
                                    )
                                    .semantics { contentDescription = optionDescription },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == color) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        displayName.isBlank() -> {
                            displayNameError = displayNameRequired
                            displayNameFocusRequester.requestFocus()
                        }
                        isBirthDateError -> birthDateFocusRequester.requestFocus()
                        else -> {
                            displayNameError = null
                            onConfirm(
                                displayName.trim(),
                                selectedColor,
                                selectedRole,
                                email.takeIf { showCredentials },
                                password.takeIf { showCredentials && it.isNotBlank() },
                                birthDate.ifBlank { null },
                                selectedPhotoUrl,
                            )
                        }
                    }
                },
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.settings_member_dialog_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private val birthDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun String.formatBirthDateInput(): String {
    val digits = filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { idx, ch ->
            append(ch)
            if (idx == 3 || idx == 5) append('-')
        }
    }
}

private fun String.isValidBirthDate(): Boolean {
    if (length != 10) return false
    return runCatching { LocalDate.parse(this, birthDateFormatter) }.getOrNull() != null
}
