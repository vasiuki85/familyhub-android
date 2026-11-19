package com.familyhub.ui.screens.tasks

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditorDialogLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createAction_isDisplayedImmediately() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorDialogHarness()
            }
        }

        composeRule.assertFooterActionsDisplayedInViewport()
    }

    @Test
    fun invalidCreateClick_showsValidationError() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorDialogHarness()
            }
        }

        composeRule.assertFooterActionsDisplayedInViewport()
        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.validation_title_empty),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun validTitleInput_submitsOnce() {
        var submitCount = 0
        var submittedTitle: String? = null

        composeRule.setContent {
            MaterialTheme {
                TaskEditorDialogHarness(
                    onConfirm = { data ->
                        submitCount += 1
                        submittedTitle = data.title
                    },
                )
            }
        }

        composeRule.assertFooterActionsDisplayedInViewport()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_title),
        ).performTextInput("Новая задача")

        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(1, submitCount)
            assertEquals("Новая задача", submittedTitle)
        }
    }

    @Test
    fun membersLoadedAfterFirstFrame_selectsAssigneeAndSubmits() {
        var members by mutableStateOf(emptyList<FamilyMember>())
        var submittedAssignee: String? = null

        composeRule.setContent {
            MaterialTheme {
                TaskEditorDialogHarness(
                    members = members,
                    onConfirm = { data ->
                        submittedAssignee = data.assignedToUserId
                    },
                )
            }
        }

        composeRule.assertFooterActionsDisplayedInViewport()

        composeRule.runOnIdle {
            members = listOf(
                FamilyMember(
                    id = "admin",
                    displayName = "Admin",
                    roleLabel = "Administrator",
                    avatarColor = "#315F93",
                    role = MemberRole.ADMIN,
                ),
            )
        }

        composeRule.waitForIdle()
        composeRule.assertFooterActionsDisplayedInViewport()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_title),
        ).performTextInput("Create after loading")
        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals("admin", submittedAssignee)
        }
    }
}

private fun AndroidComposeTestRule<*, *>.assertFooterActionsDisplayedInViewport() {
    waitForIdle()
    onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG)
        .assertIsDisplayed()
        .assertIsEnabled()
    onNodeWithTag(TASK_EDITOR_CANCEL_TEST_TAG)
        .assertIsDisplayed()
        .assertIsEnabled()

    val footerSemantics = onNodeWithTag(TASK_EDITOR_FOOTER_TEST_TAG)
        .assertIsDisplayed()
        .fetchSemanticsNode()
    var composeRoot = footerSemantics
    while (composeRoot.parent != null) {
        composeRoot = composeRoot.parent!!
    }
    val rootBounds = composeRoot.boundsInRoot
    val footerBounds = footerSemantics.boundsInRoot
    assertTrue(
        "Footer top ${footerBounds.top} is above compose root top ${rootBounds.top}",
        footerBounds.top >= rootBounds.top,
    )
    assertTrue(
        "Footer bottom ${footerBounds.bottom} is below compose root bottom ${rootBounds.bottom}",
        footerBounds.bottom <= rootBounds.bottom,
    )
}

@androidx.compose.runtime.Composable
internal fun TaskEditorDialogHarness(
    members: List<FamilyMember> = listOf(
        FamilyMember(
            id = "admin",
            displayName = "Иван",
            roleLabel = "Администратор",
            avatarColor = "#315F93",
            role = MemberRole.ADMIN,
        ),
    ),
    onConfirm: (TaskEditorSaveData) -> Unit = { _ -> },
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TaskEditorDialog(
                members = members,
                templates = emptyList(),
                categories = emptyList(),
                availableCategoryColors = emptyList(),
                currentUserId = "admin",
                currentUserRole = MemberRole.ADMIN,
                onDismiss = {},
                onConfirm = onConfirm,
                onSaveTemplate = { _, _, _, _, _, _, _, _ -> },
                onDeleteTemplate = {},
                onCreateCategory = { _, _ -> "category" },
            )
        }
    }
}
