package com.familyhub.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.Reward
import com.familyhub.domain.model.RewardRequest
import com.familyhub.domain.model.RewardRequestStatus
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhDialogFormBody
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.fhDialogAction
import com.familyhub.ui.components.runFormValidation
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import com.familyhub.util.TimeUtils

@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var rewardToRequest by remember { mutableStateOf<Reward?>(null) }
    val canManageRewards = state.currentUserRole == MemberRole.ADMIN ||
        state.currentUserRole == MemberRole.PARENT
    val snackbarHostState = remember { SnackbarHostState() }
    val showCatalogEmpty = !state.isLoading && state.rewards.isEmpty()
    val showFab = canManageRewards && !showCatalogEmpty

    LaunchedEffect(state.infoMessage) {
        val message = state.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearInfo()
    }

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_rewards),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.rewards_add_reward),
                    )
                }
            }
        },
    ) { paddingValues ->
        if (state.isLoading) {
            FhListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            RewardsContent(
                state = state,
                canManageRewards = canManageRewards,
                onRequestReward = { reward -> rewardToRequest = reward },
                onToggleReward = viewModel::toggleRewardActive,
                onApproveRequest = viewModel::approveRequest,
                onDeclineRequest = viewModel::declineRequest,
                onAddReward = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    if (showCreateDialog) {
        CreateRewardDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, cost ->
                viewModel.createReward(title, description, cost)
                showCreateDialog = false
            },
        )
    }

    rewardToRequest?.let { reward ->
        RewardRequestDialog(
            reward = reward,
            currentBalance = state.wallet.balance,
            onDismiss = { rewardToRequest = null },
            onConfirm = { note ->
                viewModel.requestReward(reward.id, note)
                rewardToRequest = null
            },
        )
    }
}

@Composable
private fun RewardsContent(
    state: RewardsUiState,
    canManageRewards: Boolean,
    onRequestReward: (Reward) -> Unit,
    onToggleReward: (Reward, Boolean) -> Unit,
    onApproveRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onAddReward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val membersMap = remember(state.members) { state.members.associateBy { it.id } }
    val availableTabs = remember(canManageRewards) {
        if (canManageRewards) {
            RewardTab.entries
        } else {
            RewardTab.entries.filter { it != RewardTab.PENDING_REVIEW }
        }
    }
    var selectedTab by rememberSaveable { mutableStateOf(RewardTab.CATALOG) }
    LaunchedEffect(availableTabs, selectedTab) {
        if (selectedTab !in availableTabs) {
            selectedTab = RewardTab.CATALOG
        }
    }
    val pendingCount = remember(state.requests) {
        state.requests.count { it.status == RewardRequestStatus.PENDING }
    }
    val myRequests = remember(state.requests, state.currentUserId) {
        state.requests.filter { it.requesterUserId == state.currentUserId }
    }
    val receivedRequests = remember(state.requests, state.currentUserId) {
        state.requests.filter {
            it.requesterUserId == state.currentUserId &&
                it.status == RewardRequestStatus.APPROVED
        }
    }
    val pendingRequests = remember(state.requests) {
        state.requests.filter { it.status == RewardRequestStatus.PENDING }
    }
    val canRequest = state.currentUserRole != MemberRole.PENDING

    Column(modifier = modifier) {
        RewardsHeroCard(
            balance = state.wallet.balance,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )

        RewardTabRow(
            tabs = availableTabs,
            selectedTab = selectedTab,
            pendingCount = pendingCount,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        when (selectedTab) {
            RewardTab.CATALOG -> {
                if (state.rewards.isEmpty()) {
                    FhEmptyState(
                        icon = FhIcons.EmojiEvents,
                        title = stringResource(R.string.rewards_empty_title),
                        description = if (canManageRewards) {
                            stringResource(R.string.rewards_empty_description)
                        } else {
                            stringResource(R.string.rewards_empty_catalog_member)
                        },
                        actionLabel = if (canManageRewards) {
                            stringResource(R.string.rewards_add_reward)
                        } else {
                            null
                        },
                        onAction = if (canManageRewards) onAddReward else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = FhLayout.listBottomPadding(canManageRewards),
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.rewards, key = { it.id }) { reward ->
                            RewardCatalogCard(
                                reward = reward,
                                canManage = canManageRewards,
                                canRequest = canRequest,
                                currentBalance = state.wallet.balance,
                                creatorName = membersMap[reward.createdByUserId]?.displayLabel()
                                    ?: stringResource(R.string.rewards_member_unknown),
                                onRequest = { onRequestReward(reward) },
                                onToggleActive = { onToggleReward(reward, it) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            RewardTab.MY_REQUESTS -> RewardRequestsList(
                requests = myRequests,
                membersMap = membersMap,
                emptyMessageRes = R.string.rewards_requests_empty,
                showActions = false,
                onApproveRequest = {},
                onDeclineRequest = {},
                hasFab = canManageRewards,
            )

            RewardTab.RECEIVED -> RewardRequestsList(
                requests = receivedRequests,
                membersMap = membersMap,
                emptyMessageRes = R.string.rewards_received_empty,
                showActions = false,
                onApproveRequest = {},
                onDeclineRequest = {},
                hasFab = canManageRewards,
            )

            RewardTab.PENDING_REVIEW -> RewardRequestsList(
                requests = pendingRequests,
                membersMap = membersMap,
                emptyMessageRes = R.string.rewards_pending_empty,
                showActions = true,
                onApproveRequest = onApproveRequest,
                onDeclineRequest = onDeclineRequest,
                hasFab = canManageRewards,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardTabRow(
    tabs: List<RewardTab>,
    selectedTab: RewardTab,
    pendingCount: Int,
    onTabSelected: (RewardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rewardColors = FhTheme.modules.reward
    val selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val tabLabels = tabs.map { tab ->
        if (tab == RewardTab.PENDING_REVIEW && pendingCount > 0) {
            stringResource(
                R.string.rewards_tab_pending_badge,
                stringResource(tab.titleRes),
                pendingCount,
            )
        } else {
            stringResource(tab.titleRes)
        }
    }

    com.familyhub.ui.components.FhPillTabRow(
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        tabLabels = tabLabels,
        palette = rewardColors,
        modifier = modifier,
    )
}

@Composable
private fun RewardsHeroCard(
    balance: Int,
    modifier: Modifier = Modifier,
) {
    val rewardColors = FhTheme.modules.reward

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = rewardColors.container,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(rewardColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FhIcons.AutoAwesome,
                    contentDescription = null,
                    tint = rewardColors.onAccent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rewards_hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = rewardColors.onContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.rewards_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = rewardColors.onContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = stringResource(R.string.rewards_balance_points, balance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = rewardColors.accent,
                    modifier = Modifier.padding(top = 10.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RewardRequestsList(
    requests: List<RewardRequest>,
    membersMap: Map<String, FamilyMember>,
    emptyMessageRes: Int,
    showActions: Boolean,
    onApproveRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    hasFab: Boolean,
) {
    if (requests.isEmpty()) {
        FhEmptyState(
            icon = FhIcons.EmojiEvents,
            title = stringResource(emptyMessageRes),
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = FhLayout.listBottomPadding(hasFab),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(requests, key = { it.id }) { request ->
                RewardRequestCard(
                    request = request,
                    membersMap = membersMap,
                    onApprove = { onApproveRequest(request.id) },
                    onDecline = { onDeclineRequest(request.id) },
                    showActions = showActions,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun RewardRequestCard(
    request: RewardRequest,
    membersMap: Map<String, FamilyMember>,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    showActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val requesterName = membersMap[request.requesterUserId]?.displayLabel()
        ?: stringResource(R.string.rewards_member_unknown)
    val processedByName = request.processedByUserId?.let { id ->
        membersMap[id]?.displayLabel() ?: stringResource(R.string.rewards_member_unknown)
    }
    ThemedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        tone = FhCardTone.INFO,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = request.rewardTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.rewards_cost_points, request.rewardCost),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.rewards_requester_label, requesterName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            request.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = stringResource(R.string.rewards_request_note, note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(
                    R.string.rewards_requested_at,
                    TimeUtils.formatDateTime(request.requestedAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (request.processedAt != null) {
                Text(
                    text = stringResource(
                        R.string.rewards_processed_at,
                        TimeUtils.formatDateTime(request.processedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = stringResource(
                    when (request.status) {
                        RewardRequestStatus.PENDING -> R.string.rewards_status_pending
                        RewardRequestStatus.APPROVED -> R.string.rewards_status_approved
                        RewardRequestStatus.DECLINED -> R.string.rewards_status_declined
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (request.status) {
                    RewardRequestStatus.PENDING -> MaterialTheme.colorScheme.primary
                    RewardRequestStatus.APPROVED -> MaterialTheme.colorScheme.tertiary
                    RewardRequestStatus.DECLINED -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            if (processedByName != null) {
                Text(
                    text = stringResource(R.string.rewards_processed_by, processedByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.rewards_action_approve))
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.rewards_action_decline))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRewardDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var costText by rememberSaveable { mutableStateOf("0") }
    var titleError by remember { mutableStateOf<String?>(null) }
    var costError by remember { mutableStateOf<String?>(null) }
    val titleRequired = stringResource(R.string.validation_reward_title_empty)
    val costInvalid = stringResource(R.string.validation_reward_cost_invalid)
    val titleFocusRequester = remember { FocusRequester() }
    val costFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    runFormValidation(
                        validators = listOf(
                            {
                                if (title.isBlank()) titleRequired else null
                            } to { titleError = it },
                            {
                                if ((costText.toIntOrNull() ?: -1) < 0) costInvalid else null
                            } to { costError = it },
                        ),
                        focusRequesters = listOf(titleFocusRequester, costFocusRequester),
                    ) {
                        val cost = costText.toIntOrNull() ?: 0
                        onCreate(title.trim(), description.trim().ifBlank { null }, cost)
                    }
                },
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(text = stringResource(R.string.rewards_action_save_reward))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fhDialogAction(),
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
        title = { Text(text = stringResource(R.string.rewards_add_dialog_title)) },
        text = {
            FhDialogFormBody {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    label = { Text(text = stringResource(R.string.rewards_field_title)) },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { error -> { Text(error) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = stringResource(R.string.rewards_field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = {
                        costText = it.filter { char -> char.isDigit() }
                        costError = null
                    },
                    label = { Text(text = stringResource(R.string.rewards_field_cost)) },
                    singleLine = true,
                    isError = costError != null,
                    supportingText = costError?.let { error -> { Text(error) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(costFocusRequester),
                )
            }
        },
    )
}

@Composable
private fun RewardRequestDialog(
    reward: Reward,
    currentBalance: Int,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    val progress = RewardProgressUiMapper.map(
        currentBalance = currentBalance,
        rewardCost = reward.cost,
        isActive = reward.isActive,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(note.trim().ifBlank { null }) },
                enabled = progress.canRequest,
            ) {
                Text(text = stringResource(R.string.rewards_action_send_request))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
        title = { Text(text = stringResource(R.string.rewards_request_dialog_title, reward.title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.rewards_request_dialog_cost, reward.cost),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.rewards_balance_points, currentBalance),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!progress.canRequest) {
                    Text(
                        text = stringResource(
                            R.string.rewards_progress_remaining,
                            progress.remainingPoints,
                        ),
                        color = FhTheme.modules.reward.accent,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(text = stringResource(R.string.rewards_field_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
