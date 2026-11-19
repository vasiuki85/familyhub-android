package com.familyhub.ui.screens.menu

import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.WeeklyMenu

internal object MenuPrepTaskPlanner {

    fun plan(
        menu: WeeklyMenu,
        recipesById: Map<String, Recipe>,
        longPrepThresholdMinutes: Int,
    ): MenuPrepTaskPlan {
        val taskIdsToDelete = mutableSetOf<String>()
        val updatedEntries = menu.entries.map { entry ->
            val recipe = entry.recipeId?.let { recipesById[it] }
            if (recipe == null || recipe.prepTimeMinutes < longPrepThresholdMinutes) {
                entry.prepTaskId?.let { taskIdsToDelete.add(it) }
                entry.copy(prepTaskId = null)
            } else {
                entry
            }
        }
        return MenuPrepTaskPlan(
            menu = menu.copy(entries = updatedEntries),
            taskIdsToDeleteAfterCommit = taskIdsToDelete,
        )
    }
}
