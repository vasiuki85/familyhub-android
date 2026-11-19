package com.familyhub.data.sync

import androidx.annotation.VisibleForTesting
import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportData

@VisibleForTesting
internal fun isDeletedCloudDocumentPresentLocally(local: ExportData, document: DeletedCloudDocument): Boolean {
    DeletedCloudDocumentCollections.requireStorageAllowed(document.collection)
    return when (document.collection) {
        DeletedCloudDocumentCollections.TASKS ->
            local.tasks.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.TASK_ROTATIONS ->
            local.taskRotations.any { it.taskId == document.documentId }
        DeletedCloudDocumentCollections.SHOPPING_LISTS ->
            local.shoppingLists.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.SHOPPING_CATEGORIES ->
            local.shoppingCategories.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.SHOPPING_ITEMS ->
            local.shoppingItems.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.EVENTS ->
            local.events.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.REWARDS ->
            local.rewards.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.HOUSE_RULES ->
            local.houseRules.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.TASK_TEMPLATES ->
            local.taskTemplates.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.TASK_CATEGORIES ->
            local.taskCategories.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.APPRECIATIONS ->
            local.appreciations.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.FAMILY_GOALS ->
            local.familyGoals.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES ->
            local.familyGoalStages.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS ->
            local.familyGoalSteps.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.EMERGENCY_CONTACTS ->
            local.emergencyContacts.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.WEEKLY_MENUS ->
            local.weeklyMenus.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.MENU_ENTRIES ->
            local.menuEntries.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.MENU_TEMPLATES ->
            local.menuTemplates.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES ->
            local.menuTemplateEntries.any { it.id == document.documentId }
        DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS ->
            local.dailyAssignments.any { it.id == document.documentId }
        else -> error("Unhandled storage collection: ${document.collection}")
    }
}

@VisibleForTesting
internal fun remoteIdsForRemoteActiveCollection(
    snapshot: ExportData,
    collection: String
): Set<String> {
    require(collection in DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS) {
        "Collection is not remote-active: $collection"
    }
    return when (collection) {
        DeletedCloudDocumentCollections.TASKS ->
            snapshot.tasks.map { it.id }.toSet()
        DeletedCloudDocumentCollections.TASK_ROTATIONS ->
            snapshot.taskRotations.map { it.taskId }.toSet()
        DeletedCloudDocumentCollections.SHOPPING_LISTS ->
            snapshot.shoppingLists.map { it.id }.toSet()
        DeletedCloudDocumentCollections.SHOPPING_CATEGORIES ->
            snapshot.shoppingCategories.map { it.id }.toSet()
        DeletedCloudDocumentCollections.SHOPPING_ITEMS ->
            snapshot.shoppingItems.map { it.id }.toSet()
        DeletedCloudDocumentCollections.EVENTS ->
            snapshot.events.map { it.id }.toSet()
        DeletedCloudDocumentCollections.REWARDS ->
            snapshot.rewards.map { it.id }.toSet()
        DeletedCloudDocumentCollections.HOUSE_RULES ->
            snapshot.houseRules.map { it.id }.toSet()
        DeletedCloudDocumentCollections.TASK_TEMPLATES ->
            snapshot.taskTemplates.map { it.id }.toSet()
        DeletedCloudDocumentCollections.TASK_CATEGORIES ->
            snapshot.taskCategories.map { it.id }.toSet()
        DeletedCloudDocumentCollections.APPRECIATIONS ->
            snapshot.appreciations.map { it.id }.toSet()
        DeletedCloudDocumentCollections.FAMILY_GOALS ->
            snapshot.familyGoals.map { it.id }.toSet()
        DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES ->
            snapshot.familyGoalStages.map { it.id }.toSet()
        DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS ->
            snapshot.familyGoalSteps.map { it.id }.toSet()
        DeletedCloudDocumentCollections.EMERGENCY_CONTACTS ->
            snapshot.emergencyContacts.map { it.id }.toSet()
        DeletedCloudDocumentCollections.WEEKLY_MENUS ->
            snapshot.weeklyMenus.map { it.id }.toSet()
        DeletedCloudDocumentCollections.MENU_ENTRIES ->
            snapshot.menuEntries.map { it.id }.toSet()
        DeletedCloudDocumentCollections.MENU_TEMPLATES ->
            snapshot.menuTemplates.map { it.id }.toSet()
        DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES ->
            snapshot.menuTemplateEntries.map { it.id }.toSet()
        DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS ->
            snapshot.dailyAssignments.map { it.id }.toSet()
        else -> error("Unhandled remote-active collection: $collection")
    }
}

@Deprecated("Use remoteIdsForRemoteActiveCollection for remote confirmation paths")
@VisibleForTesting
internal fun remoteIdsForDeletedCloudDocumentCollection(
    snapshot: ExportData,
    collection: String
): Set<String> = remoteIdsForRemoteActiveCollection(snapshot, collection)

@VisibleForTesting
internal fun deletedCloudDocumentsConfirmedAbsentOnRemote(
    remoteSnapshot: ExportData,
    deletedDocuments: Set<DeletedCloudDocument>
): Set<DeletedCloudDocument> {
    if (deletedDocuments.isEmpty()) return emptySet()
    val remoteActiveDocuments = deletedDocuments.filter {
        DeletedCloudDocumentCollections.isPreMergeConfirmable(it.collection)
    }
    return remoteActiveDocuments.filter { document ->
        document.documentId !in remoteIdsForRemoteActiveCollection(remoteSnapshot, document.collection)
    }.toSet()
}

@VisibleForTesting
internal fun deletedCloudDocumentsToReconcile(
    localSnapshot: ExportData,
    deletedDocuments: Set<DeletedCloudDocument>
): Set<DeletedCloudDocument> {
    if (deletedDocuments.isEmpty()) return emptySet()
    return deletedDocuments.filter { document ->
        isDeletedCloudDocumentPresentLocally(localSnapshot, document)
    }.toSet()
}
