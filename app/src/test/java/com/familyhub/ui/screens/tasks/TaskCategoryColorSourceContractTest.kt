package com.familyhub.ui.screens.tasks

import com.familyhub.ui.components.TASK_CATEGORY_STRIPE_TEST_TAG
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCategoryColorSourceContractTest {

    private val filterBarSource: String by lazy {
        File("src/main/java/com/familyhub/ui/screens/tasks/TaskFilterAndSortBar.kt")
            .readText(Charsets.UTF_8)
    }

    private val taskCardSource: String by lazy {
        File("src/main/java/com/familyhub/ui/components/TaskItemCard.kt")
            .readText(Charsets.UTF_8)
    }

    private val tasksScreenSource: String by lazy {
        File("src/main/java/com/familyhub/ui/screens/tasks/TasksScreen.kt")
            .readText(Charsets.UTF_8)
    }

    @Test
    fun compactCategoryChip_showsColorDotWhenSelected() {
        assertTrue(filterBarSource.contains("selectedCategoryColor"))
        assertTrue(filterBarSource.contains(TASK_CATEGORY_COLOR_DOT_TEST_TAG))
        assertTrue(filterBarSource.contains("TaskCategoryColorDot("))
    }

    @Test
    fun categorySheet_listsColorDots() {
        assertTrue(filterBarSource.contains("TaskCategoryColorDot(color = category.color)"))
    }

    @Test
    fun taskItemCard_exposesCategoryStripe() {
        assertTrue(taskCardSource.contains("categoryColor: Color?"))
        assertTrue(taskCardSource.contains(TASK_CATEGORY_STRIPE_TEST_TAG))
    }

    @Test
    fun tasksScreen_passesCategoryColorToCard() {
        assertTrue(tasksScreenSource.contains("categoryColor = categoryColor"))
        assertTrue(tasksScreenSource.contains("state.taskCategories.firstOrNull"))
    }
}
