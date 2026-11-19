package com.familyhub.ui.screens

import androidx.compose.ui.unit.dp
import com.familyhub.ui.screens.report.WeeklyReportLayoutTokens
import com.familyhub.ui.screens.stats.MemberStatisticsLayoutTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsScreenDensityPolicyTest {

    @Test
    fun memberStatistics_usesCompactScreenAndCardSpacing() {
        assertEquals(8.dp, MemberStatisticsLayoutTokens.ScreenVerticalPadding)
        assertEquals(10.dp, MemberStatisticsLayoutTokens.CardSpacing)
        assertEquals(6.dp, MemberStatisticsLayoutTokens.ContentSpacing)
        assertTrue(
            MemberStatisticsLayoutTokens.ContentSpacing <
                MemberStatisticsLayoutTokens.CardSpacing
        )
    }

    @Test
    fun weeklyReport_usesCompactScreenAndCardSpacing() {
        assertEquals(8.dp, WeeklyReportLayoutTokens.ScreenVerticalPadding)
        assertEquals(10.dp, WeeklyReportLayoutTokens.CardSpacing)
        assertEquals(6.dp, WeeklyReportLayoutTokens.ContentSpacing)
        assertTrue(
            WeeklyReportLayoutTokens.ItemDividerPadding <
                WeeklyReportLayoutTokens.ContentSpacing
        )
    }
}
