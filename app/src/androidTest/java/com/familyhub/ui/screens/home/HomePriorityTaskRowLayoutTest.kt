package com.familyhub.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RepeatRule
import com.familyhub.domain.model.Task
import com.familyhub.domain.model.TaskStatus
import com.familyhub.ui.theme.FamilyHubTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePriorityTaskRowLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun priorityTaskRow_meetsCompactHeightBudgetAtLargeFontScale() {
        var rowHeightPx = 0
        val member = FamilyMember(
            id = "child",
            displayName = "Sam",
            roleLabel = "",
            avatarColor = "#000000",
            role = MemberRole.CHILD,
        )
        val task = Task(
            id = "task-1",
            title = "Take out recycling and sort bottles for the whole family",
            description = null,
            createdByUserId = "parent",
            assignedToUserId = "child",
            dueDate = null,
            repeatRule = RepeatRule.NONE,
            status = TaskStatus.TODO,
            createdAt = 1L,
        )

        composeRule.setContent {
            LargeFontScale {
                FamilyHubTheme {
                    Box(modifier = Modifier.width(PHONE_WIDTH_DP.dp)) {
                        PriorityTaskRow(
                            task = task,
                            assignedMember = member,
                            onToggle = {},
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                rowHeightPx = coordinates.size.height
                            },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val heightDp = rowHeightPx / composeRule.density.density
        assertTrue(
            "Priority task row height ${heightDp}dp exceeds budget ${PRIORITY_TASK_ROW_MAX_HEIGHT_DP}dp",
            heightDp <= PRIORITY_TASK_ROW_MAX_HEIGHT_DP,
        )
        assertTrue(
            "Priority task row height ${heightDp}dp is below 48dp touch target",
            heightDp >= 48f,
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

    companion object {
        private const val PHONE_WIDTH_DP = 360f
        private const val PRIORITY_TASK_ROW_MAX_HEIGHT_DP = 92f
    }
}
