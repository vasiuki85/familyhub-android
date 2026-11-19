package com.familyhub.ui.screens.calendar

internal enum class CalendarDayCellHighlight {
    NONE,
    TODAY_OUTLINE,
    SELECTED_FILL,
}

internal object CalendarDayCellVisuals {
    const val SELECTED_FILL_TAG = "calendar-day-selected-fill"
    const val TODAY_OUTLINE_TAG = "calendar-day-today-outline"
    const val EVENT_INDICATOR_TAG = "calendar-day-event-indicator"

    fun highlight(
        isSelected: Boolean,
        isToday: Boolean,
    ): CalendarDayCellHighlight = when {
        isSelected -> CalendarDayCellHighlight.SELECTED_FILL
        isToday -> CalendarDayCellHighlight.TODAY_OUTLINE
        else -> CalendarDayCellHighlight.NONE
    }

    fun hasEventIndicator(indicators: CalendarDayIndicators): Boolean =
        indicators.visibleKinds.isNotEmpty() || indicators.hasMore
}
