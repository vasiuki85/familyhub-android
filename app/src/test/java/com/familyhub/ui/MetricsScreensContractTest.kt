package com.familyhub.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsScreensContractTest {

    @Test
    fun statisticsAndReportScreens_shareFhFamilyMetricsCard() {
        val statsSource = source("screens/stats/MemberStatisticsScreen.kt")
        val reportSource = source("screens/report/WeeklyReportScreen.kt")

        assertTrue(statsSource.contains("FhFamilyMetricsCard("))
        assertTrue(reportSource.contains("FhFamilyMetricsCard("))
        assertTrue(statsSource.contains("FhFamilyMetrics("))
        assertTrue(reportSource.contains("FhFamilyMetrics("))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/familyhub/ui/$relativePath")
            .readText(Charsets.UTF_8)
}
