package com.familyhub.data.menu.cloud

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.menu.firestore.MenuFirestoreDocumentCodec
import com.familyhub.domain.utils.MenuSyncIds

data class MenuCloudUploadPlanInput(
    val rawRemoteBase: MenuCloudRawRemoteBase?,
    val cloudTarget: MenuCloudSnapshot,
    val ignoredOrphanIds: Set<MenuCloudRemoteIdRef>,
    val canUploadMenu: Boolean,
)

/**
 * Pure menu upload delta planner for FH2-07 integration.
 *
 * Independent of [com.familyhub.data.sync.PlannedUploadOperation] and FirestoreBackendApi.
 */
object MenuCloudUploadPlanner {

    private val collectionOrder = listOf(
        DeletedCloudDocumentCollections.WEEKLY_MENUS,
        DeletedCloudDocumentCollections.MENU_ENTRIES,
        DeletedCloudDocumentCollections.MENU_TEMPLATES,
        DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES,
        DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS,
    )

    fun plan(input: MenuCloudUploadPlanInput): List<MenuCloudWriteOperation> {
        if (!input.canUploadMenu) return emptyList()
        MenuCloudUploadTargetPreflight.validateOrThrow(input.cloudTarget)

        if (input.rawRemoteBase == null) {
            return sortOperations(
                assertNoOperationConflicts(
                    encodeAllTarget(input.cloudTarget).map { (collection, id, fields) ->
                        MenuCloudWriteOperation.Set(collection, id, fields)
                    },
                ),
            )
        }

        val ignored = input.ignoredOrphanIds.map { it.collection to it.documentId }.toSet()
        val baseWeekly = input.rawRemoteBase.weeklyMenus.filterNot { ignored.contains(weeklyKey(it)) }
        val baseEntries = input.rawRemoteBase.menuEntries.filterNot { ignored.contains(entryKey(it)) }
        val baseTemplates = input.rawRemoteBase.menuTemplates.filterNot { ignored.contains(templateKey(it)) }
        val baseTemplateEntries = input.rawRemoteBase.menuTemplateEntries.filterNot {
            ignored.contains(templateEntryKey(it))
        }
        val baseDaily = input.rawRemoteBase.dailyAssignments.filterNot { ignored.contains(dailyKey(it)) }

        val operations = mutableListOf<MenuCloudWriteOperation>()

        planWeekly(baseWeekly, input.cloudTarget.weeklyMenus, operations)
        planMenuEntries(
            baseWeekly,
            input.cloudTarget.weeklyMenus,
            baseEntries,
            input.cloudTarget.menuEntries,
            operations,
        )
        planTemplates(baseTemplates, input.cloudTarget.menuTemplates, operations)
        planTemplateEntries(baseTemplateEntries, input.cloudTarget.menuTemplateEntries, operations)
        planDaily(baseDaily, input.cloudTarget.dailyAssignments, operations)

        return sortOperations(assertNoOperationConflicts(operations))
    }

    private fun planWeekly(
        base: List<ExportWeeklyMenu>,
        target: List<ExportWeeklyMenu>,
        operations: MutableList<MenuCloudWriteOperation>,
    ) {
        val baseByWeek = base.groupBy { it.startDateMillis }
        val targetByWeek = target.associateBy { it.startDateMillis }
        val weeks = (baseByWeek.keys + targetByWeek.keys).sorted()
        weeks.forEach { week ->
            val canonicalId = MenuSyncIds.weeklyMenuId(week)
            val targetMenu = targetByWeek[week]
            val baseDocs = baseByWeek[week].orEmpty()
            if (targetMenu == null) {
                baseDocs.forEach { doc ->
                    operations += MenuCloudWriteOperation.Delete(weeklyCollection(), doc.id)
                }
                return@forEach
            }
            val baseCanonical = baseDocs.firstOrNull { it.id == canonicalId }
            val effectiveTarget = MenuCloudImmutableParentFields.effectiveWeeklyMenu(targetMenu, baseDocs)
            val encodedTarget = MenuFirestoreDocumentCodec.encodeWeeklyMenu(effectiveTarget)
            baseDocs.filter { it.id != canonicalId }.forEach { alias ->
                operations += MenuCloudWriteOperation.Delete(weeklyCollection(), alias.id)
            }
            if (baseCanonical == null || !fieldsEqual(
                    MenuFirestoreDocumentCodec.encodeWeeklyMenu(baseCanonical),
                    encodedTarget,
                )
            ) {
                operations += MenuCloudWriteOperation.Set(weeklyCollection(), canonicalId, encodedTarget)
            }
        }
    }

    private fun planMenuEntries(
        baseWeekly: List<ExportWeeklyMenu>,
        targetWeekly: List<ExportWeeklyMenu>,
        baseEntries: List<ExportMenuEntry>,
        targetEntries: List<ExportMenuEntry>,
        operations: MutableList<MenuCloudWriteOperation>,
    ) {
        val baseWeekByMenuId = baseWeekByMenuId(baseWeekly)
        val targetWeekByMenuId = targetWeekByMenuId(targetWeekly)
        val baseBySlot = baseEntries.mapNotNull { entry ->
            val week = baseWeekByMenuId[entry.menuId] ?: return@mapNotNull null
            Triple(week, entry.dayOfWeek, entry.mealType) to entry
        }.groupBy({ it.first }, { it.second })
        val targetBySlot = targetEntries.associate { entry ->
            val week = targetWeekByMenuId[entry.menuId]
                ?: throw MenuCloudUploadPlanException(
                    "Unknown menuId ${entry.menuId} for target entry ${entry.id}",
                )
            Triple(week, entry.dayOfWeek, entry.mealType) to entry
        }
        val slots = (baseBySlot.keys + targetBySlot.keys).sortedWith(
            compareBy({ it.first }, { it.second }, { it.third }),
        )
        slots.forEach { slot ->
            val (week, day, meal) = slot
            val canonicalId = MenuSyncIds.menuEntryId(week, day, meal)
            val target = targetBySlot[slot]
            val baseDocs = baseBySlot[slot].orEmpty()
            if (target == null) {
                baseDocs.forEach { operations += MenuCloudWriteOperation.Delete(entryCollection(), it.id) }
                return@forEach
            }
            val encodedTarget = MenuFirestoreDocumentCodec.encodeMenuEntry(target)
            baseDocs.filter { it.id != canonicalId }.forEach { alias ->
                operations += MenuCloudWriteOperation.Delete(entryCollection(), alias.id)
            }
            val baseCanonical = baseDocs.firstOrNull { it.id == canonicalId }
            if (baseCanonical == null || !fieldsEqual(
                    MenuFirestoreDocumentCodec.encodeMenuEntry(baseCanonical),
                    encodedTarget,
                )
            ) {
                operations += MenuCloudWriteOperation.Set(entryCollection(), canonicalId, encodedTarget)
            }
        }
    }

    private fun planTemplates(
        base: List<ExportMenuTemplate>,
        target: List<ExportMenuTemplate>,
        operations: MutableList<MenuCloudWriteOperation>,
    ) {
        val baseById = base.associateBy { it.id }
        val targetById = target.associateBy { it.id }
        val ids = (baseById.keys + targetById.keys).sorted()
        ids.forEach { id ->
            val targetTemplate = targetById[id]
            val baseTemplate = baseById[id]
            if (targetTemplate == null) {
                if (baseTemplate != null) {
                    operations += MenuCloudWriteOperation.Delete(templateCollection(), id)
                }
                return@forEach
            }
            val encodedTarget = MenuFirestoreDocumentCodec.encodeMenuTemplate(
                MenuCloudImmutableParentFields.effectiveMenuTemplate(targetTemplate, baseTemplate),
            )
            if (baseTemplate == null || !fieldsEqual(
                    MenuFirestoreDocumentCodec.encodeMenuTemplate(baseTemplate),
                    encodedTarget,
                )
            ) {
                operations += MenuCloudWriteOperation.Set(templateCollection(), id, encodedTarget)
            }
        }
    }

    private fun planTemplateEntries(
        base: List<ExportMenuTemplateEntry>,
        target: List<ExportMenuTemplateEntry>,
        operations: MutableList<MenuCloudWriteOperation>,
    ) {
        val baseBySlot = base.groupBy { Triple(it.templateId, it.dayOfWeek, it.mealType) }
        val targetBySlot = target.associateBy { Triple(it.templateId, it.dayOfWeek, it.mealType) }
        val slots = (baseBySlot.keys + targetBySlot.keys).sortedWith(
            compareBy({ it.first }, { it.second }, { it.third }),
        )
        slots.forEach { (templateId, day, meal) ->
            val canonicalId = MenuSyncIds.menuTemplateEntryId(templateId, day, meal)
            val targetEntry = targetBySlot[Triple(templateId, day, meal)]
            val baseDocs = baseBySlot[Triple(templateId, day, meal)].orEmpty()
            if (targetEntry == null) {
                baseDocs.forEach {
                    operations += MenuCloudWriteOperation.Delete(templateEntryCollection(), it.id)
                }
                return@forEach
            }
            val encodedTarget = MenuFirestoreDocumentCodec.encodeMenuTemplateEntry(targetEntry)
            baseDocs.filter { it.id != canonicalId }.forEach { alias ->
                operations += MenuCloudWriteOperation.Delete(templateEntryCollection(), alias.id)
            }
            val baseCanonical = baseDocs.firstOrNull { it.id == canonicalId }
            if (baseCanonical == null || !fieldsEqual(
                    MenuFirestoreDocumentCodec.encodeMenuTemplateEntry(baseCanonical),
                    encodedTarget,
                )
            ) {
                operations += MenuCloudWriteOperation.Set(templateEntryCollection(), canonicalId, encodedTarget)
            }
        }
    }

    private fun planDaily(
        base: List<ExportDailyAssignment>,
        target: List<ExportDailyAssignment>,
        operations: MutableList<MenuCloudWriteOperation>,
    ) {
        val baseBySlot = base.groupBy { it.weekStartMillis to it.dayOfWeek }
        val targetBySlot = target.associateBy { it.weekStartMillis to it.dayOfWeek }
        val slots = (baseBySlot.keys + targetBySlot.keys).sortedWith(compareBy({ it.first }, { it.second }))
        slots.forEach { (week, day) ->
            val canonicalId = MenuSyncIds.dailyAssignmentId(week, day)
            val targetAssignment = targetBySlot[week to day]
            val baseDocs = baseBySlot[week to day].orEmpty()
            if (targetAssignment == null) {
                baseDocs.forEach { operations += MenuCloudWriteOperation.Delete(dailyCollection(), it.id) }
                return@forEach
            }
            val encodedTarget = MenuFirestoreDocumentCodec.encodeDailyAssignment(targetAssignment)
            baseDocs.filter { it.id != canonicalId }.forEach { alias ->
                operations += MenuCloudWriteOperation.Delete(dailyCollection(), alias.id)
            }
            val baseCanonical = baseDocs.firstOrNull { it.id == canonicalId }
            if (baseCanonical == null || !fieldsEqual(
                    MenuFirestoreDocumentCodec.encodeDailyAssignment(baseCanonical),
                    encodedTarget,
                )
            ) {
                operations += MenuCloudWriteOperation.Set(dailyCollection(), canonicalId, encodedTarget)
            }
        }
    }

    private fun encodeAllTarget(target: MenuCloudSnapshot): List<Triple<String, String, Map<String, Any?>>> =
        buildList {
            target.weeklyMenus.forEach { menu ->
                add(Triple(weeklyCollection(), menu.id, MenuFirestoreDocumentCodec.encodeWeeklyMenu(menu)))
            }
            target.menuEntries.forEach { entry ->
                add(Triple(entryCollection(), entry.id, MenuFirestoreDocumentCodec.encodeMenuEntry(entry)))
            }
            target.menuTemplates.forEach { template ->
                add(Triple(templateCollection(), template.id, MenuFirestoreDocumentCodec.encodeMenuTemplate(template)))
            }
            target.menuTemplateEntries.forEach { entry ->
                add(
                    Triple(
                        templateEntryCollection(),
                        entry.id,
                        MenuFirestoreDocumentCodec.encodeMenuTemplateEntry(entry),
                    ),
                )
            }
            target.dailyAssignments.forEach { assignment ->
                add(
                    Triple(
                        dailyCollection(),
                        assignment.id,
                        MenuFirestoreDocumentCodec.encodeDailyAssignment(assignment),
                    ),
                )
            }
        }

    private fun baseWeekByMenuId(baseWeekly: List<ExportWeeklyMenu>): Map<String, Long> = buildMap {
        baseWeekly.forEach { menu ->
            put(menu.id, menu.startDateMillis)
        }
    }

    private fun targetWeekByMenuId(targetWeekly: List<ExportWeeklyMenu>): Map<String, Long> = buildMap {
        targetWeekly.forEach { menu ->
            put(MenuSyncIds.weeklyMenuId(menu.startDateMillis), menu.startDateMillis)
        }
    }

    private fun fieldsEqual(left: Map<String, Any?>, right: Map<String, Any?>): Boolean = left == right

    private fun assertNoOperationConflicts(
        operations: List<MenuCloudWriteOperation>,
    ): List<MenuCloudWriteOperation> {
        val grouped = operations.groupBy { it.collection to it.documentId }
        grouped.forEach { (key, ops) ->
            if (ops.size > 1) {
                val kinds = ops.joinToString(", ") { operationKindLabel(it) }
                throw MenuCloudUploadPlanException(
                    "Conflicting upload operations for ${key.first}/${key.second}: $kinds",
                )
            }
        }
        return operations
    }

    private fun operationKindLabel(operation: MenuCloudWriteOperation): String = when (operation) {
        is MenuCloudWriteOperation.Delete -> "Delete"
        is MenuCloudWriteOperation.Set -> "Set"
    }

    private fun sortOperations(operations: List<MenuCloudWriteOperation>): List<MenuCloudWriteOperation> =
        operations.sortedWith(
            compareBy<MenuCloudWriteOperation>(
                { collectionOrder.indexOf(it.collection) },
                { it.documentId },
                { when (it) {
                    is MenuCloudWriteOperation.Delete -> 0
                    is MenuCloudWriteOperation.Set -> 1
                } },
            ),
        )

    private fun weeklyCollection() = DeletedCloudDocumentCollections.WEEKLY_MENUS
    private fun entryCollection() = DeletedCloudDocumentCollections.MENU_ENTRIES
    private fun templateCollection() = DeletedCloudDocumentCollections.MENU_TEMPLATES
    private fun templateEntryCollection() = DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES
    private fun dailyCollection() = DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS

    private fun weeklyKey(menu: ExportWeeklyMenu) =
        weeklyCollection() to menu.id

    private fun entryKey(entry: ExportMenuEntry) =
        entryCollection() to entry.id

    private fun templateKey(template: ExportMenuTemplate) =
        templateCollection() to template.id

    private fun templateEntryKey(entry: ExportMenuTemplateEntry) =
        templateEntryCollection() to entry.id

    private fun dailyKey(assignment: ExportDailyAssignment) =
        dailyCollection() to assignment.id
}
