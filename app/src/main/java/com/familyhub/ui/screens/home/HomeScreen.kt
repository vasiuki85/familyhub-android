package com.familyhub.ui.screens.home

import com.familyhub.ui.components.FhIcons
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.model.EventRecurrence
import com.familyhub.domain.model.FamilyEvent
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.Task
import com.familyhub.ui.common.toTitleRes
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.components.FhMemberAvatar
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.TaskCompletionFeedbackCard
import com.familyhub.ui.navigation.NavRoutes
import com.familyhub.ui.navigation.settingsDestination
import com.familyhub.ui.screens.common.EventAddDialog
import com.familyhub.ui.screens.settings.SettingsInitialSyncAction
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.util.TimeUtils

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard = HomeDashboardUiMapper.map(state)
    var showEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<FamilyEvent?>(null) }
    var eventToDelete by remember { mutableStateOf<FamilyEvent?>(null) }
    val context = LocalContext.current

    FhScreenScaffold(
        title = stringResource(R.string.home_redesign_title),
        backgroundDecorationAlpha = 0.12f,
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
    ) { paddingValues ->
        if (state.isLoading) {
            FhListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = FhTheme.spacing.lg,
                    top = FhTheme.spacing.sm,
                    end = FhTheme.spacing.lg,
                    bottom = maxOf(FhTheme.spacing.xxl, FhLayout.listBottomPadding(hasFloatingActionButton = false)),
                ),
                verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.lg),
            ) {
                dashboard.currentMember?.let { member ->
                    item(key = "family_header") {
                        UserProfileCard(
                            member = member,
                            familyMembers = dashboard.familyMembers,
                            weeklyScore = state.myScoreThisWeek,
                            onEditClick = {
                                navController?.navigate("${NavRoutes.EDIT_PROFILE}/${member.id}")
                            },
                        )
                    }
                }

                item(key = "daily_shortcuts") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                    ) {
                        HomeActionModule(
                            title = stringResource(R.string.home_redesign_shopping_title),
                            subtitle = state.shoppingItemsRemaining?.let { count ->
                                pluralStringResource(
                                    R.plurals.home_redesign_shopping_remaining,
                                    count,
                                    count,
                                )
                            } ?: stringResource(R.string.home_redesign_shopping_loading),
                            countBadge = state.shoppingItemsRemaining,
                            actionLabel = stringResource(R.string.home_redesign_open_shopping),
                            icon = FhIcons.ShoppingCart,
                            containerColor = FhTheme.modules.shopping.container,
                            contentColor = FhTheme.modules.shopping.onContainer,
                            accentColor = FhTheme.modules.shopping.accent,
                            onClick = { navController?.navigate(NavRoutes.SHOPPING) },
                            modifier = Modifier.weight(1f),
                        )
                        HomeActionModule(
                            title = stringResource(R.string.home_redesign_food_title),
                            subtitle = state.todayMealsCount?.let { count ->
                                pluralStringResource(
                                    R.plurals.home_redesign_food_meals_today,
                                    count,
                                    count,
                                )
                            } ?: stringResource(R.string.home_redesign_food_loading),
                            countBadge = state.todayMealsCount,
                            actionLabel = stringResource(R.string.home_redesign_open_food),
                            icon = FhIcons.RestaurantMenu,
                            containerColor = FhTheme.modules.food.container,
                            contentColor = FhTheme.modules.food.onContainer,
                            accentColor = FhTheme.modules.food.accent,
                            onClick = { navController?.navigate(NavRoutes.FOOD) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                state.completionFeedback?.let { feedback ->
                    item(key = "task_feedback") {
                        TaskCompletionFeedbackCard(
                            feedback = feedback,
                            onDismiss = viewModel::clearCompletionFeedback,
                        )
                    }
                }

                item(key = "priority_tasks_header") {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_redesign_priority_tasks),
                        actionLabel = stringResource(R.string.home_redesign_show_all),
                        onAction = { navController?.navigate(NavRoutes.TASKS) },
                    )
                }

                item(key = "priority_tasks") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = FhTheme.modules.task.container,
                        contentColor = FhTheme.modules.task.onContainer,
                    ) {
                        Column(modifier = Modifier.padding(vertical = FhTheme.spacing.sm)) {
                            if (dashboard.priorityTasks.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_no_tasks_today),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FhTheme.modules.task.onContainer.copy(alpha = 0.78f),
                                    modifier = Modifier.padding(FhTheme.spacing.lg),
                                )
                            } else {
                                dashboard.priorityTasks.forEach { task ->
                                    PriorityTaskRow(
                                        task = task,
                                        assignedMember = state.members.firstOrNull {
                                            it.id == task.assignedToUserId
                                        },
                                        onToggle = { viewModel.toggleTaskStatus(task) },
                                    )
                                }
                                if (dashboard.remainingTaskCount > 0) {
                                    TextButton(
                                        onClick = { navController?.navigate(NavRoutes.TASKS) },
                                        modifier = Modifier.padding(horizontal = FhTheme.spacing.sm),
                                    ) {
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.home_redesign_more_tasks,
                                                dashboard.remainingTaskCount,
                                                dashboard.remainingTaskCount,
                                            ),
                                            color = FhTheme.modules.task.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.familyOnboarding.visible) {
                    item(key = "family_onboarding") {
                        FamilyOnboardingCard(
                            model = state.familyOnboarding,
                            onJoinFamily = {
                                viewModel.recordFamilyOnboardingChoice(
                                    com.familyhub.data.analytics.ActivationAnalytics.Choice.JOIN,
                                )
                                navController?.navigate(
                                    NavRoutes.settingsDestination(SettingsInitialSyncAction.JOIN),
                                )
                            },
                            onConnectFamily = {
                                navController?.navigate(NavRoutes.SETTINGS)
                            },
                            onCreateFamily = {
                                viewModel.recordFamilyOnboardingChoice(
                                    com.familyhub.data.analytics.ActivationAnalytics.Choice.CREATE,
                                )
                                navController?.navigate(
                                    NavRoutes.settingsDestination(SettingsInitialSyncAction.CREATE),
                                )
                            },
                            onContinueLocally = viewModel::dismissFamilyOnboarding,
                        )
                    }
                }

                if (state.familyActivation.visible) {
                    item(key = "family_activation") {
                        FamilyActivationChecklistCard(
                            model = state.familyActivation,
                            onAction = {
                                when (state.familyActivation.action) {
                                    FamilyActivationAction.SETTINGS -> navController?.navigate(NavRoutes.SETTINGS)
                                    FamilyActivationAction.TASKS -> navController?.navigate(NavRoutes.TASKS)
                                }
                            },
                        )
                    }
                }

                if (state.displaySyncHealth().visible) {
                    item(key = "sync_health") {
                        val syncHealth = state.displaySyncHealth()
                        SyncHealthCard(
                            model = syncHealth,
                            onActionClick = {
                                when (syncHealth.action) {
                                    SyncHealthAction.SETTINGS -> {
                                        navController?.navigate(NavRoutes.SETTINGS)
                                    }
                                    else -> viewModel.onSyncHealthAction()
                                }
                            },
                        )
                    }
                }

                item(key = "events_header") {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_redesign_upcoming_events),
                        actionLabel = stringResource(R.string.home_redesign_add_event)
                            .takeIf { state.canMutateFamily },
                        actionIcon = FhIcons.Add,
                        onAction = { showEventDialog = true },
                    )
                }

                if (dashboard.upcomingEvents.isEmpty()) {
                    item(key = "empty_events") {
                        Surface(
                            onClick = { showEventDialog = true },
                            enabled = state.canMutateFamily,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(FhTheme.spacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = FhIcons.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = stringResource(R.string.home_no_upcoming_events),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = dashboard.upcomingEvents,
                        key = { "${it.event.id}_${it.startTime}" },
                    ) { occurrence ->
                        UpcomingEventCard(
                            occurrence = occurrence,
                            onAddToCalendar = {
                                val event = occurrence.event
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, event.title)
                                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, occurrence.startTime)
                                    putExtra(
                                        CalendarContract.EXTRA_EVENT_END_TIME,
                                        occurrence.startTime + event.durationMinutes * 60 * 1000L,
                                    )
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.home_calendar_not_available),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onEdit = { eventToEdit = occurrence.event },
                            onDelete = { eventToDelete = occurrence.event },
                            canManage = state.canMutateFamily,
                        )
                    }
                }
            }
        }
    }

    if (showEventDialog && state.canMutateFamily) {
        EventAddDialog(
            event = null,
            onDismiss = { showEventDialog = false },
            onConfirm = { title, startDateTime, durationMinutes, recurrence, reminderMinutes ->
                viewModel.createEvent(
                    title,
                    startDateTime,
                    durationMinutes,
                    recurrence,
                    reminderMinutes,
                )
                showEventDialog = false
            },
        )
    }

    eventToEdit?.takeIf { state.canMutateFamily }?.let { event ->
        EventAddDialog(
            event = event,
            onDismiss = { eventToEdit = null },
            onConfirm = { title, startDateTime, durationMinutes, recurrence, reminderMinutes ->
                viewModel.updateEvent(
                    event.copy(
                        title = title,
                        startDateTime = startDateTime,
                        durationMinutes = durationMinutes,
                        recurrence = recurrence,
                        reminderMinutesBefore = reminderMinutes,
                    ),
                )
                eventToEdit = null
            },
        )
    }

    eventToDelete?.takeIf { state.canMutateFamily }?.let { event ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_event_title),
            message = stringResource(R.string.delete_event_message, event.title),
            onDismiss = { eventToDelete = null },
            onConfirm = {
                viewModel.deleteEvent(event)
                eventToDelete = null
            },
        )
    }
}

@Composable
private fun HomeActionModule(
    title: String,
    subtitle: String,
    actionLabel: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countBadge: Int? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(150.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOf(title, subtitle, actionLabel).joinToString(". ")
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(FhTheme.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    contentColor = accentColor,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                countBadge?.let { count ->
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    actionIcon: ImageVector = FhIcons.Forward,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun PriorityTaskRow(
    task: Task,
    assignedMember: FamilyMember?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = false,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = FhTheme.spacing.sm, vertical = FhTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                assignedMember?.let { member ->
                    FhMemberAvatar(
                        member = member,
                        size = 20.dp,
                        decorative = true,
                    )
                }
                val supportingText = when {
                    task.rewardPoints != null -> {
                        stringResource(R.string.task_points_format, task.rewardPoints)
                    }
                    task.dueDate != null -> {
                        val now = TimeUtils.now()
                        when {
                            TimeUtils.isSameDay(task.dueDate, now) -> {
                                stringResource(R.string.home_redesign_task_today)
                            }
                            TimeUtils.isBeforeDay(task.dueDate, now) -> {
                                val overdueDays = TimeUtils.daysBetweenStartOfDays(task.dueDate, now)
                                pluralStringResource(
                                    R.plurals.home_redesign_task_overdue_days,
                                    overdueDays,
                                    overdueDays,
                                )
                            }
                            else -> TimeUtils.formatDate(task.dueDate)
                        }
                    }
                    else -> stringResource(R.string.home_redesign_task_today)
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = FhTheme.modules.task.onContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventCard(
    occurrence: EventOccurrence,
    onAddToCalendar: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canManage: Boolean,
) {
    val event = occurrence.event
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = FhTheme.spacing.lg,
                top = FhTheme.spacing.md,
                end = FhTheme.spacing.xs,
                bottom = FhTheme.spacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = FhIcons.Event,
                    contentDescription = null,
                    modifier = Modifier.padding(FhTheme.spacing.sm),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FhTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeUtils.formatDateTime(occurrence.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.recurrence != EventRecurrence.NONE) {
                    Text(
                        text = stringResource(
                            R.string.home_event_recurrence,
                            stringResource(event.recurrence.toTitleRes()),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onAddToCalendar) {
                Icon(
                    imageVector = FhIcons.CalendarToday,
                    contentDescription = stringResource(R.string.home_add_to_calendar_cd),
                )
            }
            if (canManage) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = FhIcons.Edit,
                        contentDescription = stringResource(R.string.home_edit_event_cd),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = FhIcons.Delete,
                        contentDescription = stringResource(R.string.home_delete_event_cd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
