package com.familyhub.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import com.familyhub.ui.components.FhCheckboxRow
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhProgressBar
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme

const val GOAL_DETAIL_SCREEN_TEST_TAG = "goal_detail_screen"
const val GOAL_STAGE_HEADER_TEST_TAG_PREFIX = "goal_stage_header_"

@Composable
fun GoalDetailScreen(
    navController: NavController,
    goalId: String?,
    viewModel: FamilyGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goalItem = goalId?.let(state::findGoal)

    FhScreenScaffold(
        title = goalItem?.goal?.title ?: stringResource(R.string.nav_label_goals),
        collapsingTopBar = false,
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = FhIcons.Back,
                    contentDescription = stringResource(R.string.goals_detail_back_cd),
                )
            }
        },
        modifier = Modifier.testTag(GOAL_DETAIL_SCREEN_TEST_TAG),
    ) { paddingValues ->
        when {
            state.isLoading -> {
                FhListSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
            goalId.isNullOrBlank() || goalItem == null -> {
                GoalDetailNotFoundBody(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
            else -> {
                GoalDetailContent(
                    item = goalItem,
                    canParticipate = state.canCreateGoals,
                    onToggleStep = viewModel::toggleStep,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun GoalDetailContent(
    item: FamilyGoalListItem,
    canParticipate: Boolean,
    onToggleStep: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val goalColors = FhTheme.modules.goals
    val sortedStages = item.stages.sortedBy(FamilyGoalStage::position)
    val stageIds = sortedStages.map { it.id }
    val expansionState = rememberSaveable(
        stageIds,
        saver = mapSaver(
            save = { map -> map.toMap() },
            restore = { restored ->
                mutableStateMapOf<String, Boolean>().apply {
                    restored.forEach { (key, value) ->
                        put(key as String, value as Boolean)
                    }
                }
            },
        ),
    ) {
        mutableStateMapOf<String, Boolean>()
    }

    LaunchedEffect(sortedStages) {
        val currentStageId = findCurrentStageId(sortedStages)
        sortedStages.forEach { stage ->
            if (!expansionState.containsKey(stage.id)) {
                expansionState[stage.id] = stage.id == currentStageId
            }
        }
        sortedStages.filter { it.isCompleted }.forEach { stage ->
            expansionState[stage.id] = false
        }
        currentStageId?.let { expansionState[it] = true }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = FhLayout.listBottomPadding(hasFloatingActionButton = false),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                color = goalColors.container,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.goals_progress_format,
                            item.completedSteps,
                            item.totalSteps,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = goalColors.onContainer,
                    )
                    if (item.totalSteps > 0) {
                        FhProgressBar(
                            progress = item.progressPercent,
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                            color = goalColors.accent,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                    item.deadlineLabel?.let { deadline ->
                        Text(
                            text = stringResource(R.string.goals_deadline_label, deadline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = goalColors.onContainer,
                        )
                    }
                    Text(
                        text = stringResource(R.string.goals_creator_label, item.creatorName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = goalColors.onContainer,
                    )
                    item.goal.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = goalColors.onContainer,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = sortedStages,
            key = { _, stage -> stage.id },
        ) { index, stage ->
            CollapsibleGoalStageSection(
                index = index,
                stage = stage,
                isExpanded = expansionState[stage.id] == true,
                onToggleExpanded = { expanded ->
                    expansionState[stage.id] = expanded
                },
                isEditable = canParticipate && item.goal.status == FamilyGoalStatus.ACTIVE,
                onToggleStep = { step, checked ->
                    onToggleStep(item.goal.id, step.id, checked)
                },
            )
            if (index < sortedStages.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun GoalDetailNotFoundBody(modifier: Modifier = Modifier) {
    FhEmptyState(
        icon = FhIcons.Flag,
        title = stringResource(R.string.goals_detail_not_found),
        modifier = modifier.testTag("goal_detail_not_found"),
    )
}

@Composable
internal fun CollapsibleGoalStageSection(
    index: Int,
    stage: FamilyGoalStage,
    isExpanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    isEditable: Boolean,
    onToggleStep: (FamilyGoalStep, Boolean) -> Unit,
) {
    val expandLabel = stringResource(R.string.goals_stage_expand_cd, stage.title)
    val collapseLabel = stringResource(R.string.goals_stage_collapse_cd, stage.title)
    val headerText = stringResource(
        R.string.goals_stage_header_summary,
        index + 1,
        stage.title,
        stage.completedSteps,
        stage.totalSteps,
    )

    Column(modifier = Modifier.fillMaxWidth().testTag("$GOAL_STAGE_HEADER_TEST_TAG_PREFIX$stage.id")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded(!isExpanded) }
                .semantics {
                    role = Role.Button
                    contentDescription = headerText
                    stateDescription = if (isExpanded) collapseLabel else expandLabel
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isExpanded) FhIcons.ExpandLess else FhIcons.ExpandMore,
                contentDescription = null,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!stage.description.isNullOrBlank()) {
                        Text(
                            text = stage.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    stage.steps.sortedBy(FamilyGoalStep::position).forEach { step ->
                        FhCheckboxRow(
                            checked = step.isCompleted,
                            onCheckedChange = { checked -> onToggleStep(step, checked) },
                            label = step.title,
                            enabled = isEditable,
                        )
                    }
                }
            }
        }
    }
}

private fun findCurrentStageId(stages: List<FamilyGoalStage>): String? =
    stages.firstOrNull { !it.isCompleted }?.id ?: stages.lastOrNull()?.id

private val FamilyGoalStage.isCompleted: Boolean
    get() = totalSteps > 0 && completedSteps >= totalSteps
