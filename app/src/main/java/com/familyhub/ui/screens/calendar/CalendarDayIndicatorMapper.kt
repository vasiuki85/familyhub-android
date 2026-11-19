package com.familyhub.ui.screens.calendar

enum class CalendarContentKind {
    EVENT,
    TASK,
    GOAL,
    MENU,
}

data class CalendarDayIndicators(
    val visibleKinds: List<CalendarContentKind>,
    val hasMore: Boolean,
)

object CalendarDayIndicatorMapper {
    private val kindOrder = listOf(
        CalendarContentKind.EVENT,
        CalendarContentKind.TASK,
        CalendarContentKind.GOAL,
        CalendarContentKind.MENU,
    )

    fun indicatorsForDay(
        hasEvents: Boolean,
        hasTasks: Boolean,
        hasGoals: Boolean,
        hasMenu: Boolean,
    ): CalendarDayIndicators {
        val present = kindOrder.filter { kind ->
            when (kind) {
                CalendarContentKind.EVENT -> hasEvents
                CalendarContentKind.TASK -> hasTasks
                CalendarContentKind.GOAL -> hasGoals
                CalendarContentKind.MENU -> hasMenu
            }
        }
        return CalendarDayIndicators(
            visibleKinds = present.take(3),
            hasMore = present.size > 3,
        )
    }

    fun isToday(date: java.time.LocalDate, today: java.time.LocalDate): Boolean = date == today

    fun isSelected(date: java.time.LocalDate, selectedDate: java.time.LocalDate): Boolean =
        date == selectedDate

    fun hasImportantContent(indicators: CalendarDayIndicators): Boolean =
        indicators.visibleKinds.isNotEmpty() || indicators.hasMore
}
