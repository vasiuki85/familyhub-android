package com.familyhub.data.menu

import com.familyhub.data.export.ExportData
import com.familyhub.domain.utils.MenuSyncIds

/**
 * Strict raw-menu preflight run **before** any canonicalization.
 *
 * Shared by JSON import and encrypted restore (.fhb) via
 * [MenuSnapshotCanonicalizer.canonicalize].
 *
 * Rules:
 * - raw ids unique within each of the five menu collections and Firestore-valid;
 * - parent relations must hold (orphans throw, never filtered);
 * - updatedAt: 0 = legacy, >0 kept later, <0 rejected;
 * - duplicate logical slots with distinct raw ids remain allowed for later collapse.
 */
object MenuSnapshotPreflight {

    fun validate(data: ExportData) {
        requireUniqueFirestoreIds("menuTemplates", data.menuTemplates.map { it.id })
        requireUniqueFirestoreIds("menuTemplateEntries", data.menuTemplateEntries.map { it.id })
        requireUniqueFirestoreIds("weeklyMenus", data.weeklyMenus.map { it.id })
        requireUniqueFirestoreIds("menuEntries", data.menuEntries.map { it.id })
        requireUniqueFirestoreIds("dailyAssignments", data.dailyAssignments.map { it.id })

        data.menuTemplates.forEach { requireNonNegativeUpdatedAt("menuTemplates", it.updatedAt) }
        data.menuTemplateEntries.forEach {
            requireNonNegativeUpdatedAt("menuTemplateEntries", it.updatedAt)
        }
        data.weeklyMenus.forEach { requireNonNegativeUpdatedAt("weeklyMenus", it.updatedAt) }
        data.menuEntries.forEach { requireNonNegativeUpdatedAt("menuEntries", it.updatedAt) }
        data.dailyAssignments.forEach {
            requireNonNegativeUpdatedAt("dailyAssignments", it.updatedAt)
        }

        val weeklyIds = data.weeklyMenus.map { it.id }.toSet()
        data.menuEntries.forEach { entry ->
            if (entry.menuId !in weeklyIds) {
                throw IllegalArgumentException(
                    "Orphan menu entry ${entry.id}: menuId=${entry.menuId} not in weeklyMenus"
                )
            }
        }

        val templateIds = data.menuTemplates.map { it.id }.toSet()
        data.menuTemplateEntries.forEach { entry ->
            if (entry.templateId !in templateIds) {
                throw IllegalArgumentException(
                    "Orphan menu template entry ${entry.id}: " +
                        "templateId=${entry.templateId} not in menuTemplates"
                )
            }
        }
    }

    private fun requireUniqueFirestoreIds(label: String, ids: List<String>) {
        val seen = HashSet<String>()
        ids.forEach { id ->
            if (id.isBlank()) {
                throw IllegalArgumentException("$label contains a blank id")
            }
            try {
                MenuSyncIds.requireValidFirestoreDocumentId(id)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid Firestore id in $label: $id", e)
            }
            if (!seen.add(id)) {
                throw IllegalArgumentException("Duplicate id in $label: $id")
            }
        }
    }

    private fun requireNonNegativeUpdatedAt(label: String, updatedAt: Long) {
        if (updatedAt < 0L) {
            throw IllegalArgumentException("$label updatedAt must be >= 0, got $updatedAt")
        }
    }
}
