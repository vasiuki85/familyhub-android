@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.familyhub.ui.screens.report

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.WeeklyFamilyReport
import com.familyhub.domain.model.WeeklyAchievement
import com.familyhub.domain.model.WeeklyRewardRedemption
import com.familyhub.domain.utils.displayLabel
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhFamilyMetrics
import com.familyhub.ui.components.FhFamilyMetricsCard
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.InfoMessage
import com.familyhub.ui.theme.components.FhCardTone
import com.familyhub.ui.theme.components.ThemedCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeeklyReportScreen(viewModel: WeeklyReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    WeeklyReportContent(
        state = state,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onRetry = viewModel::refresh,
        onDismissError = viewModel::clearError
    )
}

@Composable
private fun WeeklyReportContent(
    state: WeeklyReportUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    val dateFormatter = remember {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    }
    val weekSubtitle = state.report?.let { report ->
        stringResource(
            id = R.string.weekly_report_week_range,
            dateFormatter.format(Date(report.weekStart)),
            dateFormatter.format(Date(report.weekEnd))
        )
    }

    FhScreenScaffold(
        title = stringResource(R.string.weekly_report_title),
        subtitle = weekSubtitle,
        navigationIcon = {
            IconButton(onClick = onPreviousWeek) {
                Icon(
                    imageVector = FhIcons.Back,
                    contentDescription = stringResource(R.string.weekly_report_prev_week)
                )
            }
        },
        actions = {
            IconButton(onClick = onNextWeek, enabled = state.canNavigateForward) {
                Icon(
                    imageVector = FhIcons.Forward,
                    contentDescription = stringResource(R.string.weekly_report_next_week)
                )
            }
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = FhIcons.Refresh,
                    contentDescription = stringResource(R.string.weekly_report_refresh)
                )
            }
        },
        errorMessage = state.errorMessage,
        onErrorDismiss = onDismissError,
        isRefreshing = state.isLoading,
        onRefresh = onRetry,
    ) { paddingValues ->
        when {
            state.isLoading -> {
                FhListSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            state.report != null -> {
                WeeklyReportBody(
                    report = state.report,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                FhEmptyState(
                    icon = FhIcons.Refresh,
                    title = stringResource(R.string.weekly_report_unavailable_title),
                    description = stringResource(R.string.weekly_report_unavailable_body),
                    actionLabel = stringResource(R.string.weekly_report_refresh),
                    onAction = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun WeeklyReportBody(
    report: WeeklyFamilyReport,
    modifier: Modifier = Modifier
) {
    val dateTimeFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    }

    LazyColumn(
        modifier = modifier
            .padding(
                horizontal = WeeklyReportLayoutTokens.ScreenHorizontalPadding,
                vertical = WeeklyReportLayoutTokens.ScreenVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(WeeklyReportLayoutTokens.CardSpacing)
    ) {
        item {
            WeeklySummaryCard(report = report)
        }

        item {
            WeeklyTopPerformersCard(report = report)
        }

        item {
            WeeklyMembersCard(report = report)
        }

        item {
            WeeklyHighlightsCard(
                achievements = report.achievements,
                rewards = report.rewardRedemptions,
                dateTimeFormatter = dateTimeFormatter,
            )
        }

        if (report.totalTasksCompleted == 0 &&
            report.achievements.isEmpty() &&
            report.rewardRedemptions.isEmpty()
        ) {
            item {
                InfoMessage(
                    message = stringResource(R.string.weekly_report_no_activity),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WeeklySummaryCard(report: WeeklyFamilyReport) {
    FhFamilyMetricsCard(
        metrics = FhFamilyMetrics(
            tasksCompleted = report.totalTasksCompleted,
            pointsEarned = report.totalPointsEarned,
            pointsDeducted = report.totalPointsDeducted,
            balance = report.totalPointsEarned - report.totalPointsDeducted,
        ),
    )
}

@Composable
private fun WeeklyTopPerformersCard(report: WeeklyFamilyReport) {
    val maxTasks = report.memberSummaries.maxOfOrNull { it.tasksCompleted } ?: 0
    val maxPoints = report.memberSummaries.maxOfOrNull { it.pointsEarned } ?: 0
    val topTaskMembers = if (maxTasks > 0) {
        report.memberSummaries.filter { it.tasksCompleted == maxTasks }
    } else emptyList()
    val topPointMembers = if (maxPoints > 0) {
        report.memberSummaries.filter { it.pointsEarned == maxPoints }
    } else emptyList()

    ThemedCard(tone = FhCardTone.REWARD) {
        Column(verticalArrangement = Arrangement.spacedBy(WeeklyReportLayoutTokens.ContentSpacing)) {
            Text(
                text = stringResource(R.string.weekly_report_top_performers_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (topTaskMembers.isNotEmpty()) {
                val names = topTaskMembers.joinToString { it.member.displayLabel() }
                Text(
                    text = stringResource(
                        R.string.weekly_report_top_tasks,
                        names,
                        maxTasks
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.weekly_report_top_tasks_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (topPointMembers.isNotEmpty()) {
                val names = topPointMembers.joinToString { it.member.displayLabel() }
                Text(
                    text = stringResource(
                        R.string.weekly_report_top_points,
                        names,
                        maxPoints
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.weekly_report_top_points_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun WeeklyMembersCard(report: WeeklyFamilyReport) {
    ThemedCard {
        Column(verticalArrangement = Arrangement.spacedBy(WeeklyReportLayoutTokens.ContentSpacing)) {
            Text(
                text = stringResource(R.string.weekly_report_members_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            report.memberSummaries
                .sortedByDescending { it.pointsEarned }
                .forEachIndexed { index, summary ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WeeklyReportLayoutTokens.ContentSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summary.member.displayLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(
                                    R.string.weekly_report_member_tasks,
                                    summary.tasksCompleted
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = stringResource(
                                    R.string.weekly_report_member_points,
                                    summary.pointsEarned
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (summary.pointsDeducted > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.weekly_report_member_points_deducted,
                                        summary.pointsDeducted
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (index < report.memberSummaries.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                vertical = WeeklyReportLayoutTokens.ItemDividerPadding
                            )
                        )
                    }
                }
        }
    }
}

@Composable
private fun WeeklyHighlightsCard(
    achievements: List<WeeklyAchievement>,
    rewards: List<WeeklyRewardRedemption>,
    dateTimeFormatter: DateFormat,
) {
    ThemedCard(tone = FhCardTone.REWARD) {
        Column(verticalArrangement = Arrangement.spacedBy(WeeklyReportLayoutTokens.ContentSpacing)) {
            Text(
                text = stringResource(R.string.weekly_report_achievements_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (achievements.isEmpty()) {
                Text(
                    text = stringResource(R.string.weekly_report_achievements_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                achievements.forEachIndexed { index, achievement ->
                    Text(
                        text = stringResource(
                            R.string.weekly_report_achievement_item,
                            achievement.member.displayLabel(),
                            stringResource(achievement.type.titleRes),
                            dateTimeFormatter.format(Date(achievement.earnedAt))
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (index < achievements.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                vertical = WeeklyReportLayoutTokens.ItemDividerPadding
                            )
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = WeeklyReportLayoutTokens.SectionDividerPadding)
            )
            Text(
                text = stringResource(R.string.weekly_report_rewards_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (rewards.isEmpty()) {
                Text(
                    text = stringResource(R.string.weekly_report_rewards_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                rewards.forEachIndexed { index, reward ->
                    Text(
                        text = stringResource(
                            R.string.weekly_report_reward_item,
                            reward.member.displayLabel(),
                            reward.rewardTitle,
                            reward.rewardCost,
                            dateTimeFormatter.format(Date(reward.processedAt))
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (index < rewards.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                vertical = WeeklyReportLayoutTokens.ItemDividerPadding
                            )
                        )
                    }
                }
            }
        }
    }
}

internal object WeeklyReportLayoutTokens {
    val ScreenHorizontalPadding = 16.dp
    val ScreenVerticalPadding = 8.dp
    val CardSpacing = 10.dp
    val ContentSpacing = 6.dp
    val ItemDividerPadding = 2.dp
    val SectionDividerPadding = 4.dp
}
