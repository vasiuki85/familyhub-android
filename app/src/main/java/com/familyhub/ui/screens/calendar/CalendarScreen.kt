package com.familyhub.ui.screens.calendar

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.theme.FhLayout
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.ui.screens.common.EventAddDialog
import com.familyhub.ui.theme.FhTheme
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import com.familyhub.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.navigation.NavController
import com.familyhub.ui.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEventDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    }
    val weekdayLabels = remember { viewModel.getWeekdayLabels() }
    val today = remember { LocalDate.now() }

    FhScreenScaffold(
        title = stringResource(R.string.nav_calendar),
        actions = {
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(
                    imageVector = FhIcons.MoreVert,
                    contentDescription = stringResource(R.string.secondary_actions_cd),
                )
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.calendar_sync_menu_action)) },
                    onClick = {
                        showOverflowMenu = false
                        navController.navigate(NavRoutes.CALENDAR_SYNC)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = FhIcons.Settings,
                            contentDescription = null,
                        )
                    },
                )
            }
        },
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
        floatingActionButton = {
            if (!state.isLoading && state.canMutateFamily) {
                FloatingActionButton(onClick = { showEventDialog = true }) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.home_add_event_cd)
                    )
                }
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            FhListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = FhTheme.spacing.lg,
                        end = FhTheme.spacing.lg,
                        top = FhTheme.spacing.lg,
                        bottom = FhLayout.listBottomPadding(
                            hasFloatingActionButton = state.canMutateFamily,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.lg)
            ) {
                MonthHeader(
                    title = state.currentMonth.format(monthFormatter),
                    onPrevious = { viewModel.goToPreviousMonth() },
                    onNext = { viewModel.goToNextMonth() }
                )

                WeekdayRow(labels = weekdayLabels)

                CalendarGrid(
                    days = state.days,
                    selectedDate = state.selectedDate,
                    today = today,
                    onDaySelected = { viewModel.selectDate(it) }
                )

                SelectedDayEntries(
                    selectedDate = state.selectedDate,
                    entries = state.selectedEntries,
                    canAddEvent = state.canMutateFamily,
                    onAddEvent = { showEventDialog = true },
                )
            }
        }
    }

    if (showEventDialog && state.canMutateFamily) {
        EventAddDialog(
            event = null,
            initialDate = state.selectedDate,
            onDismiss = { showEventDialog = false },
            onConfirm = { title, startDateTime, durationMinutes, recurrence, reminderMinutes ->
                viewModel.createEvent(title, startDateTime, durationMinutes, recurrence, reminderMinutes)
                showEventDialog = false
            }
        )
    }
}

@Composable
private fun MonthHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = FhIcons.ChevronLeft,
                contentDescription = stringResource(R.string.calendar_prev_month_cd)
            )
        }

        Text(
            text = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onNext) {
            Icon(
                imageVector = FhIcons.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next_month_cd)
            )
        }
    }
}

@Composable
private fun WeekdayRow(labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    days: List<CalendarDay>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDaySelected: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs)) {
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs)
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        isSelected = CalendarDayIndicatorMapper.isSelected(day.date, selectedDate),
                        isToday = CalendarDayIndicatorMapper.isToday(day.date, today),
                        onDaySelected = onDaySelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImportantContent = CalendarDayIndicatorMapper.hasImportantContent(day.indicators)
    val dayMillis = day.date
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val formattedDate = TimeUtils.formatDate(dayMillis)
    val dayDescription = buildCalendarDayContentDescription(day.indicators, isToday, isSelected)
    val highlight = CalendarDayCellVisuals.highlight(isSelected = isSelected, isToday = isToday)

    val backgroundColor = when {
        highlight == CalendarDayCellHighlight.SELECTED_FILL -> {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        }
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        hasImportantContent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val borderColor = if (highlight == CalendarDayCellHighlight.TODAY_OUTLINE) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(FhTheme.radius.chip))
            .background(backgroundColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(FhTheme.radius.chip),
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = listOf(formattedDate, dayDescription).joinToString(". ")
            }
            .clickable(role = Role.Button) { onDaySelected(day.date) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = when {
                    !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            DayIndicators(indicators = day.indicators)
        }
    }
}

@Composable
private fun buildCalendarDayContentDescription(
    indicators: CalendarDayIndicators,
    isToday: Boolean,
    isSelected: Boolean,
): String {
    val parts = mutableListOf<String>()
    if (isToday) parts += stringResource(R.string.calendar_day_today_cd)
    if (isSelected) parts += stringResource(R.string.calendar_day_selected_cd)
    indicators.visibleKinds.forEach { kind ->
        parts += when (kind) {
            CalendarContentKind.EVENT -> stringResource(R.string.calendar_day_content_events)
            CalendarContentKind.TASK -> stringResource(R.string.calendar_day_content_tasks)
            CalendarContentKind.GOAL -> stringResource(R.string.calendar_day_content_goals)
            CalendarContentKind.MENU -> stringResource(R.string.calendar_day_content_menu)
        }
    }
    if (indicators.hasMore) {
        parts += stringResource(R.string.calendar_day_content_more)
    }
    if (parts.isEmpty()) {
        parts += stringResource(R.string.calendar_day_content_none)
    }
    return parts.joinToString(", ")
}

@Composable
private fun DayIndicators(indicators: CalendarDayIndicators) {
    if (indicators.visibleKinds.isEmpty() && !indicators.hasMore) return
    Row(
        modifier = Modifier.testTag(CalendarDayCellVisuals.EVENT_INDICATOR_TAG),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicators.visibleKinds.forEach { kind ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(indicatorColor(kind)),
            )
        }
        if (indicators.hasMore) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun indicatorColor(kind: CalendarContentKind): Color {
    val modules = FhTheme.modules
    return when (kind) {
        CalendarContentKind.EVENT -> MaterialTheme.colorScheme.secondary
        CalendarContentKind.TASK -> modules.task.accent
        CalendarContentKind.GOAL -> modules.goals.accent
        CalendarContentKind.MENU -> modules.food.accent
    }
}

@Composable
private fun SelectedDayEntries(
    selectedDate: LocalDate,
    entries: List<CalendarEntry>,
    canAddEvent: Boolean,
    onAddEvent: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm)) {
        val dateHeadingFormatter = remember {
            DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
        }
        val formattedDate = selectedDate.format(dateHeadingFormatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.calendar_selected_day_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
            ) {
                FhEmptyState(
                    icon = FhIcons.Event,
                    title = stringResource(R.string.calendar_empty_day_title, formattedDate),
                    actionLabel = stringResource(R.string.calendar_empty_day_action).takeIf { canAddEvent },
                    onAction = onAddEvent.takeIf { canAddEvent },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            entries.forEach { entry ->
                ThemedCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = cardToneFor(entry.type),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FhTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = TimeUtils.formatTime(entry.startTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        when (entry) {
                            is CalendarEventEntry -> {
                                if (entry.occurrence.event.durationMinutes > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.home_event_duration,
                                            entry.occurrence.event.durationMinutes
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is CalendarTaskEntry -> {
                                Text(
                                    text = stringResource(R.string.calendar_task_deadline_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is CalendarMenuEntry -> {
                                Text(
                                    text = stringResource(R.string.calendar_menu_entry_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is CalendarGoalEntry -> {
                                Text(
                                    text = stringResource(R.string.calendar_goal_deadline_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cardToneFor(type: CalendarEntryType): FhCardTone = when (type) {
    CalendarEntryType.EVENT -> FhCardTone.INFO
    CalendarEntryType.TASK -> FhCardTone.INFO
    CalendarEntryType.MENU -> FhCardTone.INFO
    CalendarEntryType.GOAL -> FhCardTone.INFO
}
