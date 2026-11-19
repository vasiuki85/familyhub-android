package com.familyhub.data.menu

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.utils.MenuSyncIds

/**
 * Pure, idempotent pre-Room menu snapshot canonicalizer.
 *
 * Shared algorithm used by JSON import, encrypted restore, and [Migration36To37Planner]
 * (via ExportData adapter).
 *
 * Order: [MenuSnapshotPreflight] → parent timestamp map → slot resolve → canonical rekey.
 */
object MenuSnapshotCanonicalizer {

    fun canonicalize(data: ExportData): ExportData {
        MenuSnapshotPreflight.validate(data)

        val templates = canonicalizeTemplates(data.menuTemplates)
        val templateUpdatedAtById = data.menuTemplates
            .map { legacyParentUpdatedAt(it) }
            .associate { it.id to it.updatedAt }
        val templateEntries = canonicalizeTemplateEntries(
            data.menuTemplateEntries,
            templateUpdatedAtById
        )

        val (weeklyMenus, menuEntries) = canonicalizeWeeklyMenusAndEntries(
            data.weeklyMenus,
            data.menuEntries
        )
        val dailyAssignments = canonicalizeDailyAssignments(data.dailyAssignments)

        return data.copy(
            menuTemplates = templates.sortedBy { it.id },
            menuTemplateEntries = templateEntries.sortedBy { it.id },
            weeklyMenus = weeklyMenus.sortedBy { it.id },
            menuEntries = menuEntries.sortedBy { it.id },
            dailyAssignments = dailyAssignments.sortedBy { it.id }
        )
    }

    private fun canonicalizeTemplates(
        templates: List<ExportMenuTemplate>
    ): List<ExportMenuTemplate> {
        templates.forEach { template ->
            require(template.name.isNotBlank()) { "menu template name must not be blank" }
        }
        // Raw ids already unique (preflight); keep selectWinner for total-order stability.
        return templates
            .groupBy { it.id }
            .map { (_, group) ->
                val withTs = group.map { legacyParentUpdatedAt(it) }
                MenuRecordComparator.selectWinner(withTs, MenuRecordComparator::compareMenuTemplates)
            }
    }

    private fun canonicalizeTemplateEntries(
        entries: List<ExportMenuTemplateEntry>,
        templateUpdatedAtById: Map<String, Long>
    ): List<ExportMenuTemplateEntry> {
        entries.forEach { entry ->
            MenuSyncIds.requireDayOfWeek(entry.dayOfWeek)
            MenuSyncIds.requireMealType(entry.mealType)
            require(entry.servings in 1..99) {
                "template entry servings must be in 1..99, got ${entry.servings}"
            }
            require(entry.templateId in templateUpdatedAtById) {
                "Orphan menu template entry ${entry.id}: templateId=${entry.templateId}"
            }
        }
        return entries
            .groupBy { Triple(it.templateId, it.dayOfWeek, it.mealType) }
            .map { (key, group) ->
                val (templateId, dayOfWeek, mealType) = key
                val parentTs = templateUpdatedAtById.getValue(templateId)
                val withTs = group.map { entry ->
                    val normalized = entry.copy(cookingTaskId = null, prepTaskId = null)
                    val effective = if (normalized.updatedAt > 0L) {
                        normalized.updatedAt
                    } else {
                        maxOf(parentTs, 1L)
                    }
                    normalized.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(
                    withTs,
                    MenuRecordComparator::compareTemplateEntries
                )
                val canonicalId = MenuSyncIds.menuTemplateEntryId(templateId, dayOfWeek, mealType)
                winner.copy(
                    id = canonicalId,
                    templateId = templateId,
                    mealType = MenuSyncIds.requireMealType(mealType),
                    cookingTaskId = null,
                    prepTaskId = null
                )
            }
    }

    private fun canonicalizeWeeklyMenusAndEntries(
        menus: List<ExportWeeklyMenu>,
        entries: List<ExportMenuEntry>
    ): Pair<List<ExportWeeklyMenu>, List<ExportMenuEntry>> {
        menus.forEach { menu ->
            require(menu.startDateMillis >= 0L) { "startDateMillis must be non-negative" }
        }
        entries.forEach { entry ->
            MenuSyncIds.requireDayOfWeek(entry.dayOfWeek)
            MenuSyncIds.requireMealType(entry.mealType)
            require(entry.servings in 1..99) {
                "menu entry servings must be in 1..99, got ${entry.servings}"
            }
        }

        // Provenance: old parent id → effective updatedAt (before week collapse).
        val parentsWithTs = menus.map { legacyParentUpdatedAt(it) }
        val oldParentIdToEffectiveUpdatedAt = parentsWithTs.associate { it.id to it.updatedAt }

        val menusByWeek = parentsWithTs.groupBy { it.startDateMillis }
        val entriesByMenuId = entries.groupBy { it.menuId }

        val canonicalMenus = mutableListOf<ExportWeeklyMenu>()
        val canonicalEntries = mutableListOf<ExportMenuEntry>()

        menusByWeek.entries.sortedBy { it.key }.forEach { (startDateMillis, parents) ->
            val winnerParent = MenuRecordComparator.selectWinner(
                parents,
                MenuRecordComparator::compareWeeklyMenus
            )
            val canonicalParentId = MenuSyncIds.weeklyMenuId(startDateMillis)
            canonicalMenus += winnerParent.copy(
                id = canonicalParentId,
                startDateMillis = startDateMillis
            )

            // Complementary slots: collect entries from ALL parents of this week.
            val allChildEntries = parents.flatMap { parent ->
                entriesByMenuId[parent.id].orEmpty()
            }
            val slotGroups = allChildEntries.groupBy { it.dayOfWeek to it.mealType }
            slotGroups.entries.sortedWith(
                compareBy({ it.key.first }, { it.key.second })
            ).forEach { (slot, group) ->
                val (dayOfWeek, mealType) = slot
                val withTs = group.map { entry ->
                    val effective = if (entry.updatedAt > 0L) {
                        entry.updatedAt
                    } else {
                        // Inherit from THIS entry's old parent, not the week winner.
                        maxOf(oldParentIdToEffectiveUpdatedAt.getValue(entry.menuId), 1L)
                    }
                    entry.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(
                    withTs,
                    MenuRecordComparator::compareMenuEntries
                )
                val canonicalEntryId = MenuSyncIds.menuEntryId(startDateMillis, dayOfWeek, mealType)
                canonicalEntries += winner.copy(
                    id = canonicalEntryId,
                    menuId = canonicalParentId,
                    dayOfWeek = dayOfWeek,
                    mealType = MenuSyncIds.requireMealType(mealType)
                )
            }
        }

        return canonicalMenus to canonicalEntries
    }

    private fun canonicalizeDailyAssignments(
        assignments: List<ExportDailyAssignment>
    ): List<ExportDailyAssignment> {
        assignments.forEach { assignment ->
            MenuSyncIds.requireDayOfWeek(assignment.dayOfWeek)
            require(assignment.weekStartMillis >= 0L) {
                "weekStartMillis must be non-negative"
            }
        }
        return assignments
            .groupBy { it.weekStartMillis to it.dayOfWeek }
            .map { (key, group) ->
                val (weekStart, dayOfWeek) = key
                val withTs = group.map { assignment ->
                    val effective = if (assignment.updatedAt > 0L) assignment.updatedAt else 1L
                    assignment.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(
                    withTs,
                    MenuRecordComparator::compareDailyAssignments
                )
                val canonicalId = MenuSyncIds.dailyAssignmentId(weekStart, dayOfWeek)
                winner.copy(
                    id = canonicalId,
                    weekStartMillis = weekStart,
                    dayOfWeek = dayOfWeek
                )
            }
    }

    private fun legacyParentUpdatedAt(menu: ExportWeeklyMenu): ExportWeeklyMenu {
        val effective = if (menu.updatedAt > 0L) {
            menu.updatedAt
        } else {
            maxOf(menu.createdAt, 1L)
        }
        return menu.copy(updatedAt = effective)
    }

    private fun legacyParentUpdatedAt(template: ExportMenuTemplate): ExportMenuTemplate {
        val effective = if (template.updatedAt > 0L) {
            template.updatedAt
        } else {
            maxOf(template.createdAt, 1L)
        }
        return template.copy(updatedAt = effective)
    }
}
