package com.familyhub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard

data class FhFamilyMetrics(
    val tasksCompleted: Int,
    val pointsEarned: Int,
    val pointsDeducted: Int,
    val balance: Int,
)

fun fhFamilyMetricsTestTag(metric: FhFamilyMetricType): String = "fh_family_metric_${metric.name.lowercase()}"

enum class FhFamilyMetricType {
    TASKS,
    EARNED,
    DEDUCTED,
    BALANCE,
}

@Composable
fun FhFamilyMetricsCard(
    metrics: FhFamilyMetrics,
    modifier: Modifier = Modifier,
    tone: FhCardTone = FhCardTone.ACCENT,
) {
    ThemedCard(
        modifier = modifier.fillMaxWidth(),
        tone = tone,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCell(
                    label = stringResource(R.string.metrics_tasks_label),
                    value = metrics.tasksCompleted,
                    type = FhFamilyMetricType.TASKS,
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = stringResource(R.string.metrics_earned_label),
                    value = metrics.pointsEarned,
                    type = FhFamilyMetricType.EARNED,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCell(
                    label = stringResource(R.string.metrics_deducted_label),
                    value = metrics.pointsDeducted,
                    type = FhFamilyMetricType.DEDUCTED,
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = stringResource(R.string.metrics_balance_label),
                    value = metrics.balance,
                    type = FhFamilyMetricType.BALANCE,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: Int,
    type: FhFamilyMetricType,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(fhFamilyMetricsTestTag(type)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
