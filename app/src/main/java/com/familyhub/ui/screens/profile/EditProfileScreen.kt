package com.familyhub.ui.screens.profile

import com.familyhub.ui.components.FhIcons
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.domain.utils.BundledFamilyAvatar
import com.familyhub.util.ExternalImageUrlUtils
import com.familyhub.ui.components.FamilyAvatarPicker
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhLoading
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.RemoteImage
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.theme.components.FHButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    memberId: String?,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { Timber.w(it, "EditProfileScreen: failed to persist selected avatar uri") }
            viewModel.onPhotoUrlChange(it.toString())
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            navController.popBackStack()
        }
    }

    val contentMode = EditProfileContentPolicy.resolve(
        memberId = memberId,
        loadStatus = uiState.loadStatus,
        hasMember = uiState.member != null,
    )

    FhScreenScaffold(
        title = stringResource(R.string.edit_profile),
        collapsingTopBar = false,
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    FhIcons.Back,
                    contentDescription = stringResource(R.string.edit_profile_back_cd)
                )
            }
        },
        errorMessage = uiState.error.takeIf {
            contentMode == EditProfileContentMode.EDITOR
        },
        onErrorDismiss = viewModel::clearError
    ) { paddingValues ->
        when (contentMode) {
            EditProfileContentMode.LOADING -> {
                FhLoading(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            EditProfileContentMode.UNAVAILABLE -> {
                FhEmptyState(
                    icon = FhIcons.Person,
                    title = stringResource(R.string.edit_profile_unavailable_title),
                    description = stringResource(R.string.edit_profile_unavailable_body),
                    actionLabel = stringResource(R.string.edit_profile_back_action),
                    onAction = { navController.popBackStack() },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            EditProfileContentMode.LOAD_FAILED -> {
                FhEmptyState(
                    icon = FhIcons.Refresh,
                    title = stringResource(R.string.edit_profile_load_failed_title),
                    description = stringResource(R.string.edit_profile_load_failed_body),
                    actionLabel = stringResource(R.string.edit_profile_retry_action),
                    onAction = viewModel::retryLoad,
                    modifier = Modifier.padding(paddingValues),
                )
            }
            EditProfileContentMode.EDITOR -> {
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.photoUrl != null) {
                        RemoteImage(
                            url = uiState.photoUrl,
                            contentDescription = uiState.displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = FhIcons.Person,
                            contentDescription = stringResource(R.string.edit_profile_default_avatar_cd),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FamilyAvatarPicker(
                    selectedToken = uiState.photoUrl,
                    onSelected = viewModel::onPhotoUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                val photoUrl = uiState.photoUrl
                val photoLinkInput = when {
                    photoUrl == null -> ""
                    BundledFamilyAvatar.isToken(photoUrl) -> ""
                    ExternalImageUrlUtils.isLocalContentUri(photoUrl) -> ""
                    else -> photoUrl
                }

                OutlinedTextField(
                    value = photoLinkInput,
                    onValueChange = viewModel::onPhotoUrlChange,
                    label = { Text(stringResource(R.string.edit_profile_photo_url_label)) },
                    placeholder = { Text(stringResource(R.string.edit_profile_photo_url_hint)) },
                    supportingText = {
                        Text(stringResource(R.string.edit_profile_photo_url_help))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.edit_profile_pick_local_photo))
                    }
                    if (uiState.photoUrl != null) {
                        TextButton(onClick = { viewModel.onPhotoUrlChange("") }) {
                            Text(stringResource(R.string.edit_profile_clear_photo))
                        }
                    }
                }

                if (uiState.photoUrl?.let { ExternalImageUrlUtils.isLocalContentUri(it) } == true) {
                    Text(
                        text = stringResource(R.string.edit_profile_local_photo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.edit_profile_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.birthDate,
                    onValueChange = { viewModel.onBirthDateChange(it.formatBirthDateInput()) },
                    label = { Text(stringResource(R.string.edit_profile_birthdate_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.common_date_hint)) },
                    isError = uiState.birthDate.isNotBlank() && !uiState.birthDate.isValidBirthDate(),
                    supportingText = {
                        if (uiState.birthDate.isNotBlank() && !uiState.birthDate.isValidBirthDate()) {
                            Text(
                                text = stringResource(R.string.error_invalid_date),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )

                if (uiState.age.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.edit_profile_age_value, uiState.age),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(stringResource(R.string.edit_profile_email_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.edit_profile_password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                FHButton(
                    onClick = { viewModel.saveProfile() },
                    enabled = uiState.displayName.isNotBlank() &&
                        (uiState.birthDate.isBlank() || uiState.birthDate.isValidBirthDate()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fhDialogAction(),
                ) {
                    Text(stringResource(R.string.common_save))
                }
                }
            }
        }
    }
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
