package com.familyhub.ui.screens.settings

import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhSwitchRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateListOf
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.google.firebase.auth.FirebaseAuth
import com.familyhub.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.fragment.app.FragmentActivity
import com.familyhub.data.security.BiometricAuthService
import com.familyhub.data.export.ExportFormat
import com.familyhub.ui.screens.settings.encrypted.EncryptedBackupRestoreSection
import com.familyhub.ui.screens.settings.encrypted.EncryptedBackupRestoreViewModel
import com.familyhub.data.export.PlaintextExportPendingRequest
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.ThemeMode
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.RemoteImage
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton
import com.familyhub.ui.theme.components.ThemedCard
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    joinFamilyId: String? = null,
    initialSyncAction: SettingsInitialSyncAction? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    encryptedBackupRestoreViewModel: EncryptedBackupRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToReject by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToApprove by remember { mutableStateOf<FamilyMember?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showCreateFamilyDialog by remember { mutableStateOf(false) }
    var createFamilyId by remember { mutableStateOf("") }
    var consumedInitialSyncAction by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var showFamilyQr by remember { mutableStateOf(false) }
    var showSyncTechnicalDetails by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var biometricToggleBusy by remember { mutableStateOf(false) }
    var biometricToggleMessage by remember { mutableStateOf<String?>(null) }

    // Если пришли по диплинку join и синхронизация ещё не включена — показать диалог подключения
    androidx.compose.runtime.LaunchedEffect(joinFamilyId, state.syncState.isEnabled) {
        if (!state.syncState.isEnabled && !joinFamilyId.isNullOrBlank()) {
            showSyncDialog = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        initialSyncAction,
        consumedInitialSyncAction,
        state.syncState.accountId,
        state.syncState.isHydrated,
    ) {
        val action = SettingsInitialSyncActionPolicy.resolve(
            initialAction = initialSyncAction,
            alreadyConsumed = consumedInitialSyncAction,
            accountId = state.syncState.accountId,
            isHydrated = state.syncState.isHydrated,
        ) ?: return@LaunchedEffect
        consumedInitialSyncAction = true
        viewModel.clearSyncError()
        when (action) {
            SettingsInitialSyncAction.CREATE -> {
                createFamilyId = com.familyhub.domain.utils.IdGenerator.newId()
                showCreateFamilyDialog = true
            }
            SettingsInitialSyncAction.JOIN -> showSyncDialog = true
        }
    }

    val settingsError = state.exportError ?: state.syncActionError ?: state.backupError

    FhScreenScaffold(
        title = stringResource(R.string.settings_title),
        errorMessage = settingsError,
        onErrorDismiss = {
            when {
                state.exportError != null -> viewModel.clearExportError()
                state.syncActionError != null -> viewModel.clearSyncError()
                state.backupError != null -> viewModel.clearBackupError()
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_section),
                            style = MaterialTheme.typography.titleMedium
                        )

                        ThemeMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = state.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.themeMode == mode,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = when (mode) {
                                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    },
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_security_section),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = state.biometricEnabled,
                                enabled = state.biometricAvailable && !biometricToggleBusy,
                                role = Role.Switch,
                                onValueChange = { enabled ->
                                    val activity = context as? FragmentActivity ?: return@toggleable
                                    if (biometricToggleBusy) return@toggleable
                                    scope.launch {
                                        biometricToggleBusy = true
                                        biometricToggleMessage = null
                                        val biometricService = BiometricAuthService(activity)
                                        val confirmed = biometricService.authenticate()
                                        if (confirmed) {
                                            viewModel.setBiometricEnabled(enabled)
                                        } else {
                                            biometricToggleMessage = context.getString(
                                                if (enabled) {
                                                    R.string.settings_biometric_enable_failed
                                                } else {
                                                    R.string.settings_biometric_disable_failed
                                                }
                                            )
                                        }
                                        biometricToggleBusy = false
                                    }
                                },
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_biometric_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (state.biometricAvailable) {
                                    stringResource(R.string.settings_biometric_enabled_hint)
                                } else {
                                    stringResource(R.string.settings_biometric_unavailable)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = null,
                            enabled = state.biometricAvailable && !biometricToggleBusy
                        )
                    }
                    biometricToggleMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            }

            // Выбор пунктов нижней навигации
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_bottom_nav_section),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_bottom_nav_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val bottomNavSlotCount = com.familyhub.ui.navigation.MAX_BOTTOM_NAV_ITEMS
                        val navItems = com.familyhub.ui.navigation.bottomNavItems
                        val selectedRoutes = remember(state.bottomNavRoutes) {
                            mutableStateListOf<String?>().apply {
                                addAll(com.familyhub.ui.navigation.BottomNavEditorSlotsMapper.map(state.bottomNavRoutes))
                            }
                        }
                        var expandedSlot by remember { mutableStateOf(-1) }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(bottomNavSlotCount) { slotIndex ->
                                val slotLabel = stringResource(R.string.settings_bottom_nav_slot_label, slotIndex + 1)
                                val slotSelection = selectedRoutes[slotIndex]
                                val selectedItem = navItems.firstOrNull { it.route == slotSelection }
                                val displayLabel = selectedItem?.let { stringResource(it.labelRes) } ?: ""
                                val otherSelectedRoutes = selectedRoutes
                                    .mapIndexedNotNull { index, route -> if (index != slotIndex) route else null }
                                    .toSet()
                                val slotOptions = navItems.filter { navItem ->
                                    navItem.route == slotSelection || navItem.route !in otherSelectedRoutes
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = slotLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.width(88.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    ExposedDropdownMenuBox(
                                        expanded = expandedSlot == slotIndex,
                                        onExpandedChange = { expanded ->
                                            expandedSlot = if (expanded) slotIndex else -1
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = displayLabel,
                                            onValueChange = {},
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth(),
                                            readOnly = true,
                                            singleLine = true,
                                            placeholder = {
                                                Text(stringResource(R.string.settings_bottom_nav_slot_empty))
                                            },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(
                                                    expanded = expandedSlot == slotIndex
                                                )
                                            },
                                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                                        )

                                        DropdownMenu(
                                            expanded = expandedSlot == slotIndex,
                                            onDismissRequest = { expandedSlot = -1 }
                                        ) {
                                            slotOptions.forEach { navItem ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(navItem.labelRes)) },
                                                    onClick = {
                                                        selectedRoutes[slotIndex] = navItem.route
                                                        expandedSlot = -1
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_bottom_nav_slot_clear)) },
                                                onClick = {
                                                    selectedRoutes[slotIndex] = null
                                                    expandedSlot = -1
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val selectedBottomNavCount = selectedRoutes.count { it != null }
                        val canSaveBottomNav = selectedBottomNavCount in
                            com.familyhub.ui.navigation.MIN_BOTTOM_NAV_ITEMS..
                            com.familyhub.ui.navigation.MAX_BOTTOM_NAV_ITEMS

                        Button(
                            onClick = {
                                val routesToSave = selectedRoutes.filterNotNull()
                                viewModel.setBottomNavRoutes(routesToSave)
                                Toast.makeText(context, R.string.settings_bottom_nav_saved, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp),
                            enabled = canSaveBottomNav
                        ) {
                            Text(stringResource(R.string.settings_save_button))
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FhSwitchRow(
                            checked = state.syncState.isEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val accId = state.syncState.accountId
                                    val devId = state.syncState.deviceId
                                    if (accId.isNullOrBlank() || devId.isNullOrBlank()) {
                                        showSyncDialog = true
                                    } else {
                                        viewModel.resumeSyncFromStored()
                                    }
                                } else {
                                    viewModel.pauseSync()
                                }
                            },
                            label = stringResource(R.string.settings_sync_section_title),
                        )

                        Text(
                            text = stringResource(R.string.settings_sync_section_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!state.syncState.isEnabled) {
                            Text(
                                text = stringResource(R.string.settings_sync_section_disabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FHTonalButton(
                                    onClick = {
                                        viewModel.clearSyncError()
                                        createFamilyId = com.familyhub.domain.utils.IdGenerator.newId()
                                        showCreateFamilyDialog = true
                                    }
                                ) {
                                    Text(text = stringResource(R.string.settings_family_create_button))
                                }
                                FHTonalButton(
                                    onClick = {
                                        viewModel.clearSyncError()
                                        showSyncDialog = true
                                    }
                                ) {
                                    Text(text = stringResource(R.string.settings_family_join_button))
                                }
                            }
                        } else {
                            val lastSyncText = state.syncState.lastSyncAt?.let { timestamp ->
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(timestamp))
                            } ?: stringResource(R.string.settings_sync_last_sync_never)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_sync_last_sync, lastSyncText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                TextButton(
                                    onClick = {
                                        showSyncTechnicalDetails = !showSyncTechnicalDetails
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            if (showSyncTechnicalDetails) {
                                                R.string.settings_sync_details_hide
                                            } else {
                                                R.string.settings_sync_details_show
                                            }
                                        )
                                    )
                                }

                                AnimatedVisibility(visible = showSyncTechnicalDetails) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = stringResource(
                                                R.string.settings_sync_account_label,
                                                state.syncState.accountId
                                                    ?: stringResource(R.string.settings_sync_unknown)
                                            ),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.settings_sync_device_label,
                                                state.syncState.deviceId
                                                    ?: stringResource(R.string.settings_sync_unknown)
                                            ),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        TextButton(onClick = {
                                            val cm = context.getSystemService(ClipboardManager::class.java)
                                            val id = state.syncState.accountId.orEmpty()
                                            cm?.setPrimaryClip(ClipData.newPlainText("familyId", id))
                                            Toast.makeText(
                                                context,
                                                R.string.settings_family_id_copied,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }) {
                                            Text(stringResource(R.string.settings_family_copy_id))
                                        }
                                        TextButton(onClick = {
                                            val id = state.syncState.accountId.orEmpty()
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    context.getString(
                                                        R.string.settings_family_share_text,
                                                        "familyhub://join?familyId=$id"
                                                    )
                                                )
                                            }
                                            launchIntentSafely(
                                                context,
                                                Intent.createChooser(
                                                    intent,
                                                    context.getString(
                                                        R.string.settings_family_share_chooser
                                                    )
                                                )
                                            )
                                        }) {
                                            Icon(
                                                FhIcons.Share,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.settings_family_share_id
                                                ),
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                        TextButton(onClick = { showFamilyQr = true }) {
                                            Text(stringResource(R.string.settings_family_show_qr))
                                        }
                                    }
                                }
                            }

                            if (state.syncState.hasPendingChanges) {
                                Text(
                                    text = stringResource(R.string.settings_sync_pending_changes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }

                            if (state.syncState.isSyncing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_sync_in_progress),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            state.syncState.lastError?.let {
                                Text(
                                    text = stringResource(R.string.settings_sync_safe_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FHButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.syncNow()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.syncState.isSyncing
                                ) {
                                    if (state.syncState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            FhIcons.Sync,
                                            contentDescription = stringResource(R.string.settings_sync_now_button),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            if (state.syncState.isSyncing) {
                                                R.string.settings_sync_in_progress
                                            } else {
                                                R.string.settings_sync_now_button
                                            }
                                        ),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.requestPlaintextExport(PlaintextExportPendingRequest.Backup)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isBackupInProgress && state.pendingPlaintextExport == null
                                ) {
                                    if (state.isBackupInProgress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            FhIcons.Save,
                                            contentDescription = stringResource(R.string.settings_sync_backup_button),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = if (state.isBackupInProgress) {
                                            stringResource(R.string.settings_sync_backup_in_progress)
                                        } else {
                                            stringResource(R.string.settings_sync_backup_button)
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            } 

                            TextButton(
                                onClick = { viewModel.disableSync() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    FhIcons.CloudOff,
                                    contentDescription = stringResource(R.string.settings_sync_disable_button),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_sync_disable_button),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { viewModel.signOut() }) {
                                    Text(text = stringResource(R.string.settings_sign_out_button))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_family_section),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (state.members.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_family_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        // Примечание: Добавление членов семьи вручную отключено.
                        // Члены семьи могут только присоединяться к семье через ввод ID семьи.
                        Text(
                            text = stringResource(R.string.settings_members_join_only_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Определяем текущего пользователя
                            val currentMember = state.members.find { it.id == state.currentUserId }
                            
                            // Если текущий пользователь в статусе PENDING, показываем уведомление
                            val isCurrentUserPending = currentMember?.role == MemberRole.PENDING
                            if (isCurrentUserPending) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            FhIcons.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_member_pending_notice),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                            
                            // Разделяем участников на группы: активные и ожидающие
                            val activeMembers = state.members.filter { it.role != MemberRole.PENDING }
                            val pendingMembers = state.members.filter { it.role == MemberRole.PENDING }
                            
                            // Показываем активных участников
                            // Показываем активных участников (только если текущий пользователь не ожидает подтверждения)
                            if (!isCurrentUserPending) {
                                activeMembers.forEach { member ->
                                    MemberRow(
                                        member = member,
                                        currentUserId = state.currentUserId,
                                        canManage = state.currentUserRole == MemberRole.ADMIN,
                                        onEdit = { memberToEdit = member },
                                        onDelete = { memberToDelete = member }
                                    )
                                }
                            }
                            
                            // Показываем ожидающих участников (только для админа)
                            if (state.currentUserRole == MemberRole.ADMIN && pendingMembers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_member_status_pending),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                pendingMembers.forEach { member ->
                                    PendingMemberRow(
                                        member = member,
                                        onApprove = { memberToApprove = member },
                                        onReject = { memberToReject = member }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_export_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = stringResource(R.string.settings_export_section_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        FHButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && state.pendingPlaintextExport == null
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    FhIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = if (state.isExporting) {
                                    stringResource(R.string.settings_export_in_progress)
                                } else {
                                    stringResource(R.string.settings_export_button)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                EncryptedBackupRestoreSection(
                    viewModel = encryptedBackupRestoreViewModel,
                    onLaunchShareIntent = { intent ->
                        launchIntentSafely(context, intent)
                    }
                )
            }

        }
    }

    // Диалог редактирования члена семьи (только для существующих членов)
    AnimatedVisibility(
        visible = memberToEdit != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        val authEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        val isSelf = memberToEdit?.id == state.currentUserId
        MemberEditDialog(
            member = memberToEdit,
            onDismiss = {
                memberToEdit = null
            },
            onConfirm = { displayName, avatarColor, role, email, password, birthDate, photoUrl ->
                memberToEdit?.let {
                    viewModel.updateMember(
                        member = it,
                        displayName = displayName,
                        avatarColor = avatarColor,
                        role = role,
                        birthDate = birthDate,
                        email = email,
                        password = password,
                        photoUrl = photoUrl,
                    )
                }
                memberToEdit = null
            },
            defaultRole = memberToEdit?.role ?: MemberRole.CHILD,
            showCredentials = isSelf,
            initialEmail = authEmail,
            showRoleSelector = false
        )
    }

    AnimatedVisibility(
        visible = showSyncDialog,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        val currentUser = state.members.firstOrNull { it.id == state.currentUserId }
        SyncSetupDialog(
            onDismiss = {
                showSyncDialog = false
            },
            onConfirm = { accountId ->
                viewModel.enableSync(
                    accountId = accountId,
                    isJoining = true,
                    memberDisplayName = currentUser?.displayName
                )
                showSyncDialog = false
            },
            initialAccountId = joinFamilyId
        )
    }

    // Create Family dialog (prefilled ID, read-only)
    AnimatedVisibility(
        visible = showCreateFamilyDialog,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        val currentUser = state.members.firstOrNull { it.id == state.currentUserId }
        SyncSetupDialog(
            onDismiss = { showCreateFamilyDialog = false },
            onConfirm = { accountId ->
                viewModel.enableSync(accountId, isJoining = false, memberDisplayName = currentUser?.displayName)
                showCreateFamilyDialog = false
            },
            initialAccountId = createFamilyId,
            accountReadOnly = true
        )
    }

    // QR для приглашения (после включённой синхронизации)
    if (showFamilyQr) {
        val accId = state.syncState.accountId.orEmpty()
        AlertDialog(
            onDismissRequest = { showFamilyQr = false },
            shape = RoundedCornerShape(FhTheme.radius.card),
            title = { Text(stringResource(R.string.settings_family_qr_title)) },
            text = {
                val qrBitmap = remember(accId) { generateQrBitmap("familyhub://join?familyId=$accId", 512) }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.settings_family_qr_cd),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(accId)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFamilyQr = false },
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.settings_close))
                }
            }

            // Демонстрационные данные
        )
    }

    AnimatedVisibility(
        visible = memberToDelete != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        memberToDelete?.let { member ->
            DeleteConfirmationDialog(
                title = stringResource(R.string.settings_delete_member_title),
                message = stringResource(R.string.settings_delete_member_message, member.displayLabel()),
                onDismiss = { memberToDelete = null },
                onConfirm = {
                    viewModel.deleteMember(member)
                    memberToDelete = null
                    // Закрываем диалог одобрения, если это тот же участник
                    if (memberToApprove?.id == member.id) {
                        memberToApprove = null
                    }
                }
            )
        }
    }
    
    // Диалог отклонения участника
    AnimatedVisibility(
        visible = memberToReject != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        memberToReject?.let { member ->
            DeleteConfirmationDialog(
                title = stringResource(R.string.settings_member_reject_confirm_title),
                message = stringResource(R.string.settings_member_reject_confirm_message, member.displayLabel()),
                onDismiss = { memberToReject = null },
                onConfirm = {
                    viewModel.rejectMember(member)
                    memberToReject = null
                    // Закрываем диалог одобрения, если это тот же участник
                    if (memberToApprove?.id == member.id) {
                        memberToApprove = null
                    }
                }
            )
        }
    }
    
    // Диалог одобрения участника с выбором роли
    // ВАЖНО: Проверяем, что участник все еще в списке pending перед показом диалога
    val pendingMembers = state.members.filter { it.role == MemberRole.PENDING }
    val memberToApproveStillPending = memberToApprove?.let { 
        pendingMembers.any { it.id == memberToApprove?.id && it.role == MemberRole.PENDING }
    } ?: false
    
    AnimatedVisibility(
        visible = memberToApprove != null && memberToApproveStillPending,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        memberToApprove?.let { member ->
            var selectedRole by remember { mutableStateOf(MemberRole.CHILD) }
            
            AlertDialog(
                onDismissRequest = { memberToApprove = null },
                shape = RoundedCornerShape(FhTheme.radius.card),
                title = { Text(stringResource(R.string.settings_member_approve)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_member_dialog_type_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // КРИТИЧНО: Не показываем ADMIN в списке, если уже есть ADMIN
                        // (в семье может быть только один ADMIN)
                        val hasAdmin = state.members.any { it.role == MemberRole.ADMIN }
                        val availableRoles = MemberRole.values().filter { 
                            it != MemberRole.PENDING && 
                            (it != MemberRole.ADMIN || !hasAdmin)  // ADMIN только если его еще нет
                        }
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
                                )
                                Text(
                                    text = when (role) {
                                        MemberRole.ADMIN -> stringResource(
                                            R.string.settings_member_dialog_role_admin,
                                        )
                                        MemberRole.PARENT -> stringResource(R.string.settings_member_dialog_role_parent)
                                        MemberRole.CHILD -> stringResource(R.string.settings_member_dialog_role_child)
                                        MemberRole.PENDING -> stringResource(
                                            R.string.settings_member_dialog_role_child,
                                        )
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.approveMember(member, selectedRole)
                            memberToApprove = null
                        },
                        modifier = Modifier.fhDialogAction(),
                    ) {
                        Text(stringResource(R.string.settings_member_approve))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { memberToApprove = null },
                        modifier = Modifier.fhDialogAction(),
                    ) {
                        Text(stringResource(R.string.settings_close))
                    }
                }
            )
        }
    }
    
    // Диалог выбора формата экспорта
    AnimatedVisibility(
        visible = showExportDialog,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        ExportFormatDialog(
            onDismiss = { showExportDialog = false },
            onFormatSelected = { format ->
                viewModel.requestPlaintextExport(PlaintextExportPendingRequest.Format(format))
                showExportDialog = false
            }
        )
    }

    state.pendingPlaintextExport?.let { pendingRequest ->
        val inProgress = when (pendingRequest) {
            is PlaintextExportPendingRequest.Format -> state.isExporting
            PlaintextExportPendingRequest.Backup -> state.isBackupInProgress
        }
        PlaintextExportWarningDialog(
            request = pendingRequest,
            isInProgress = inProgress,
            onConfirm = {
                scope.launch {
                    val shareIntent = viewModel.confirmPlaintextExport()
                    if (shareIntent != null) {
                        launchIntentSafely(context, shareIntent)
                    }
                }
            },
            onCancel = { viewModel.cancelPlaintextExport() }
        )
    }
}


private fun parseHexColor(hex: String): Color? {
    val sanitized = if (hex.startsWith("#")) hex else "#$hex"
    return try {
        Color(AndroidColor.parseColor(sanitized))
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
private fun avatarColorOrFallback(hex: String?): Color {
    return parseHexColor(hex.orEmpty()) ?: MaterialTheme.colorScheme.primaryContainer
}

private fun launchIntentSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                UserFacingErrorMapper.message(
                    context,
                    it,
                    UserFacingErrorContext.General,
                    tag = "SettingsScreen",
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
}

@Composable
fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (ExportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf<ExportFormat?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.settings_export_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportFormat.values().forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == format,
                                role = Role.RadioButton,
                                onClick = { selectedFormat = format },
                            )
                            .fhDialogAction(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = when (format) {
                                ExportFormat.CSV -> stringResource(R.string.settings_export_option_csv)
                                ExportFormat.JSON -> stringResource(R.string.settings_export_option_json)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedFormat?.let { onFormatSelected(it) }
                },
                enabled = selectedFormat != null,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.settings_export_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.settings_export_cancel))
            }
        }
    )
}

@Composable
fun SyncSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialAccountId: String? = null,
    accountReadOnly: Boolean = false
) {
    val context = LocalContext.current
    var accountId by remember { mutableStateOf(initialAccountId.orEmpty()) }
    var showQr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(FhTheme.radius.card),
        title = { Text(stringResource(R.string.settings_sync_dialog_title)) },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = accountId,
                    onValueChange = { if (!accountReadOnly) accountId = it },
                    label = { Text(stringResource(R.string.settings_sync_dialog_account_hint)) },
                    singleLine = true,
                    readOnly = accountReadOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (accountReadOnly && accountId.isNotBlank()) {
                    Column {
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("familyId", accountId))
                                Toast.makeText(
                                    context,
                                    R.string.settings_family_id_copied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fhDialogAction(),
                        ) {
                            Text(stringResource(R.string.settings_family_copy_id))
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        context.getString(
                                            R.string.settings_family_share_text,
                                            "familyhub://join?familyId=$accountId",
                                        ),
                                    )
                                }
                                launchIntentSafely(
                                    context,
                                    Intent.createChooser(
                                        intent,
                                        context.getString(R.string.settings_family_share_chooser),
                                    ),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fhDialogAction(),
                        ) {
                            Icon(
                                FhIcons.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_family_share_id),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        TextButton(
                            onClick = { showQr = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fhDialogAction(),
                        ) {
                            Text(stringResource(R.string.settings_family_show_qr))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(accountId) },
                enabled = accountId.isNotBlank(),
                modifier = Modifier.fhDialogAction(),
            ) {
                Icon(
                    FhIcons.CloudUpload,
                    contentDescription = stringResource(R.string.settings_sync_enable_button),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.settings_sync_dialog_confirm),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(stringResource(R.string.settings_sync_dialog_cancel))
            }
        }
    )

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            shape = RoundedCornerShape(FhTheme.radius.card),
            title = { Text(stringResource(R.string.settings_family_qr_title)) },
            text = {
                val qrBitmap = remember(accountId) { generateQrBitmap("familyhub://join?familyId=$accountId", 512) }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.settings_family_qr_cd),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(accountId)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQr = false },
                    modifier = Modifier.fhDialogAction(),
                ) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }
}

private fun generateQrBitmap(text: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MemberAvatar(
    member: FamilyMember,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(avatarColorOrFallback(member.avatarColor))
        )
        if (member.photoUrl != null) {
            RemoteImage(
                url = member.photoUrl,
                contentDescription = member.displayLabel(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun PendingMemberRow(
    member: FamilyMember,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemberAvatar(
                    member = member,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(
                        text = member.displayLabel(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_member_status_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FHTonalButton(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        FhIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_member_approve),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                FHTonalButton(
                    onClick = onReject,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        FhIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_member_reject),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MemberRow(
    member: FamilyMember,
    currentUserId: String,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ThemedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemberAvatar(
                    member = member,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(
                        text = member.displayLabel(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (member.role) {
                            MemberRole.ADMIN -> stringResource(R.string.settings_member_dialog_role_admin)
                            MemberRole.PARENT -> stringResource(R.string.settings_member_dialog_role_parent)
                            MemberRole.CHILD -> stringResource(R.string.settings_member_dialog_role_child)
                            MemberRole.PENDING -> stringResource(R.string.settings_member_dialog_role_child)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canManage && member.id != currentUserId) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(FhIcons.Edit, contentDescription = stringResource(R.string.common_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(FhIcons.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}
