package com.familyhub.data.sync.logic

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskHistory
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.export.ExportRecipeIngredient
import com.familyhub.data.export.ExportRecipeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncMergerNormalizationTest {

    private val merger = SyncMerger()

    @Test
    fun normalizedForComparison_stabilizesBackupOnlyMenuCollections() {
        val first = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl_2", name = "B", createdAt = 2L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(id = "te_2", templateId = "tpl_2", dayOfWeek = 2, mealType = "DINNER", servings = 2),
                ExportMenuTemplateEntry(id = "te_1", templateId = "tpl_1", dayOfWeek = 1, mealType = "LUNCH", servings = 1)
            ),
            weeklyMenus = listOf(ExportWeeklyMenu(id = "menu_2", startDateMillis = 2L)),
            menuEntries = listOf(
                ExportMenuEntry(id = "me_2", menuId = "menu_2", dayOfWeek = 2, mealType = "SNACK"),
                ExportMenuEntry(id = "me_1", menuId = "menu_1", dayOfWeek = 1, mealType = "BREAKFAST")
            ),
            taskHistory = listOf(
                ExportTaskHistory(id = "hist_2", taskId = "t2", changedByUserId = "u1", changeType = "created", changeDescription = "B", timestamp = 2L),
                ExportTaskHistory(id = "hist_1", taskId = "t1", changedByUserId = "u1", changeType = "created", changeDescription = "A", timestamp = 1L)
            ),
            dailyAssignments = listOf(
                ExportDailyAssignment(id = "da_2", weekStartMillis = 2L, dayOfWeek = 2),
                ExportDailyAssignment(id = "da_1", weekStartMillis = 1L, dayOfWeek = 1)
            )
        )
        val second = ExportData(
            menuTemplates = listOf(ExportMenuTemplate(id = "tpl_2", name = "B", createdAt = 2L)),
            menuTemplateEntries = listOf(
                ExportMenuTemplateEntry(id = "te_1", templateId = "tpl_1", dayOfWeek = 1, mealType = "LUNCH", servings = 1),
                ExportMenuTemplateEntry(id = "te_2", templateId = "tpl_2", dayOfWeek = 2, mealType = "DINNER", servings = 2)
            ),
            weeklyMenus = listOf(ExportWeeklyMenu(id = "menu_2", startDateMillis = 2L)),
            menuEntries = listOf(
                ExportMenuEntry(id = "me_1", menuId = "menu_1", dayOfWeek = 1, mealType = "BREAKFAST"),
                ExportMenuEntry(id = "me_2", menuId = "menu_2", dayOfWeek = 2, mealType = "SNACK")
            ),
            taskHistory = listOf(
                ExportTaskHistory(id = "hist_1", taskId = "t1", changedByUserId = "u1", changeType = "created", changeDescription = "A", timestamp = 1L),
                ExportTaskHistory(id = "hist_2", taskId = "t2", changedByUserId = "u1", changeType = "created", changeDescription = "B", timestamp = 2L)
            ),
            dailyAssignments = listOf(
                ExportDailyAssignment(id = "da_1", weekStartMillis = 1L, dayOfWeek = 1),
                ExportDailyAssignment(id = "da_2", weekStartMillis = 2L, dayOfWeek = 2)
            )
        )

        assertEquals(
            merger.normalizedForComparison(first),
            merger.normalizedForComparison(second)
        )
    }

    @Test
    fun normalizedForComparison_stabilizesOrderSensitiveCollections() {
        val first = ExportData(
            tasks = listOf(
                ExportTask(id = "task_2", title = "B"),
                ExportTask(id = "task_1", title = "A")
            ),
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_1",
                    title = "Soup",
                    tags = listOf("dinner", "quick"),
                    ingredients = listOf(
                        ExportRecipeIngredient(id = "ing_2", name = "Salt", quantity = "1 tsp"),
                        ExportRecipeIngredient(id = "ing_1", name = "Water", quantity = "1 l")
                    ),
                    steps = listOf(
                        ExportRecipeStep(id = "step_2", description = "Serve", order = 2),
                        ExportRecipeStep(id = "step_1", description = "Boil", order = 1)
                    )
                )
            )
        )
        val second = ExportData(
            tasks = listOf(
                ExportTask(id = "task_1", title = "A"),
                ExportTask(id = "task_2", title = "B")
            ),
            recipes = listOf(
                ExportRecipe(
                    id = "recipe_1",
                    title = "Soup",
                    tags = listOf("quick", "dinner"),
                    ingredients = listOf(
                        ExportRecipeIngredient(id = "ing_1", name = "Water", quantity = "1 l"),
                        ExportRecipeIngredient(id = "ing_2", name = "Salt", quantity = "1 tsp")
                    ),
                    steps = listOf(
                        ExportRecipeStep(id = "step_1", description = "Boil", order = 1),
                        ExportRecipeStep(id = "step_2", description = "Serve", order = 2)
                    )
                )
            )
        )

        assertEquals(
            merger.normalizedForComparison(first),
            merger.normalizedForComparison(second)
        )
    }

    @Test
    fun normalizedForComparison_detectsContentDifferences() {
        val first = ExportData(
            tasks = listOf(ExportTask(id = "task_1", title = "A"))
        )
        val second = ExportData(
            tasks = listOf(ExportTask(id = "task_1", title = "B"))
        )

        assertNotEquals(
            merger.normalizedForComparison(first),
            merger.normalizedForComparison(second)
        )
    }
}