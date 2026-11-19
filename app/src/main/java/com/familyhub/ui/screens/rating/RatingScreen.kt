@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.familyhub.ui.screens.rating

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.ScoreRow
import com.familyhub.ui.components.ScoreTrendChart
import com.familyhub.ui.screens.rating.ScoreTrendChartMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MILLIS_IN_DAY = java.util.concurrent.TimeUnit.DAYS.toMillis(1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    viewModel: RatingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FhScreenScaffold(
        title = stringResource(R.string.nav_label_rating),
        subtitle = stringResource(R.string.rating_week_scores),
        errorMessage = state.errorMessage,
        onErrorDismiss = { viewModel.clearError() }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                FhListSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            state.items.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RatingEmptyIllustration()
                            Text(
                                text = stringResource(R.string.rating_empty),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.rating_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.items.forEach { item ->
                                ScoreRow(
                                    memberName = item.memberName,
                                    score = item.score,
                                    avatarColor = item.avatarColor,
                                    maxScore = state.maxScore
                                )
                            }
                        }
                    }

                    if (state.history.isNotEmpty()) {
                        item {
                            HistorySection(state.history)
                        }
                    }

                    item {
                        TrendSection(state = state, onSelectMember = viewModel::selectMember)
                    }

                    item {
                        AchievementsSection(state)
                    }

                    item {
                        Text(
                            text = stringResource(R.string.rating_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingEmptyIllustration() {
    Image(
        painter = painterResource(R.drawable.fh_illustration_rating_empty_v2),
        contentDescription = stringResource(R.string.rating_v2_empty_illustration_cd),
        modifier = Modifier.size(190.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun HistorySection(history: List<WeekHistoryUi>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.rating_history_title),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.weekStart }) { week ->
                HistoryCard(week, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun HistoryCard(week: WeekHistoryUi, modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val start = remember(week.weekStart) { formatter.format(Date(week.weekStart)) }
    val end = remember(week.weekStart) { formatter.format(Date(week.weekStart + 6 * MILLIS_IN_DAY)) }
    val total = remember(week.rows) { week.rows.sumOf { it.score } }
    Card(
        modifier = modifier.width(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.rating_history_item, "$start - $end", total),
                style = MaterialTheme.typography.bodyMedium
            )
            week.rows.take(3).forEach { row ->
                Text(
                    text = stringResource(
                        R.string.rating_history_member_score,
                        row.memberName,
                        row.score
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendSection(state: RatingUiState, onSelectMember: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.rating_trend_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.members.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            val selectedMember = state.members.firstOrNull { it.id == state.selectedMemberId }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedMember?.displayLabel() ?: stringResource(R.string.rating_trend_select_member),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.rating_trend_select_member)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayLabel()) },
                            onClick = {
                                expanded = false
                                onSelectMember(member.id)
                            }
                        )
                    }
                }
            }
        }

        val trendPoints = state.selectedMemberId?.let { id -> state.trend[id] } ?: emptyList()
        if (!ScoreTrendChartMath.hasEnoughPointsForChart(trendPoints.size)) {
            Text(
                text = stringResource(R.string.stats_chart_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ScoreTrendChart(points = trendPoints)
        }
    }
}

@Composable
private fun AchievementsSection(state: RatingUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.rating_achievements_title),
            style = MaterialTheme.typography.titleMedium
        )
        if (state.achievements.isEmpty()) {
            Text(
                text = stringResource(R.string.rating_achievements_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.achievements
                .sortedByDescending { it.earnedAt }
                .forEach { achievement ->
                    Text(
                        text = stringResource(achievement.type.titleRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(achievement.type.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
        }
    }
}
