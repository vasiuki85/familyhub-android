package com.familyhub.ui.screens.tasks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksResponsiveLayoutContractTest {

    @Test
    fun taskTabsAndFilters_areNarrowScreenSafe() {
        val tasksSource = source("TasksScreen.kt")
        val pillTabSource = File("src/main/java/com/familyhub/ui/components/FhPillTabRow.kt")
            .readText(Charsets.UTF_8)
        val filtersSource = source("TaskFilterAndSortBar.kt")

        assertTrue(tasksSource.contains("FhPillTabRow("))
        assertTrue(pillTabSource.contains("maxLines = 1"))
        assertTrue(tasksSource.contains("TaskContentUiStateMapper.mapFromState(state, effectiveTab)"))
        assertTrue(filtersSource.contains("FhScrollableHorizontalRow("))
        assertTrue(filtersSource.contains("TaskFilterCompactBar"))
    }

    @Test
    fun taskList_reservesSpaceForVisibleFab() {
        val source = source("TasksScreen.kt")

        assertTrue(source.contains("val showCreateFab ="))
        assertTrue(source.contains("bottom = FhLayout.listBottomPadding(showCreateFab)"))
    }

    @Test
    fun editorSaveAction_isASiblingOfScrollableContent_andSubmits() {
        val source = source("TaskEditorDialog.kt")
        val editor = source.substring(
            startIndex = source.indexOf("BackHandler(onBack = onDismiss)"),
            endIndex = source.indexOf("if (showCategorySheet"),
        )

        assertFalse(editor.contains("androidx.compose.ui.window.Dialog"))
        assertTrue(editor.contains("BackHandler(onBack = onDismiss)"))
        assertTrue(editor.contains(".fillMaxSize()\n                    .imePadding()"))
        assertTrue(editor.contains(".weight(1f)"))
        assertTrue(editor.contains("Surface(tonalElevation = 3.dp)"))
        assertTrue(editor.contains(".navigationBarsPadding()"))
        assertTrue(editor.contains("onClick = submitTask"))
        assertTrue(editor.contains("heightIn(min = 48.dp)"))
        assertFalse(editor.contains("enabled = title.isNotBlank() && selectedMemberId.isNotBlank()"))
        assertTrue(editor.contains("padding(bottom = 16.dp)"))
        assertTrue(editor.contains("TASK_EDITOR_FOOTER_TEST_TAG"))
        assertTrue(source("TasksScreen.kt").contains(".zIndex(1f)"))
        assertTrue(source.contains("var submitLocked"))
        assertTrue(source.contains("val submitTask = {"))
        assertTrue(source.contains("onConfirm("))
        assertTrue(source.contains("LaunchedEffect(\n        task?.id,\n        members,"))
        assertTrue(source.contains("selectedMemberId in memberIds"))
    }

    private fun source(fileName: String): String =
        File("src/main/java/com/familyhub/ui/screens/tasks/$fileName")
            .readText(Charsets.UTF_8)
}
