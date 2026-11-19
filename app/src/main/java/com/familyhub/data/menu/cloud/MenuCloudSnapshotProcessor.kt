package com.familyhub.data.menu.cloud

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.menu.MenuRecordComparator
import com.familyhub.domain.utils.MenuSyncIds

internal data class MenuCloudProcessedSnapshot(
    val snapshot: MenuCloudSnapshot,
    val classifications: List<MenuCloudRemoteIdClassification>,
    val documentIndex: MenuCloudDocumentIndex,
)

internal data class MenuCloudDocumentIndex(
    val weeklyMenuIdToWeek: Map<String, Long>,
    val menuEntryIdToSlot: Map<String, MenuCloudMenuEntrySlot>,
    val templateIds: Set<String>,
    val templateEntryIdToSlot: Map<String, MenuCloudTemplateEntrySlot>,
    val dailyAssignmentIdToSlot: Map<String, MenuCloudDailySlot>,
) {
    fun weekForMenuId(menuId: String): Long? = weeklyMenuIdToWeek[menuId]

    companion object {
        fun merge(vararg indices: MenuCloudDocumentIndex): MenuCloudDocumentIndex {
            if (indices.isEmpty()) {
                return MenuCloudDocumentIndex(emptyMap(), emptyMap(), emptySet(), emptyMap(), emptyMap())
            }
            return MenuCloudDocumentIndex(
                weeklyMenuIdToWeek = indices.flatMap { it.weeklyMenuIdToWeek.entries }.associate { it.toPair() },
                menuEntryIdToSlot = indices.flatMap { it.menuEntryIdToSlot.entries }.associate { it.toPair() },
                templateIds = indices.flatMap { it.templateIds }.toSet(),
                templateEntryIdToSlot = indices.flatMap { it.templateEntryIdToSlot.entries }.associate { it.toPair() },
                dailyAssignmentIdToSlot = indices.flatMap { it.dailyAssignmentIdToSlot.entries }.associate { it.toPair() },
            )
        }
    }
}

internal data class MenuCloudMenuEntrySlot(
    val weekStartMillis: Long,
    val dayOfWeek: Int,
    val mealType: String,
)

internal data class MenuCloudTemplateEntrySlot(
    val templateId: String,
    val dayOfWeek: Int,
    val mealType: String,
)

internal data class MenuCloudDailySlot(
    val weekStartMillis: Long,
    val dayOfWeek: Int,
)

/**
 * Canonicalizes one snapshot, classifies alias/orphan remote IDs, and normalizes references.
 */
internal object MenuCloudSnapshotProcessor {

    fun process(snapshot: MenuCloudSnapshot): MenuCloudProcessedSnapshot {
        val classifications = mutableListOf<MenuCloudRemoteIdClassification>()
        val weekly = processWeeklyMenus(snapshot.weeklyMenus, classifications)
        val menuEntries = processMenuEntries(snapshot.menuEntries, weekly, classifications)
        val templates = processTemplates(snapshot.menuTemplates, classifications)
        val templateEntries = processTemplateEntries(snapshot.menuTemplateEntries, templates, classifications)
        val dailyAssignments = processDailyAssignments(snapshot.dailyAssignments, classifications)

        val canonical = MenuCloudSnapshot(
            weeklyMenus = weekly.canonicalMenus,
            menuEntries = menuEntries.canonicalEntries,
            menuTemplates = templates.canonicalTemplates,
            menuTemplateEntries = templateEntries.canonicalEntries,
            dailyAssignments = dailyAssignments.canonicalAssignments,
        )
        val index = buildDocumentIndex(
            canonical = canonical,
            weeklyIdToWeek = weekly.idToWeek,
            templateIds = templates.templateIds,
            entryAliasSlots = menuEntries.entryAliasSlots,
            templateEntryAliasSlots = templateEntries.entryAliasSlots,
            dailyAliasSlots = dailyAssignments.assignmentAliasSlots,
        )
        return MenuCloudProcessedSnapshot(
            snapshot = canonical,
            classifications = finalizeClassifications(classifications),
            documentIndex = index,
        )
    }

    fun mergeWriters(
        left: MenuCloudProcessedSnapshot,
        right: MenuCloudProcessedSnapshot,
    ): MenuCloudProcessedSnapshot {
        val mergedWeekly = mergeWeeklyMenus(left.snapshot.weeklyMenus, right.snapshot.weeklyMenus)
        val mergedEntries = mergeMenuEntries(left.snapshot.menuEntries, right.snapshot.menuEntries)
        val mergedTemplates = mergeTemplates(left.snapshot.menuTemplates, right.snapshot.menuTemplates)
        val mergedTemplateEntries = mergeTemplateEntries(
            left.snapshot.menuTemplateEntries,
            right.snapshot.menuTemplateEntries,
        )
        val mergedDaily = mergeDailyAssignments(
            left.snapshot.dailyAssignments,
            right.snapshot.dailyAssignments,
        )
        val snapshot = MenuCloudSnapshot(
            weeklyMenus = mergedWeekly,
            menuEntries = mergedEntries,
            menuTemplates = mergedTemplates,
            menuTemplateEntries = mergedTemplateEntries,
            dailyAssignments = mergedDaily,
        )
        val weeklyIdToWeek = mergedWeekly.associate { it.id to it.startDateMillis } +
            mergedWeekly.associate { MenuSyncIds.weeklyMenuId(it.startDateMillis) to it.startDateMillis }
        val templateIds = mergedTemplates.map { it.id }.toSet()
        return MenuCloudProcessedSnapshot(
            snapshot = snapshot,
            classifications = emptyList(),
            documentIndex = buildDocumentIndex(
                canonical = snapshot,
                weeklyIdToWeek = weeklyIdToWeek,
                templateIds = templateIds,
                entryAliasSlots = left.documentIndex.menuEntryIdToSlot +
                    right.documentIndex.menuEntryIdToSlot,
                templateEntryAliasSlots = left.documentIndex.templateEntryIdToSlot +
                    right.documentIndex.templateEntryIdToSlot,
                dailyAliasSlots = left.documentIndex.dailyAssignmentIdToSlot +
                    right.documentIndex.dailyAssignmentIdToSlot,
            ),
        )
    }

    fun applyTombstones(
        snapshot: MenuCloudSnapshot,
        tombstones: List<MenuCloudTombstone>,
        documentIndex: MenuCloudDocumentIndex,
    ): MenuCloudSnapshot {
        if (tombstones.isEmpty()) return snapshot
        val index = documentIndex
        val suppressedWeeks = mutableSetOf<Long>()
        val suppressedMenuSlots = mutableSetOf<MenuCloudMenuEntrySlot>()
        val suppressedTemplateIds = mutableSetOf<String>()
        val suppressedTemplateSlots = mutableSetOf<MenuCloudTemplateEntrySlot>()
        val suppressedDailySlots = mutableSetOf<MenuCloudDailySlot>()

        tombstones.forEach { tombstone ->
            when (tombstone.collection) {
                DeletedCloudDocumentCollections.WEEKLY_MENUS -> {
                    val week = index.weeklyMenuIdToWeek[tombstone.documentId] ?: return@forEach
                    suppressedWeeks += week
                }
                DeletedCloudDocumentCollections.MENU_ENTRIES -> {
                    val slot = index.menuEntryIdToSlot[tombstone.documentId] ?: return@forEach
                    suppressedMenuSlots += slot
                }
                DeletedCloudDocumentCollections.MENU_TEMPLATES -> {
                    if (tombstone.documentId !in index.templateIds) return@forEach
                    suppressedTemplateIds += tombstone.documentId
                }
                DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES -> {
                    val slot = index.templateEntryIdToSlot[tombstone.documentId] ?: return@forEach
                    suppressedTemplateSlots += slot
                }
                DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS -> {
                    val slot = index.dailyAssignmentIdToSlot[tombstone.documentId] ?: return@forEach
                    suppressedDailySlots += slot
                }
            }
        }

        val weeklyMenus = snapshot.weeklyMenus.filter { menu ->
            menu.startDateMillis !in suppressedWeeks
        }
        val menuEntries = snapshot.menuEntries.filter { entry ->
            val slot = MenuCloudMenuEntrySlot(
                weekStartMillis = index.weekForMenuId(entry.menuId) ?: return@filter false,
                dayOfWeek = entry.dayOfWeek,
                mealType = entry.mealType,
            )
            slot !in suppressedMenuSlots && slot.weekStartMillis !in suppressedWeeks
        }
        val menuTemplates = snapshot.menuTemplates.filter { template ->
            template.id !in suppressedTemplateIds
        }
        val menuTemplateEntries = snapshot.menuTemplateEntries.filter { entry ->
            entry.templateId !in suppressedTemplateIds &&
                MenuCloudTemplateEntrySlot(entry.templateId, entry.dayOfWeek, entry.mealType) !in suppressedTemplateSlots
        }
        val dailyAssignments = snapshot.dailyAssignments.filter { assignment ->
            MenuCloudDailySlot(assignment.weekStartMillis, assignment.dayOfWeek) !in suppressedDailySlots
        }
        return MenuCloudSnapshot(
            weeklyMenus = weeklyMenus,
            menuEntries = menuEntries,
            menuTemplates = menuTemplates,
            menuTemplateEntries = menuTemplateEntries,
            dailyAssignments = dailyAssignments,
        )
    }

    private data class WeeklyProcessResult(
        val canonicalMenus: List<ExportWeeklyMenu>,
        val idToWeek: Map<String, Long>,
    )

    private fun processWeeklyMenus(
        menus: List<ExportWeeklyMenu>,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ): WeeklyProcessResult {
        val idToWeek = menus.associate { it.id to it.startDateMillis }
        val canonicalMenus = menus
            .map { legacyParentUpdatedAt(it) }
            .groupBy { it.startDateMillis }
            .entries
            .sortedBy { it.key }
            .map { (startDateMillis, group) ->
                val winner = MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareWeeklyMenus)
                val canonicalId = MenuSyncIds.weeklyMenuId(startDateMillis)
                group.forEach { item ->
                    classifyObsoleteAlias(
                        collection = DeletedCloudDocumentCollections.WEEKLY_MENUS,
                        rawId = item.id,
                        canonicalId = canonicalId,
                        classifications = classifications,
                    )
                }
                winner.copy(id = canonicalId, startDateMillis = startDateMillis)
            }
        val mergedIdToWeek = buildMap {
            canonicalMenus.forEach { menu ->
                put(menu.id, menu.startDateMillis)
            }
            idToWeek.forEach { (rawId, week) ->
                put(rawId, week)
            }
        }
        return WeeklyProcessResult(canonicalMenus, mergedIdToWeek)
    }

    private data class MenuEntriesProcessResult(
        val canonicalEntries: List<ExportMenuEntry>,
        val entryAliasSlots: Map<String, MenuCloudMenuEntrySlot> = emptyMap(),
    )

    private fun processMenuEntries(
        entries: List<ExportMenuEntry>,
        weekly: WeeklyProcessResult,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ): MenuEntriesProcessResult {
        val entriesByMenuId = entries.groupBy { it.menuId }
        val parentUpdatedAtById = weekly.canonicalMenus.associate { it.id to it.updatedAt } +
            weekly.idToWeek.mapNotNull { (menuId, week) ->
                weekly.canonicalMenus.firstOrNull { it.startDateMillis == week }?.updatedAt?.let { menuId to it }
            }.toMap()
        val entryAliasSlots = mutableMapOf<String, MenuCloudMenuEntrySlot>()
        val canonicalEntries = mutableListOf<ExportMenuEntry>()
        weekly.canonicalMenus.forEach { parent ->
            val weekStart = parent.startDateMillis
            val canonicalParentId = parent.id
            val allChildEntries = weekly.idToWeek.entries
                .filter { it.value == weekStart }
                .flatMap { (menuId, _) -> entriesByMenuId[menuId].orEmpty() }
            val slotGroups = allChildEntries.groupBy { it.dayOfWeek to it.mealType }
            slotGroups.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).forEach { (slot, group) ->
                val (dayOfWeek, mealType) = slot
                val entrySlot = MenuCloudMenuEntrySlot(weekStart, dayOfWeek, mealType)
                group.forEach { entry -> entryAliasSlots[entry.id] = entrySlot }
                val withTs = group.map { entry ->
                    val effective = if (entry.updatedAt > 0L) {
                        entry.updatedAt
                    } else {
                        maxOf(parentUpdatedAtById[entry.menuId] ?: 1L, 1L)
                    }
                    entry.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(withTs, MenuRecordComparator::compareMenuEntries)
                val canonicalEntryId = MenuSyncIds.menuEntryId(weekStart, dayOfWeek, mealType)
                group.forEach { item ->
                    classifyObsoleteAlias(
                        collection = DeletedCloudDocumentCollections.MENU_ENTRIES,
                        rawId = item.id,
                        canonicalId = canonicalEntryId,
                        classifications = classifications,
                    )
                }
                canonicalEntries += winner.copy(
                    id = canonicalEntryId,
                    menuId = canonicalParentId,
                    dayOfWeek = dayOfWeek,
                    mealType = MenuSyncIds.requireMealType(mealType),
                )
            }
        }
        val knownMenuIds = weekly.idToWeek.keys
        entries.filter { it.menuId !in knownMenuIds }.forEach { orphan ->
            classifications += MenuCloudRemoteIdClassification(
                collection = DeletedCloudDocumentCollections.MENU_ENTRIES,
                documentId = orphan.id,
                kind = MenuCloudRemoteIdKind.IGNORED_ORPHAN,
            )
        }
        return MenuEntriesProcessResult(
            canonicalEntries = canonicalEntries.sortedWith(menuEntryOrder),
            entryAliasSlots = entryAliasSlots,
        )
    }

    private data class TemplateProcessResult(
        val canonicalTemplates: List<ExportMenuTemplate>,
        val templateIds: Set<String>,
    )

    private fun processTemplates(
        templates: List<ExportMenuTemplate>,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ): TemplateProcessResult {
        val canonicalTemplates = templates
            .map { legacyParentUpdatedAt(it) }
            .groupBy { it.id }
            .entries
            .sortedBy { it.key }
            .map { (_, group) ->
                val winner = MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareMenuTemplates)
                group.filter { it.id != winner.id }.forEach { loser ->
                    classifications += MenuCloudRemoteIdClassification(
                        collection = DeletedCloudDocumentCollections.MENU_TEMPLATES,
                        documentId = loser.id,
                        kind = MenuCloudRemoteIdKind.OBSOLETE_ALIAS,
                    )
                }
                winner
            }
        return TemplateProcessResult(canonicalTemplates, canonicalTemplates.map { it.id }.toSet())
    }

    private data class TemplateEntriesProcessResult(
        val canonicalEntries: List<ExportMenuTemplateEntry>,
        val entryAliasSlots: Map<String, MenuCloudTemplateEntrySlot> = emptyMap(),
    )

    private fun processTemplateEntries(
        entries: List<ExportMenuTemplateEntry>,
        templates: TemplateProcessResult,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ): TemplateEntriesProcessResult {
        val templateUpdatedAtById = templates.canonicalTemplates.associate { it.id to it.updatedAt }
        val entriesByTemplateId = entries.groupBy { it.templateId }
        val canonicalEntries = mutableListOf<ExportMenuTemplateEntry>()
        val entryAliasSlots = mutableMapOf<String, MenuCloudTemplateEntrySlot>()
        templates.canonicalTemplates.forEach { template ->
            val groups = entriesByTemplateId[template.id].orEmpty().groupBy { it.dayOfWeek to it.mealType }
            groups.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).forEach { (slot, group) ->
                val (dayOfWeek, mealType) = slot
                val entrySlot = MenuCloudTemplateEntrySlot(template.id, dayOfWeek, mealType)
                group.forEach { entry -> entryAliasSlots[entry.id] = entrySlot }
                val parentTs = templateUpdatedAtById.getValue(template.id)
                val withTs = group.map { entry ->
                    val normalized = entry.copy(cookingTaskId = null, prepTaskId = null)
                    val effective = if (normalized.updatedAt > 0L) normalized.updatedAt else maxOf(parentTs, 1L)
                    normalized.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(withTs, MenuRecordComparator::compareTemplateEntries)
                val canonicalId = MenuSyncIds.menuTemplateEntryId(template.id, dayOfWeek, mealType)
                group.forEach { item ->
                    classifyObsoleteAlias(
                        collection = DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES,
                        rawId = item.id,
                        canonicalId = canonicalId,
                        classifications = classifications,
                    )
                }
                canonicalEntries += winner.copy(
                    id = canonicalId,
                    templateId = template.id,
                    dayOfWeek = dayOfWeek,
                    mealType = MenuSyncIds.requireMealType(mealType),
                    cookingTaskId = null,
                    prepTaskId = null,
                )
            }
        }
        entries.filter { it.templateId !in templates.templateIds }.forEach { orphan ->
            classifications += MenuCloudRemoteIdClassification(
                collection = DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES,
                documentId = orphan.id,
                kind = MenuCloudRemoteIdKind.IGNORED_ORPHAN,
            )
        }
        return TemplateEntriesProcessResult(
            canonicalEntries = canonicalEntries.sortedWith(templateEntryOrder),
            entryAliasSlots = entryAliasSlots,
        )
    }

    private data class DailyProcessResult(
        val canonicalAssignments: List<ExportDailyAssignment>,
        val assignmentAliasSlots: Map<String, MenuCloudDailySlot> = emptyMap(),
    )

    private fun processDailyAssignments(
        assignments: List<ExportDailyAssignment>,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ): DailyProcessResult {
        val assignmentAliasSlots = mutableMapOf<String, MenuCloudDailySlot>()
        val canonicalAssignments = assignments
            .groupBy { it.weekStartMillis to it.dayOfWeek }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (key, group) ->
                val (weekStart, dayOfWeek) = key
                val dailySlot = MenuCloudDailySlot(weekStart, dayOfWeek)
                group.forEach { assignment -> assignmentAliasSlots[assignment.id] = dailySlot }
                val withTs = group.map { assignment ->
                    val effective = if (assignment.updatedAt > 0L) assignment.updatedAt else 1L
                    assignment.copy(updatedAt = effective)
                }
                val winner = MenuRecordComparator.selectWinner(withTs, MenuRecordComparator::compareDailyAssignments)
                val canonicalId = MenuSyncIds.dailyAssignmentId(weekStart, dayOfWeek)
                group.forEach { item ->
                    classifyObsoleteAlias(
                        collection = DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS,
                        rawId = item.id,
                        canonicalId = canonicalId,
                        classifications = classifications,
                    )
                }
                winner.copy(
                    id = canonicalId,
                    weekStartMillis = weekStart,
                    dayOfWeek = dayOfWeek,
                )
            }
        return DailyProcessResult(
            canonicalAssignments = canonicalAssignments,
            assignmentAliasSlots = assignmentAliasSlots,
        )
    }

    private fun mergeWeeklyMenus(
        left: List<ExportWeeklyMenu>,
        right: List<ExportWeeklyMenu>,
    ): List<ExportWeeklyMenu> {
        return (left + right)
            .groupBy { it.startDateMillis }
            .entries
            .sortedBy { it.key }
            .map { (startDateMillis, group) ->
                val winner = MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareWeeklyMenus)
                winner.copy(id = MenuSyncIds.weeklyMenuId(startDateMillis), startDateMillis = startDateMillis)
            }
    }

    private fun mergeMenuEntries(
        left: List<ExportMenuEntry>,
        right: List<ExportMenuEntry>,
    ): List<ExportMenuEntry> {
        return (left + right)
            .groupBy { Triple(it.menuId, it.dayOfWeek, it.mealType) }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third }))
            .map { (_, group) ->
                val winner = MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareMenuEntries)
                winner
            }
    }

    private fun mergeTemplates(
        left: List<ExportMenuTemplate>,
        right: List<ExportMenuTemplate>,
    ): List<ExportMenuTemplate> {
        return (left + right)
            .groupBy { it.id }
            .entries
            .sortedBy { it.key }
            .map { (_, group) ->
                MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareMenuTemplates)
            }
    }

    private fun mergeTemplateEntries(
        left: List<ExportMenuTemplateEntry>,
        right: List<ExportMenuTemplateEntry>,
    ): List<ExportMenuTemplateEntry> {
        return (left + right)
            .groupBy { Triple(it.templateId, it.dayOfWeek, it.mealType) }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third }))
            .map { (_, group) ->
                val winner = MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareTemplateEntries)
                winner.copy(cookingTaskId = null, prepTaskId = null)
            }
    }

    private fun mergeDailyAssignments(
        left: List<ExportDailyAssignment>,
        right: List<ExportDailyAssignment>,
    ): List<ExportDailyAssignment> {
        return (left + right)
            .groupBy { it.weekStartMillis to it.dayOfWeek }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (_, group) ->
                MenuRecordComparator.selectWinner(group, MenuRecordComparator::compareDailyAssignments)
            }
    }

    private fun buildDocumentIndex(
        canonical: MenuCloudSnapshot,
        weeklyIdToWeek: Map<String, Long>,
        templateIds: Set<String>,
        entryAliasSlots: Map<String, MenuCloudMenuEntrySlot> = emptyMap(),
        templateEntryAliasSlots: Map<String, MenuCloudTemplateEntrySlot> = emptyMap(),
        dailyAliasSlots: Map<String, MenuCloudDailySlot> = emptyMap(),
    ): MenuCloudDocumentIndex {
        val menuEntryIdToSlot = buildMap {
            putAll(entryAliasSlots)
            canonical.menuEntries.forEach { entry ->
                val week = weeklyIdToWeek[entry.menuId] ?: return@forEach
                put(entry.id, MenuCloudMenuEntrySlot(week, entry.dayOfWeek, entry.mealType))
            }
        }
        val templateEntryIdToSlot = buildMap {
            putAll(templateEntryAliasSlots)
            canonical.menuTemplateEntries.forEach { entry ->
                put(
                    entry.id,
                    MenuCloudTemplateEntrySlot(entry.templateId, entry.dayOfWeek, entry.mealType),
                )
            }
        }
        val dailyAssignmentIdToSlot = buildMap {
            putAll(dailyAliasSlots)
            canonical.dailyAssignments.forEach { assignment ->
                put(
                    assignment.id,
                    MenuCloudDailySlot(assignment.weekStartMillis, assignment.dayOfWeek),
                )
            }
        }
        return MenuCloudDocumentIndex(
            weeklyMenuIdToWeek = weeklyIdToWeek + canonical.weeklyMenus.associate { it.id to it.startDateMillis },
            menuEntryIdToSlot = menuEntryIdToSlot,
            templateIds = templateIds,
            templateEntryIdToSlot = templateEntryIdToSlot,
            dailyAssignmentIdToSlot = dailyAssignmentIdToSlot,
        )
    }

    private fun classifyObsoleteAlias(
        collection: String,
        rawId: String,
        canonicalId: String,
        classifications: MutableList<MenuCloudRemoteIdClassification>,
    ) {
        if (rawId == canonicalId) return
        classifications += MenuCloudRemoteIdClassification(
            collection = collection,
            documentId = rawId,
            kind = MenuCloudRemoteIdKind.OBSOLETE_ALIAS,
        )
    }

    private fun finalizeClassifications(
        classifications: List<MenuCloudRemoteIdClassification>,
    ): List<MenuCloudRemoteIdClassification> {
        return classifications
            .distinctBy { Triple(it.collection, it.documentId, it.kind) }
            .sortedWith(compareBy({ it.collection }, { it.documentId }, { it.kind.name }))
    }

    private fun legacyParentUpdatedAt(menu: ExportWeeklyMenu): ExportWeeklyMenu {
        val effective = if (menu.updatedAt > 0L) menu.updatedAt else maxOf(menu.createdAt, 1L)
        return menu.copy(updatedAt = effective)
    }

    private fun legacyParentUpdatedAt(template: ExportMenuTemplate): ExportMenuTemplate {
        val effective = if (template.updatedAt > 0L) template.updatedAt else maxOf(template.createdAt, 1L)
        return template.copy(updatedAt = effective)
    }

    private val menuEntryOrder = compareBy<ExportMenuEntry>(
        { it.menuId },
        { it.dayOfWeek },
        { it.mealType },
    )

    private val templateEntryOrder = compareBy<ExportMenuTemplateEntry>(
        { it.templateId },
        { it.dayOfWeek },
        { it.mealType },
    )
}
