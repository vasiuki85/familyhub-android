package com.familyhub.ui.screens.tasks

import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.R
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEditorDialogSmokeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureEditorFooterVisibilityStates() {
        composeRule.setContent {
            MaterialTheme {
                TaskEditorDialogHarness()
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG).assertIsDisplayed()
        capture("01_open")

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.tasks_editor_additional),
        ).performScrollTo()
        composeRule.waitForIdle()
        capture("02_scrolled")

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_title),
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.task_title),
        ).performTextInput("Smoke")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TASK_EDITOR_CREATE_TEST_TAG).assertIsDisplayed()
        capture("03_keyboard")
    }

    private fun capture(name: String) {
        val bitmap: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap) { "Screenshot capture failed for $name" }
        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "familyhub-task-editor-smoke",
        )
        outputDir.mkdirs()
        FileOutputStream(File(outputDir, "$name.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
