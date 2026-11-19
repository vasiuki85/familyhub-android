package com.familyhub.ui.screens.calendar

import com.familyhub.ui.components.FhIcons
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.ui.components.FhLoading
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.components.FHButton
import com.familyhub.ui.theme.components.FHTonalButton
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncScreen(
    navController: NavController,
    viewModel: CalendarSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.onSignInResult(account)
        } catch (e: ApiException) {
            val code = e.statusCode
            val name = GoogleSignInStatusCodes.getStatusCodeString(code)
            viewModel.onSignInError(code, name)
        }
    }

    FhScreenScaffold(
        title = stringResource(R.string.calendar_sync_title),
        collapsingTopBar = false,
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(FhIcons.Back, contentDescription = stringResource(R.string.calendar_sync_back_cd))
            }
        },
        errorMessage = uiState.error,
        onErrorDismiss = viewModel::clearError
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!uiState.isSignedIn) {
                FHButton(
                    onClick = { googleSignInLauncher.launch(viewModel.getSignInIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.calendar_sync_sign_in_button))
                }
                if (uiState.signInErrorCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.calendar_sync_error_sign_in_detailed,
                            uiState.signInErrorName ?: "",
                            uiState.signInErrorCode ?: -1
                        )
                    )
                }
            } else {
                ThemedCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = FhCardTone.INFO
                ) {
                    Text(
                        text = stringResource(
                            R.string.calendar_sync_signed_in_as,
                            uiState.googleSignInAccount?.email.orEmpty()
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (uiState.isLoading) {
                    FhLoading(modifier = Modifier.fillMaxWidth())
                } else if (uiState.needsCalendarScopeGrant) {
                    Text(stringResource(R.string.calendar_sync_grant_scope_message))
                    FHButton(onClick = { googleSignInLauncher.launch(viewModel.getSignInIntent()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.calendar_sync_grant_scope_button))
                    }
                } else {
                    if (uiState.syncCompleted) {
                        Text(stringResource(R.string.calendar_sync_done))
                    }
                    if (uiState.calendars.isEmpty()) {
                        FhEmptyState(
                            icon = FhIcons.CalendarMonth,
                            title = stringResource(R.string.calendar_sync_empty_title),
                            description = stringResource(R.string.calendar_sync_empty_body),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.calendars, key = { it.id }) { calendar ->
                                val selected = calendar.id == uiState.selectedCalendarId
                                ThemedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    tone = if (selected) FhCardTone.INFO else FhCardTone.NEUTRAL
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selected,
                                                onClick = { viewModel.onCalendarSelected(calendar.id) },
                                                role = Role.RadioButton
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(calendar.summary, modifier = Modifier.weight(1f))
                                        if (selected) {
                                            Icon(
                                                FhIcons.Check,
                                                contentDescription = stringResource(R.string.calendar_sync_selected_cd)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FHTonalButton(
                        onClick = { viewModel.saveSelectedCalendar() },
                        enabled = uiState.selectedCalendarId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.calendar_sync_save_button))
                    }

                    FHButton(
                        onClick = { viewModel.syncCalendars() },
                        enabled = uiState.selectedCalendarId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(FhIcons.Sync, contentDescription = stringResource(R.string.calendar_sync_sync_now_cd))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.calendar_sync_sync_now_button))
                    }
                }
            }
        }
    }
}
