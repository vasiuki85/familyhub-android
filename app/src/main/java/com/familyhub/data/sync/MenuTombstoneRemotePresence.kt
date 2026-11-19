package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.menu.cloud.MenuCloudRemoteIdKind
import com.familyhub.data.menu.cloud.MenuCloudSnapshot
import com.familyhub.data.menu.cloud.MenuCloudSnapshotProcessor
import com.familyhub.data.menu.cloud.MenuCloudTombstone

internal enum class MenuTombstoneRemotePresence {
    PRESENT,
    ABSENT,
    AMBIGUOUS,
}

internal data class MenuTombstonePresenceBatch(
    val rawRemote: MenuCloudSnapshot,
    val classifications: List<com.familyhub.data.menu.cloud.MenuCloudRemoteIdClassification>,
    val documentIndex: com.familyhub.data.menu.cloud.MenuCloudDocumentIndex,
)

internal fun buildMenuTombstonePresenceBatch(rawRemote: MenuCloudSnapshot): MenuTombstonePresenceBatch? {
    return try {
        val processed = MenuCloudSnapshotProcessor.process(rawRemote)
        MenuTombstonePresenceBatch(
            rawRemote = rawRemote,
            classifications = processed.classifications,
            documentIndex = processed.documentIndex,
        )
    } catch (_: Exception) {
        null
    }
}

internal fun evaluateMenuTombstoneRemotePresence(
    tombstone: MenuCloudTombstone,
    batch: MenuTombstonePresenceBatch,
): MenuTombstoneRemotePresence {
    if (hasExactRawDocumentId(tombstone, batch.rawRemote)) {
        return MenuTombstoneRemotePresence.PRESENT
    }

    val index = batch.documentIndex
    val logicalPresent = when (tombstone.collection) {
        DeletedCloudDocumentCollections.WEEKLY_MENUS -> {
            val week = index.weeklyMenuIdToWeek[tombstone.documentId] ?: return ambiguousOrAbsent(
                tombstone.collection,
                batch.classifications,
            )
            batch.rawRemote.weeklyMenus.any { it.startDateMillis == week }
        }
        DeletedCloudDocumentCollections.MENU_ENTRIES -> {
            val slot = index.menuEntryIdToSlot[tombstone.documentId] ?: return ambiguousOrAbsent(
                tombstone.collection,
                batch.classifications,
            )
            batch.rawRemote.menuEntries.any { entry ->
                val week = index.weekForMenuId(entry.menuId) ?: return@any false
                week == slot.weekStartMillis &&
                    entry.dayOfWeek == slot.dayOfWeek &&
                    entry.mealType == slot.mealType
            }
        }
        DeletedCloudDocumentCollections.MENU_TEMPLATES -> {
            if (batch.rawRemote.menuTemplates.any { it.id == tombstone.documentId }) {
                true
            } else {
                tombstone.documentId in index.templateIds
            }
        }
        DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES -> {
            val slot = index.templateEntryIdToSlot[tombstone.documentId] ?: return ambiguousOrAbsent(
                tombstone.collection,
                batch.classifications,
            )
            batch.rawRemote.menuTemplateEntries.any { entry ->
                entry.templateId == slot.templateId &&
                    entry.dayOfWeek == slot.dayOfWeek &&
                    entry.mealType == slot.mealType
            }
        }
        DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS -> {
            val slot = index.dailyAssignmentIdToSlot[tombstone.documentId] ?: return ambiguousOrAbsent(
                tombstone.collection,
                batch.classifications,
            )
            batch.rawRemote.dailyAssignments.any { assignment ->
                assignment.weekStartMillis == slot.weekStartMillis &&
                    assignment.dayOfWeek == slot.dayOfWeek
            }
        }
        else -> return MenuTombstoneRemotePresence.AMBIGUOUS
    }

    return if (logicalPresent) {
        MenuTombstoneRemotePresence.PRESENT
    } else {
        ambiguousOrAbsent(tombstone.collection, batch.classifications)
    }
}

private fun ambiguousOrAbsent(
    collection: String,
    classifications: List<com.familyhub.data.menu.cloud.MenuCloudRemoteIdClassification>,
): MenuTombstoneRemotePresence {
    val hasIgnoredOrphan = classifications.any {
        it.collection == collection && it.kind == MenuCloudRemoteIdKind.IGNORED_ORPHAN
    }
    return if (hasIgnoredOrphan) {
        MenuTombstoneRemotePresence.AMBIGUOUS
    } else {
        MenuTombstoneRemotePresence.ABSENT
    }
}

private fun hasExactRawDocumentId(
    tombstone: MenuCloudTombstone,
    rawRemote: MenuCloudSnapshot,
): Boolean {
    return when (tombstone.collection) {
        DeletedCloudDocumentCollections.WEEKLY_MENUS ->
            rawRemote.weeklyMenus.any { it.id == tombstone.documentId }
        DeletedCloudDocumentCollections.MENU_ENTRIES ->
            rawRemote.menuEntries.any { it.id == tombstone.documentId }
        DeletedCloudDocumentCollections.MENU_TEMPLATES ->
            rawRemote.menuTemplates.any { it.id == tombstone.documentId }
        DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES ->
            rawRemote.menuTemplateEntries.any { it.id == tombstone.documentId }
        DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS ->
            rawRemote.dailyAssignments.any { it.id == tombstone.documentId }
        else -> false
    }
}
