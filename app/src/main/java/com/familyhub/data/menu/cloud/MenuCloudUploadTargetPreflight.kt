package com.familyhub.data.menu.cloud

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.menu.firestore.MenuFirestoreDocumentCodec
import com.familyhub.data.menu.firestore.MenuFirestoreDocumentCodecException
import com.familyhub.domain.utils.MenuSyncIds

/**
 * Canonical cloud-target validation before upload planning.
 *
 * Fail closed: invalid targets produce [MenuCloudUploadPlanException] with no partial operations.
 */
object MenuCloudUploadTargetPreflight {

    fun validateOrThrow(target: MenuCloudSnapshot) {
        try {
            validate(target)
        } catch (error: MenuFirestoreDocumentCodecException) {
            throw MenuCloudUploadPlanException(error.message ?: "Invalid menu cloud target", error)
        }
    }

    private fun validate(target: MenuCloudSnapshot) {
        val weekStartByCanonicalMenuId = linkedMapOf<String, Long>()
        target.weeklyMenus.forEach { menu ->
            requireCanonicalWeeklyMenu(menu)
            val encoded = MenuFirestoreDocumentCodec.encodeWeeklyMenu(menu)
            assertEncodedIdMatches(menu.id, encoded)
            if (weekStartByCanonicalMenuId.put(menu.id, menu.startDateMillis) != null) {
                throw duplicateLogicalKey("weekly_menus", menu.id)
            }
        }

        val templateIds = linkedSetOf<String>()
        target.menuTemplates.forEach { template ->
            validateTemplateRecord(template)
            if (!templateIds.add(template.id)) {
                throw duplicateLogicalKey("menu_templates", template.id)
            }
        }

        val menuEntrySlots = linkedSetOf<Triple<Long, Int, String>>()
        target.menuEntries.forEach { entry ->
            val weekStart = weekStartByCanonicalMenuId[entry.menuId]
                ?: throw orphanChild("menu_entries", entry.id, "menuId", entry.menuId)
            requireCanonicalMenuEntry(entry, weekStart)
            val encoded = MenuFirestoreDocumentCodec.encodeMenuEntry(entry)
            assertEncodedIdMatches(entry.id, encoded)
            val slot = Triple(weekStart, entry.dayOfWeek, entry.mealType)
            if (!menuEntrySlots.add(slot)) {
                throw duplicateLogicalKey(
                    "menu_entries",
                    "${entry.dayOfWeek}/${entry.mealType}@$weekStart",
                )
            }
        }

        val templateEntrySlots = linkedSetOf<Triple<String, Int, String>>()
        target.menuTemplateEntries.forEach { entry ->
            if (entry.templateId !in templateIds) {
                throw orphanChild("menu_template_entries", entry.id, "templateId", entry.templateId)
            }
            requireCanonicalTemplateEntry(entry)
            requireNullTemplateTaskRefs(entry)
            val encoded = MenuFirestoreDocumentCodec.encodeMenuTemplateEntry(entry)
            assertEncodedIdMatches(entry.id, encoded)
            val slot = Triple(entry.templateId, entry.dayOfWeek, entry.mealType)
            if (!templateEntrySlots.add(slot)) {
                throw duplicateLogicalKey(
                    "menu_template_entries",
                    "${entry.templateId}/${entry.dayOfWeek}/${entry.mealType}",
                )
            }
        }

        val dailySlots = linkedSetOf<Pair<Long, Int>>()
        target.dailyAssignments.forEach { assignment ->
            requireCanonicalDailyAssignment(assignment)
            val encoded = MenuFirestoreDocumentCodec.encodeDailyAssignment(assignment)
            assertEncodedIdMatches(assignment.id, encoded)
            val slot = assignment.weekStartMillis to assignment.dayOfWeek
            if (!dailySlots.add(slot)) {
                throw duplicateLogicalKey(
                    "daily_assignments",
                    "${assignment.weekStartMillis}/${assignment.dayOfWeek}",
                )
            }
        }
    }

    private fun validateTemplateRecord(template: ExportMenuTemplate) {
        val encoded = MenuFirestoreDocumentCodec.encodeMenuTemplate(template)
        assertEncodedIdMatches(template.id, encoded)
    }

    private fun requireCanonicalWeeklyMenu(menu: ExportWeeklyMenu) {
        val canonicalId = MenuSyncIds.weeklyMenuId(menu.startDateMillis)
        if (menu.id != canonicalId) {
            throw MenuCloudUploadPlanException(
                "weekly_menus id ${menu.id} is not canonical for startDateMillis ${menu.startDateMillis}; expected $canonicalId",
            )
        }
    }

    private fun requireCanonicalMenuEntry(entry: ExportMenuEntry, weekStart: Long) {
        val canonicalId = MenuSyncIds.menuEntryId(weekStart, entry.dayOfWeek, entry.mealType)
        val canonicalMenuId = MenuSyncIds.weeklyMenuId(weekStart)
        if (entry.id != canonicalId) {
            throw MenuCloudUploadPlanException(
                "menu_entries id ${entry.id} is not canonical for slot $weekStart/${entry.dayOfWeek}/${entry.mealType}; expected $canonicalId",
            )
        }
        if (entry.menuId != canonicalMenuId) {
            throw MenuCloudUploadPlanException(
                "menu_entries ${entry.id} menuId ${entry.menuId} is not canonical for week $weekStart; expected $canonicalMenuId",
            )
        }
    }

    private fun requireCanonicalTemplateEntry(entry: ExportMenuTemplateEntry) {
        val canonicalId = MenuSyncIds.menuTemplateEntryId(entry.templateId, entry.dayOfWeek, entry.mealType)
        if (entry.id != canonicalId) {
            throw MenuCloudUploadPlanException(
                "menu_template_entries id ${entry.id} is not canonical for template ${entry.templateId}/${entry.dayOfWeek}/${entry.mealType}; expected $canonicalId",
            )
        }
    }

    private fun requireCanonicalDailyAssignment(assignment: ExportDailyAssignment) {
        val canonicalId = MenuSyncIds.dailyAssignmentId(assignment.weekStartMillis, assignment.dayOfWeek)
        if (assignment.id != canonicalId) {
            throw MenuCloudUploadPlanException(
                "daily_assignments id ${assignment.id} is not canonical for ${assignment.weekStartMillis}/${assignment.dayOfWeek}; expected $canonicalId",
            )
        }
    }

    private fun requireNullTemplateTaskRefs(entry: ExportMenuTemplateEntry) {
        if (entry.cookingTaskId != null || entry.prepTaskId != null) {
            throw MenuCloudUploadPlanException(
                "menu_template_entries ${entry.id} must not contain cookingTaskId or prepTaskId before upload",
            )
        }
    }

    private fun assertEncodedIdMatches(documentId: String, encoded: Map<String, Any?>) {
        val encodedId = encoded["id"]
        if (encodedId != documentId) {
            throw MenuCloudUploadPlanException(
                "encoded id $encodedId does not match document id $documentId",
            )
        }
    }

    private fun duplicateLogicalKey(collection: String, key: String): MenuCloudUploadPlanException =
        MenuCloudUploadPlanException("Duplicate logical key in $collection: $key")

    private fun orphanChild(
        collection: String,
        documentId: String,
        field: String,
        value: String,
    ): MenuCloudUploadPlanException = MenuCloudUploadPlanException(
        "$collection $documentId references missing parent $field=$value",
    )
}
