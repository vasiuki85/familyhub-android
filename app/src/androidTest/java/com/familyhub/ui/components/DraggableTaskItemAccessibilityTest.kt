package com.familyhub.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraggableTaskItemAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dragHandle_exposesReorderCustomActions() {
        val moveUpLabel = "Move up"
        val moveDownLabel = "Move down"
        val dragHandleLabel = "Reorder task"
        val taskTitle = "Buy groceries"

        composeRule.setContent {
            MaterialTheme {
                DraggableTaskItem(
                    enabled = true,
                    moveUpLabel = moveUpLabel,
                    moveDownLabel = moveDownLabel,
                    onMoveUp = {},
                    onMoveDown = {},
                    dragHandleContentDescription = dragHandleLabel,
                    content = { _ ->
                        Text(
                            text = taskTitle,
                            modifier = Modifier.testTag(TASK_CONTENT_TAG),
                        )
                    },
                )
            }
        }

        val dragHandleSemantics = composeRule
            .onNodeWithContentDescription(dragHandleLabel)
            .fetchSemanticsNode()
        val customActions = dragHandleSemantics.config.getOrNull(SemanticsActions.CustomActions)
        assertNotNull(customActions)
        assertEquals(2, customActions!!.size)
        assertEquals(moveUpLabel, customActions[0].label)
        assertEquals(moveDownLabel, customActions[1].label)

        val contentSemantics = composeRule
            .onNodeWithTag(TASK_CONTENT_TAG)
            .fetchSemanticsNode()
        assertNull(contentSemantics.config.getOrNull(SemanticsActions.CustomActions))
    }

    private companion object {
        const val TASK_CONTENT_TAG = "draggable_task_content"
    }
}
