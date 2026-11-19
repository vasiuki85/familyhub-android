@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.familyhub.ui.screens.stats

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.MemberStatistics
import com.familyhub.domain.model.MemberTaskTypeBreakdown
import com.familyhub.domain.model.MemberWeekScore
import com.familyhub.domain.utils.MemberPointsStatistics
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhFamilyMetrics
import com.familyhub.ui.components.FhFamilyMetricsCard
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.ScoreTrendChart
import com.familyhub.ui.screens.rating.ScorePoint
import com.familyhub.ui.screens.rating.ScoreTrendChartMath
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemberStatisticsScreen(viewModel: MemberStatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    MemberStatisticsContent(
        state = state,
        onSelectMember = viewModel::selectMember,
        onRefresh = viewModel::refresh,
        onDismissError = viewModel::clearError
    )
}

@Composable
private fun MemberStatisticsContent(
    state: MemberStatisticsUiState,
    onSelectMember: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit
) {
    FhScreenScaffold(
        title = stringResource(R.string.member_stats_title),
        errorMessage = state.errorMessage,
        onErrorDismiss = onDismissError,
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = FhIcons.Refresh,
                    contentDescription = stringResource(R.string.member_stats_refresh)
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                FhListSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            state.members.isEmpty() -> {
                FhEmptyState(
                    icon = FhIcons.Groups,
                    title = stringResource(R.string.member_stats_empty),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                val selected = state.members.firstOrNull { it.member.id == state.selectedMemberId }
                if (selected != null) {
                    MemberStatisticsBody(
                        members = state.members,
                        selectedMember = selected,
                        onSelectMember = onSelectMember,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                } else {
                    FhEmptyState(
                        icon = FhIcons.Groups,
                        title = stringResource(R.string.member_stats_select_prompt),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberStatisticsBody(
    members: List<MemberStatistics>,
    selectedMember: MemberStatistics,
    onSelectMember: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    }
    val numberFormatter = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }
    }

    LazyColumn(
        modifier = modifier
            .padding(
                horizontal = MemberStatisticsLayoutTokens.ScreenHorizontalPadding,
                vertical = MemberStatisticsLayoutTokens.ScreenVerticalPadding,
            ),
        contentPadding = PaddingValues(
            bottom = MemberStatisticsLayoutTokens.listBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.CardSpacing)
    ) {
        item {
            MemberSelectionRow(
                members = members,
                selectedMemberId = selectedMember.member.id,
                onSelectMember = onSelectMember
            )
        }

        item {
            FhFamilyMetricsCard(
                metrics = FhFamilyMetrics(
                    tasksCompleted = selectedMember.totalTasksCompleted,
                    pointsEarned = selectedMember.totalPointsEarned,
                    pointsDeducted = selectedMember.totalPenaltiesAmount,
                    balance = selectedMember.walletBalance,
                ),
            )
        }

        item {
            MemberPerformanceCard(
                member = selectedMember,
                numberFormatter = numberFormatter,
                dateFormatter = dateFormatter
            )
        }

        item {
            MemberWeeklyTrendCard(
                scores = selectedMember.weeklyScores,
            )
        }

        item {
            MemberTaskBreakdownCard(breakdown = selectedMember.taskTypeBreakdown)
        }

        item {
            MemberRecognitionCard(member = selectedMember)
        }
    }
}

@Composable
private fun MemberSelectionRow(
    members: List<MemberStatistics>,
    selectedMemberId: String,
    onSelectMember: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.SelectorSpacing)
    ) {
        Text(
            text = stringResource(R.string.member_stats_member_selector_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.SelectorChipSpacing)) {
            items(members, key = { it.member.id }) { member ->
                val isSelected = member.member.id == selectedMemberId
                FilterChip(
                    modifier = Modifier.animateItem(),
                    selected = isSelected,
                    onClick = { onSelectMember(member.member.id) },
                    label = { Text(text = member.member.displayLabel()) }
                )
            }
        }
    }
}

@Composable
private fun MemberPerformanceCard(
    member: MemberStatistics,
    numberFormatter: NumberFormat,
    dateFormatter: DateFormat
) {
    ThemedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.ContentSpacing),
        ) {
            Text(
                text = stringResource(R.string.member_stats_performance_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val bestWeek = member.bestWeek
            if (bestWeek != null) {
                val formattedDate = dateFormatter.format(Date(bestWeek.weekStart))
                Text(
                    text = stringResource(
                        R.string.member_stats_best_week,
                        formattedDate,
                        bestWeek.points
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.member_stats_best_week_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val recentAveragePoints = MemberPointsStatistics.recentAverageWeeklyPointsEarned(member.weeklyScores)
            Text(
                text = stringResource(
                    R.string.member_stats_recent_average,
                    numberFormatter.format(recentAveragePoints)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MemberWeeklyTrendCard(
    scores: List<MemberWeekScore>,
) {
    ThemedCard(
        tone = FhCardTone.INFO,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.ContentSpacing),
        ) {
            Text(
                text = stringResource(R.string.member_stats_weekly_trend_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (scores.size < ScoreTrendChartMath.MIN_POINTS_FOR_CHART) {
                Text(
                    text = stringResource(R.string.stats_chart_not_enough_data),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val trendPoints = scores
                    .sortedBy(MemberWeekScore::weekStart)
                    .map { week ->
                        ScorePoint(
                            weekStart = week.weekStart,
                            score = week.pointsEarned - week.pointsDeducted,
                        )
                    }
                ScoreTrendChart(
                    points = trendPoints,
                    chartHeight = 120.dp,
                )
            }
        }
    }
}

@Composable
private fun MemberTaskBreakdownCard(breakdown: List<MemberTaskTypeBreakdown>) {
    ThemedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.ContentSpacing),
        ) {
            Text(
                text = stringResource(R.string.member_stats_task_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (breakdown.isEmpty()) {
                Text(
                    text = stringResource(R.string.member_stats_task_breakdown_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                breakdown.forEach { item ->
                    val typeName = stringResource(item.taskType.titleRes)
                    Text(
                        text = stringResource(
                            R.string.member_stats_task_breakdown_item,
                            typeName,
                            item.count
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRecognitionCard(member: MemberStatistics) {
    ThemedCard(
        tone = FhCardTone.REWARD,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MemberStatisticsLayoutTokens.ContentSpacing),
        ) {
            Text(
                text = stringResource(R.string.member_stats_achievements_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (member.achievements.isEmpty()) {
                Text(
                    text = stringResource(R.string.member_stats_achievements_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                member.achievements.forEach { achievement ->
                    Text(
                        text = stringResource(achievement.type.titleRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MemberStatisticsLayoutTokens.SectionDividerPadding)
            )
            Text(
                text = stringResource(R.string.member_stats_rewards_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.member_stats_rewards_counts,
                    member.rewardsSummary.approved,
                    member.rewardsSummary.pending,
                    member.rewardsSummary.declined
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

internal object MemberStatisticsLayoutTokens {
    val ScreenHorizontalPadding = 16.dp
    val ScreenVerticalPadding = 8.dp
    val CardSpacing = 10.dp
    val ContentSpacing = 6.dp
    val SelectorSpacing = 4.dp
    val SelectorChipSpacing = 6.dp
    val SectionDividerPadding = 2.dp

    fun listBottomPadding() = FhLayout.listBottomPadding(hasFloatingActionButton = false)
}
