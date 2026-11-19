package com.familyhub.data.menu.cloud

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.utils.MenuSyncIds

/**
 * Strict structural validation for cloud merge inputs.
 *
 * Parent relations are validated later during classification; orphans are not malformed.
 */
internal object MenuCloudSnapshotValidator {

    fun validateStrict(snapshot: MenuCloudSnapshot, label: String) {
        requireUniqueIds("$label.weeklyMenus", snapshot.weeklyMenus.map { it.id })
        requireUniqueIds("$label.menuEntries", snapshot.menuEntries.map { it.id })
        requireUniqueIds("$label.menuTemplates", snapshot.menuTemplates.map { it.id })
        requireUniqueIds("$label.menuTemplateEntries", snapshot.menuTemplateEntries.map { it.id })
        requireUniqueIds("$label.dailyAssignments", snapshot.dailyAssignments.map { it.id })

        snapshot.weeklyMenus.forEach { validateWeeklyMenu("$label.weeklyMenus", it) }
        snapshot.menuEntries.forEach { validateMenuEntry("$label.menuEntries", it) }
        snapshot.menuTemplates.forEach { validateMenuTemplate("$label.menuTemplates", it) }
        snapshot.menuTemplateEntries.forEach { validateTemplateEntry("$label.menuTemplateEntries", it) }
        snapshot.dailyAssignments.forEach { validateDailyAssignment("$label.dailyAssignments", it) }
    }

    private fun validateWeeklyMenu(label: String, menu: ExportWeeklyMenu) {
        requireValidId(label, menu.id)
        if (menu.startDateMillis < 0L) {
            throw invalid("$label ${menu.id}: startDateMillis must be non-negative")
        }
        requireNonNegativeUpdatedAt(label, menu.id, menu.updatedAt)
        if (menu.createdAt < 0L) {
            throw invalid("$label ${menu.id}: createdAt must be non-negative")
        }
    }

    private fun validateMenuEntry(label: String, entry: ExportMenuEntry) {
        requireValidId(label, entry.id)
        requireValidId(label, entry.menuId)
        try {
            MenuSyncIds.requireDayOfWeek(entry.dayOfWeek)
            MenuSyncIds.requireMealType(entry.mealType)
        } catch (e: IllegalArgumentException) {
            throw invalid("$label ${entry.id}: ${e.message}", e)
        }
        if (entry.servings !in 1..99) {
            throw invalid("$label ${entry.id}: servings must be in 1..99")
        }
        requireNonNegativeUpdatedAt(label, entry.id, entry.updatedAt)
    }

    private fun validateMenuTemplate(label: String, template: ExportMenuTemplate) {
        requireValidId(label, template.id)
        if (template.name.isBlank()) {
            throw invalid("$label ${template.id}: name must not be blank")
        }
        requireNonNegativeUpdatedAt(label, template.id, template.updatedAt)
        if (template.createdAt < 0L) {
            throw invalid("$label ${template.id}: createdAt must be non-negative")
        }
    }

    private fun validateTemplateEntry(label: String, entry: ExportMenuTemplateEntry) {
        requireValidId(label, entry.id)
        requireValidId(label, entry.templateId)
        try {
            MenuSyncIds.requireDayOfWeek(entry.dayOfWeek)
            MenuSyncIds.requireMealType(entry.mealType)
        } catch (e: IllegalArgumentException) {
            throw invalid("$label ${entry.id}: ${e.message}", e)
        }
        if (entry.servings !in 1..99) {
            throw invalid("$label ${entry.id}: servings must be in 1..99")
        }
        requireNonNegativeUpdatedAt(label, entry.id, entry.updatedAt)
    }

    private fun validateDailyAssignment(label: String, assignment: ExportDailyAssignment) {
        requireValidId(label, assignment.id)
        try {
            MenuSyncIds.requireDayOfWeek(assignment.dayOfWeek)
        } catch (e: IllegalArgumentException) {
            throw invalid("$label ${assignment.id}: ${e.message}", e)
        }
        if (assignment.weekStartMillis < 0L) {
            throw invalid("$label ${assignment.id}: weekStartMillis must be non-negative")
        }
        requireNonNegativeUpdatedAt(label, assignment.id, assignment.updatedAt)
    }

    private fun requireUniqueIds(label: String, ids: List<String>) {
        val seen = HashSet<String>()
        ids.forEach { id ->
            if (!seen.add(id)) {
                throw invalid("Duplicate id in $label: $id")
            }
        }
    }

    private fun requireValidId(label: String, id: String) {
        if (id.isBlank()) {
            throw invalid("Blank id in $label")
        }
        try {
            MenuSyncIds.requireValidFirestoreDocumentId(id)
        } catch (e: IllegalArgumentException) {
            throw invalid("Invalid Firestore id in $label: $id", e)
        }
    }

    private fun requireNonNegativeUpdatedAt(label: String, id: String, updatedAt: Long) {
        if (updatedAt < 0L) {
            throw invalid("$label $id: updatedAt must be >= 0, got $updatedAt")
        }
    }

    private fun invalid(message: String, cause: Throwable? = null): MenuCloudSnapshotInvalidException =
        MenuCloudSnapshotInvalidException(message, cause)
}
