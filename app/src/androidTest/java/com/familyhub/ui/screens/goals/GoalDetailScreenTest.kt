package com.familyhub.ui.screens.goals

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingGoalCard_navigatesToDetailAndBack() {
        val item = sampleGoalItem()
        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "list",
                ) {
                    composable("list") {
                        GoalListCard(
                            item = item,
                            canManageGoal = true,
                            canParticipate = true,
                            onOpen = { navController.navigate("detail/${item.goal.id}") },
                            onEdit = {},
                            onAddProgress = {},
                            onComplete = {},
                            onReopen = {},
                            onArchive = {},
                            onDelete = {},
                        )
                    }
                    composable("detail/{goalId}") {
                        Text(
                            text = "detail",
                            modifier = Modifier.testTag(GOAL_DETAIL_SCREEN_TEST_TAG),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(goalListCardTestTag(item.goal.id)).performClick()
        composeRule.onNodeWithTag(GOAL_DETAIL_SCREEN_TEST_TAG).assertExists()
    }

    @Test
    fun missingGoalId_showsNotFoundWithoutCrashing() {
        composeRule.setContent {
            MaterialTheme {
                GoalDetailNotFoundBody()
            }
        }

        composeRule.onNodeWithTag("goal_detail_not_found").assertExists()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.goals_detail_not_found),
        ).assertExists()
    }

    @Test
    fun stageHeader_expandsAndAnnouncesState() {
        val stage = sampleStage()
        val expandCd = composeRule.activity.getString(R.string.goals_stage_expand_cd, stage.title)
        val collapseCd = composeRule.activity.getString(R.string.goals_stage_collapse_cd, stage.title)

        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                CollapsibleGoalStageSection(
                    index = 0,
                    stage = stage,
                    isExpanded = expanded,
                    onToggleExpanded = { expanded = it },
                    isEditable = true,
                    onToggleStep = { _, _ -> },
                )
            }
        }

        composeRule.onNode(hasStateDescription(expandCd))
            .performClick()

        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription(collapseCd))
            .assertExists()
    }

    private fun hasStateDescription(description: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

    private fun sampleGoalItem(): FamilyGoalListItem {
        val goalId = "goal-nav"
        return FamilyGoalListItem(
            goal = FamilyGoal(
                id = goalId,
                title = "Board game",
                description = null,
                targetPoints = 0,
                currentPoints = 0,
                deadlineAt = null,
                status = FamilyGoalStatus.ACTIVE,
                createdByUserId = "user-1",
                createdAt = 1L,
                updatedAt = 1L,
                isPersonal = false,
            ),
            stages = emptyList(),
            creatorName = "Ivan",
            deadlineLabel = null,
            progressPercent = 0f,
            completedSteps = 0,
            totalSteps = 2,
            remainingSteps = 2,
            nextStepTitle = "Pick a game",
        )
    }

    private fun sampleStage(): FamilyGoalStage {
        val goalId = "goal-stage"
        val stageId = "stage-1"
        return FamilyGoalStage(
            id = stageId,
            goalId = goalId,
            title = "Choose a game",
            description = null,
            position = 0,
            steps = listOf(
                FamilyGoalStep(
                    id = "step-1",
                    goalId = goalId,
                    stageId = stageId,
                    title = "Research options",
                    isCompleted = false,
                    position = 0,
                ),
            ),
        )
    }
}
