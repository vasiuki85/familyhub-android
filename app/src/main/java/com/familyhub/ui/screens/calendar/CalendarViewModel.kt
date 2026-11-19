package com.familyhub.ui.screens.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.EventReminderScheduler
import com.familyhub.data.repository.FamilyEventRepository
import com.familyhub.data.repository.FamilyGoalsRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.TaskRepository
import com.familyhub.data.repository.WeeklyMenuRepository
import com.familyhub.domain.model.EventOccurrence
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.WeekUtils
import com.familyhub.domain.utils.filterByVisibility
import com.familyhub.domain.utils.getOccurrencesBetween
import kotlinx.coroutines.Dispatchers
import com.familyhub.domain.utils.IdGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

sealed interface CalendarEntry {
    val id: String
    val startTime: Long
    val title: String
    val type: CalendarEntryType
}

enum class CalendarEntryType {
    EVENT,
    TASK,
    MENU,
    GOAL,
}

data class CalendarEventEntry(
    val occurrence: EventOccurrence
) : CalendarEntry {
    override val id: String = "${occurrence.event.id}_${occurrence.startTime}"
    override val startTime: Long = occurrence.startTime
    override val title: String = occurrence.event.title
    override val type: CalendarEntryType = CalendarEntryType.EVENT
}

data class CalendarTaskEntry(
    val task: Task
) : CalendarEntry {
    init {
        require(task.dueDate != null) { "Task must have a due date to be shown on the calendar" }
    }

    override val id: String = task.id
    override val startTime: Long = task.dueDate!!
    override val title: String = task.title
    override val type: CalendarEntryType = CalendarEntryType.TASK
}

data class CalendarMenuEntry(
    val entry: MenuEntry,
    val dayStartMillis: Long,
    override val title: String,
) : CalendarEntry {
    override val id: String = entry.id
    override val startTime: Long = dayStartMillis
    override val type: CalendarEntryType = CalendarEntryType.MENU
}

data class CalendarGoalEntry(
    val goalId: String,
    override val title: String,
    override val startTime: Long,
) : CalendarEntry {
    override val id: String = goalId
    override val type: CalendarEntryType = CalendarEntryType.GOAL
}

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val entries: List<CalendarEntry>,
    val indicators: CalendarDayIndicators,
)

data class CalendarUiState(
    val currentMonth: YearMonth,
    val days: List<CalendarDay>,
    val selectedDate: LocalDate,
    val selectedEntries: List<CalendarEntry>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUserRole: MemberRole = MemberRole.PENDING,
) {
    val canMutateFamily: Boolean
        get() = currentUserRole != MemberRole.PENDING
}

private data class CalendarUserContext(
    val userId: String,
    val role: MemberRole,
)

private data class CalendarMonthData(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val events: List<com.familyhub.domain.model.FamilyEvent>,
    val tasks: List<Task>,
    val goalDetails: List<com.familyhub.domain.model.FamilyGoalDetail>,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventRepository: FamilyEventRepository,
    private val taskRepository: TaskRepository,
    private val memberRepository: FamilyMemberRepository,
    private val goalsRepository: FamilyGoalsRepository,
    private val weeklyMenuRepository: WeeklyMenuRepository,
    private val eventReminderScheduler: EventReminderScheduler,
    private val idGenerator: IdGenerator,
    private val appSettingsDataStore: AppSettingsDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val monthState = MutableStateFlow(YearMonth.now())
    private val selectedDateState = MutableStateFlow(LocalDate.now())

    private val eventsFlow = eventRepository.getAllEvents()
    private val tasksFlow = taskRepository.getAllTasks()
    private val goalsFlow = goalsRepository.getAllGoalDetails()
    private val menusFlow = weeklyMenuRepository.observeMenuHistory()
    private val currentUserFlow = combine(
        appSettingsDataStore.settingsFlow,
        memberRepository.getAllMembers(),
    ) { settings, members ->
        CalendarUserContext(
            userId = settings.currentUserId,
            role = members.firstOrNull { it.id == settings.currentUserId }?.role
                ?: MemberRole.PENDING,
        )
    }
    private val mutationError = MutableStateFlow<String?>(null)

    private val initialState = CalendarUiState(
        currentMonth = monthState.value,
        days = emptyList(),
        selectedDate = selectedDateState.value,
        selectedEntries = emptyList(),
        isLoading = true,
        errorMessage = null
    )

    private val projection = combine(
        combine(
            monthState,
            selectedDateState,
            eventsFlow,
            tasksFlow,
            goalsFlow,
        ) { month, selectedDate, events, tasks, goalDetails ->
            CalendarMonthData(month, selectedDate, events, tasks, goalDetails)
        },
        menusFlow,
        currentUserFlow,
    ) { monthData, menus, userContext ->
        val month = monthData.month
        val selectedDate = monthData.selectedDate
        val events = monthData.events
        val tasks = monthData.tasks
        val goalDetails = monthData.goalDetails
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        val rangeStart = monthStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rangeEnd = LocalDateTime.of(monthEnd, LocalTime.MAX)
            .atZone(zoneId).toInstant().toEpochMilli()

        val calendarStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val calendarEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val gridRangeStart = calendarStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val gridRangeEnd = LocalDateTime.of(calendarEnd, LocalTime.MAX)
            .atZone(zoneId).toInstant().toEpochMilli()

        val eventEntries = events.flatMap { event ->
            event.getOccurrencesBetween(rangeStart, rangeEnd)
                .map { CalendarEventEntry(EventOccurrence(event, it)) }
        }

        val taskEntries = tasks
            .filter { it.status == TaskStatus.TODO && it.dueDate != null }
            .filterByVisibility(userContext.userId)
            .mapNotNull { task ->
                val dueDate = task.dueDate!!
                if (dueDate in rangeStart..rangeEnd) {
                    CalendarTaskEntry(task)
                } else {
                    null
                }
            }

        val goalEntries = goalDetails.mapNotNull { detail ->
            val deadline = detail.goal.deadlineAt ?: return@mapNotNull null
            if (detail.goal.status != FamilyGoalStatus.ACTIVE) return@mapNotNull null
            if (deadline !in gridRangeStart..gridRangeEnd) return@mapNotNull null
            CalendarGoalEntry(
                goalId = detail.goal.id,
                title = detail.goal.title,
                startTime = deadline,
            )
        }

        val menuEntries = menus.flatMap { menu ->
            menuEntriesInRange(menu, gridRangeStart, gridRangeEnd)
        }

        val allEntries = (eventEntries + taskEntries + goalEntries + menuEntries).sortedBy { it.startTime }
        val entriesByDate = allEntries.groupBy { entry ->
            Instant.ofEpochMilli(entry.startTime).atZone(zoneId).toLocalDate()
        }

        val days = generateSequence(calendarStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(calendarEnd) }
            .map { date ->
                val dayEntries = entriesByDate[date]?.sortedBy { it.startTime } ?: emptyList()
                CalendarDay(
                    date = date,
                    isCurrentMonth = date.month == month.month,
                    entries = dayEntries,
                    indicators = CalendarDayIndicatorMapper.indicatorsForDay(
                        hasEvents = dayEntries.any { it.type == CalendarEntryType.EVENT },
                        hasTasks = dayEntries.any { it.type == CalendarEntryType.TASK },
                        hasGoals = dayEntries.any { it.type == CalendarEntryType.GOAL },
                        hasMenu = dayEntries.any { it.type == CalendarEntryType.MENU },
                    ),
                )
            }
            .toList()

        val effectiveSelectedDate = when {
            YearMonth.from(selectedDate) == month -> selectedDate
            else -> monthStart
        }

        val selectedEntries = entriesByDate[effectiveSelectedDate]?.sortedBy { it.startTime }
            ?: emptyList()

        CalendarUiState(
            currentMonth = month,
            days = days,
            selectedDate = effectiveSelectedDate,
            selectedEntries = selectedEntries,
            isLoading = false,
            errorMessage = null,
            currentUserRole = userContext.role,
        )
    }.catch {
        emit(
            initialState.copy(
                isLoading = false,
                errorMessage = context.getString(R.string.calendar_error_load),
            )
        )
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<CalendarUiState> = combine(projection, mutationError) { projected, error ->
        projected.copy(errorMessage = error ?: projected.errorMessage)
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialState
    )

    fun goToPreviousMonth() {
        monthState.value = monthState.value.minusMonths(1)
        selectedDateState.value = monthState.value.atDay(1)
    }

    fun goToNextMonth() {
        monthState.value = monthState.value.plusMonths(1)
        selectedDateState.value = monthState.value.atDay(1)
    }

    fun selectDate(date: LocalDate) {
        monthState.value = YearMonth.from(date)
        selectedDateState.value = date
    }

    fun createEvent(
        title: String,
        startDateTime: Long,
        durationMinutes: Int,
        recurrence: com.familyhub.domain.model.EventRecurrence,
        reminderMinutesBefore: Int?
    ) {
        viewModelScope.launch {
            try {
                val userContext = currentUserFlow.first()
                if (userContext.userId.isBlank() || userContext.role == MemberRole.PENDING) {
                    return@launch
                }

                val newEvent = com.familyhub.domain.model.FamilyEvent(
                    id = idGenerator.newId(),
                    title = title,
                    startDateTime = startDateTime,
                    createdByUserId = userContext.userId,
                    durationMinutes = durationMinutes,
                    recurrence = recurrence,
                    reminderMinutesBefore = reminderMinutesBefore
                )
                eventRepository.upsertEvent(newEvent)
                if (reminderMinutesBefore != null) {
                    eventReminderScheduler.scheduleReminder(newEvent)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutationError.value = context.getString(R.string.calendar_error_save)
            }
        }
    }

    fun clearError() {
        mutationError.value = null
    }

    fun getWeekdayLabels(): List<String> {
        val locale = Locale.getDefault()
        return listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        ).map { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    private fun menuEntriesInRange(
        menu: WeeklyMenu,
        rangeStart: Long,
        rangeEnd: Long,
    ): List<CalendarMenuEntry> {
        val weekStart = menu.startDateMillis
        return menu.entries.mapNotNull { entry ->
            val dayStart = WeekUtils.dayStart(weekStart, entry.dayOfWeek)
            if (dayStart !in rangeStart..rangeEnd) return@mapNotNull null
            val title = entry.customDish?.takeIf { it.isNotBlank() }
                ?: context.getString(mealTypeTitleRes(entry.mealType))
            CalendarMenuEntry(
                entry = entry,
                dayStartMillis = dayStart,
                title = title,
            )
        }
    }

    private fun mealTypeTitleRes(mealType: com.familyhub.domain.model.MealType): Int =
        when (mealType) {
            com.familyhub.domain.model.MealType.BREAKFAST -> R.string.meal_breakfast
            com.familyhub.domain.model.MealType.LUNCH -> R.string.meal_lunch
            com.familyhub.domain.model.MealType.DINNER -> R.string.meal_dinner
            com.familyhub.domain.model.MealType.SNACK -> R.string.meal_snack
        }
}
