package com.familyhub.ui.screens.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.familyhub.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.ui.theme.FamilyHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarDayCellLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun calendarDayCell_largeFontScale_keepsMinimumTouchTarget() {
        var cellHeightPx = 0
        val day = CalendarDay(
            date = LocalDate.of(2026, 8, 8),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(
                hasEvents = true,
                hasTasks = false,
                hasGoals = false,
                hasMenu = false,
            ),
        )

        composeRule.setContent {
            LargeFontScale {
                FamilyHubTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        CalendarDayCellHarness(
                            day = day,
                            isSelected = false,
                            isToday = true,
                            onDaySelected = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                cellHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cellHeightPx / composeRule.density.density
        assertTrue(
            "Calendar cell height ${heightDp}dp is below 48dp minimum",
            heightDp >= 48f,
        )
    }

    @Test
    fun calendarDayCell_accessibilityDescription_listsContentKinds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val eventsLabel = context.getString(R.string.calendar_day_content_events)
        val tasksLabel = context.getString(R.string.calendar_day_content_tasks)
        val goalsLabel = context.getString(R.string.calendar_day_content_goals)
        val moreLabel = context.getString(R.string.calendar_day_content_more)

        val day = CalendarDay(
            date = LocalDate.of(2026, 8, 8),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(
                hasEvents = true,
                hasTasks = true,
                hasGoals = true,
                hasMenu = true,
            ),
        )

        composeRule.setContent {
            FamilyHubTheme {
                CalendarDayCellHarness(
                    day = day,
                    isSelected = true,
                    isToday = false,
                    onDaySelected = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescriptionSubstring(eventsLabel)).assertExists()
        composeRule.onNode(hasContentDescriptionSubstring(tasksLabel)).assertExists()
        composeRule.onNode(hasContentDescriptionSubstring(goalsLabel)).assertExists()
        composeRule.onNode(hasContentDescriptionSubstring(moreLabel)).assertExists()
    }

    @Test
    fun todayAndSelectedCells_useDifferentSemantics() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val todayLabel = context.getString(R.string.calendar_day_today_cd)
        val selectedLabel = context.getString(R.string.calendar_day_selected_cd)

        val today = CalendarDay(
            date = LocalDate.of(2026, 8, 1),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(false, false, false, false),
        )
        val selected = CalendarDay(
            date = LocalDate.of(2026, 8, 8),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(false, false, false, false),
        )

        composeRule.setContent {
            FamilyHubTheme {
                CalendarDayCellHarness(day = today, isSelected = false, isToday = true, onDaySelected = {})
                CalendarDayCellHarness(day = selected, isSelected = true, isToday = false, onDaySelected = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescriptionSubstring(todayLabel)).assertExists()
        composeRule.onNode(hasContentDescriptionSubstring(selectedLabel)).assertExists()
    }

    @Test
    fun calendarDayCell_visualStates_useDistinctTags() {
        val today = CalendarDay(
            date = LocalDate.of(2026, 8, 1),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(false, false, false, false),
        )
        val selected = CalendarDay(
            date = LocalDate.of(2026, 8, 8),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(false, false, false, false),
        )
        val withEvents = CalendarDay(
            date = LocalDate.of(2026, 8, 15),
            isCurrentMonth = true,
            entries = emptyList(),
            indicators = CalendarDayIndicatorMapper.indicatorsForDay(
                hasEvents = true,
                hasTasks = false,
                hasGoals = false,
                hasMenu = false,
            ),
        )

        composeRule.setContent {
            FamilyHubTheme {
                CalendarDayCellHarness(day = today, isSelected = false, isToday = true, onDaySelected = {})
                CalendarDayCellHarness(day = selected, isSelected = true, isToday = false, onDaySelected = {})
                CalendarDayCellHarness(day = withEvents, isSelected = false, isToday = false, onDaySelected = {})
            }
        }

        composeRule.waitForIdle()
        val eventsLabel = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.calendar_day_content_events)
        composeRule.onNode(androidx.compose.ui.test.hasTestTag(CalendarDayCellVisuals.TODAY_OUTLINE_TAG))
            .assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag(CalendarDayCellVisuals.SELECTED_FILL_TAG))
            .assertExists()
        composeRule.onNode(hasContentDescriptionSubstring(eventsLabel)).assertExists()
    }

    private fun hasContentDescriptionSubstring(substring: String): SemanticsMatcher =
        SemanticsMatcher("content description contains '$substring'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.contains(substring, ignoreCase = true) } == true
        }

    @Composable
    private fun LargeFontScale(content: @Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides density,
            content = content,
        )
    }

    companion object {
        private const val PHONE_WIDTH_DP = 360f
    }
}

@Composable
private fun CalendarDayCellHarness(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateTag = when {
        isSelected -> CalendarDayCellVisuals.SELECTED_FILL_TAG
        isToday -> CalendarDayCellVisuals.TODAY_OUTLINE_TAG
        else -> "calendar-day-${day.date.dayOfMonth}"
    }
    CalendarDayCell(
        day = day,
        isSelected = isSelected,
        isToday = isToday,
        onDaySelected = onDaySelected,
        modifier = modifier.testTag(stateTag),
    )
}
