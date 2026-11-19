package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FhFamilyMetricsCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun metricsCard_rendersAllFourValues() {
        val metrics = FhFamilyMetrics(
            tasksCompleted = 12,
            pointsEarned = 45,
            pointsDeducted = 5,
            balance = 40,
        )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    FhFamilyMetricsCard(metrics = metrics)
                }
            }
        }

        composeRule.onNodeWithTag(fhFamilyMetricsTestTag(FhFamilyMetricType.TASKS)).assertExists()
        composeRule.onNodeWithTag(fhFamilyMetricsTestTag(FhFamilyMetricType.EARNED)).assertExists()
        composeRule.onNodeWithTag(fhFamilyMetricsTestTag(FhFamilyMetricType.DEDUCTED)).assertExists()
        composeRule.onNodeWithTag(fhFamilyMetricsTestTag(FhFamilyMetricType.BALANCE)).assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("45").assertExists()
        composeRule.onNodeWithText("5").assertExists()
        composeRule.onNodeWithText("40").assertExists()
    }

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
