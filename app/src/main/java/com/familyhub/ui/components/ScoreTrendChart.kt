package com.familyhub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.screens.rating.ScorePoint
import com.familyhub.ui.screens.rating.ScoreTrendChartMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScoreTrendChart(
    points: List<ScorePoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val range = remember(points) { ScoreTrendChartMath.chartRange(points) }
    val labelIndices = remember(points) { ScoreTrendChartMath.labelIndices(points.size) }
    val formatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val chartDescription = stringResource(
        R.string.rating_trend_chart_content_description,
        range.min,
        range.max,
        points.size,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = chartDescription },
        ) {
            if (points.isEmpty()) return@Canvas

            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            val canvasHeight = size.height
            val path = Path()

            ScoreTrendChartMath.zeroLineRatio(range)?.let { zeroRatio ->
                val zeroY = canvasHeight - (zeroRatio * canvasHeight)
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.35f),
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = with(density) { 1.dp.toPx() },
                )
            }

            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else stepX * index
                val yRatio = ScoreTrendChartMath.scoreToRatio(point.score, range)
                val y = canvasHeight - (yRatio * canvasHeight)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = with(density) { 2.dp.toPx() }),
            )

            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else stepX * index
                val yRatio = ScoreTrendChartMath.scoreToRatio(point.score, range)
                val y = canvasHeight - (yRatio * canvasHeight)
                drawCircle(
                    color = primaryColor,
                    radius = with(density) { 6.dp.toPx() },
                    center = Offset(x, y),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labelIndices.forEach { index ->
                val point = points[index]
                Text(
                    text = formatter.format(Date(point.weekStart)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
