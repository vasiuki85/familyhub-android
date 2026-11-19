package com.familyhub.ui.screens.goals

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.FamilyGoal
import com.familyhub.domain.model.FamilyGoalStage
import com.familyhub.domain.model.FamilyGoalStatus
import com.familyhub.domain.model.FamilyGoalStep
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalListCardLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactGoalCard_meetsHeightBudgetAtPhoneWidthWithLargeFontScale() {
        val item = sampleGoalItem(
            title = "Купить настольную игру для всей семьи на длинном названии",
            nextStep = "выбрать игру с подходящим возрастом и тематикой",
        )
        var cardHeightPx = 0

        composeRule.setContent {
            LargeFontScale {
                Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                    GoalListCard(
                        item = item,
                        canManageGoal = true,
                        canParticipate = true,
                        onOpen = {},
                        onEdit = {},
                        onAddProgress = {},
                        onComplete = {},
                        onReopen = {},
                        onArchive = {},
                        onDelete = {},
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            cardHeightPx = coordinates.size.height
                        },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = cardHeightPx / composeRule.density.density
        assertTrue(
            "Goal card height ${heightDp}dp exceeds budget ${GOAL_LIST_CARD_MAX_HEIGHT_DP}dp",
            heightDp <= GOAL_LIST_CARD_MAX_HEIGHT_DP,
        )
    }

    @Composable
    private fun LargeFontScale(content: @Composable () -> Unit) {
        val density = Density(
            density = LocalDensity.current.density,
            fontScale = 1.5f,
        )
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides density,
            content = content,
        )
    }

    private fun sampleGoalItem(
        title: String,
        nextStep: String,
    ): FamilyGoalListItem {
        val goalId = "goal-1"
        val stageId = "stage-1"
        return FamilyGoalListItem(
            goal = FamilyGoal(
                id = goalId,
                title = title,
                description = null,
                targetPoints = 0,
                currentPoints = 0,
                deadlineAt = 1_725_000_000_000L,
                status = FamilyGoalStatus.ACTIVE,
                createdByUserId = "user-1",
                createdAt = 1L,
                updatedAt = 1L,
                isPersonal = false,
            ),
            stages = listOf(
                FamilyGoalStage(
                    id = stageId,
                    goalId = goalId,
                    title = "Выбираем игру",
                    description = null,
                    position = 0,
                    steps = listOf(
                        FamilyGoalStep(
                            id = "step-1",
                            goalId = goalId,
                            stageId = stageId,
                            title = nextStep,
                            isCompleted = false,
                            position = 0,
                        ),
                    ),
                ),
            ),
            creatorName = "Ivan",
            deadlineLabel = "7 August",
            progressPercent = 0f,
            completedSteps = 0,
            totalSteps = 5,
            remainingSteps = 5,
            nextStepTitle = nextStep,
        )
    }

    private companion object {
        const val PHONE_WIDTH_DP = 411
    }
}
