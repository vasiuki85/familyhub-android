package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportAppreciation
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportEmergencyContact
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.data.export.ExportEvent
import com.familyhub.data.export.ExportFamilyGoal
import com.familyhub.data.export.ExportFamilyGoalStage
import com.familyhub.data.export.ExportFamilyGoalStep
import com.familyhub.data.export.ExportHouseRule
import com.familyhub.data.export.ExportReward
import com.familyhub.data.export.ExportShoppingCategory
import com.familyhub.data.export.ExportShoppingItem
import com.familyhub.data.export.ExportShoppingList
import com.familyhub.data.export.ExportTask
import com.familyhub.data.export.ExportTaskCategory
import com.familyhub.data.export.ExportTaskRotation
import com.familyhub.data.export.ExportTaskTemplate
import com.familyhub.data.sync.deletedCloudDocumentsConfirmedAbsentOnRemote
import com.familyhub.data.sync.deletedCloudDocumentsToReconcile
import com.familyhub.data.sync.isDeletedCloudDocumentPresentLocally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedCloudDocumentSyncTest {

    @Test
    fun reconcile_detectsLocallyRecreatedDocumentsAcrossCollections() {
        val local = ExportData(
            tasks = listOf(ExportTask(id = "task-1", title = "Back")),
            shoppingLists = listOf(
                ExportShoppingList(
                    id = "list-1",
                    title = "List",
                    colorHex = "#fff",
                    iconName = "cart",
                    createdAt = 1L
                )
            ),
            shoppingCategories = listOf(
                ExportShoppingCategory(
                    id = "cat-1",
                    listId = "list-1",
                    title = "Cat",
                    colorHex = "#fff",
                    iconName = "icon",
                    sortOrder = 0
                )
            ),
            shoppingItems = listOf(
                ExportShoppingItem(
                    id = "item-1",
                    listId = "list-1",
                    title = "Milk",
                    createdAt = 1L
                )
            ),
            events = listOf(ExportEvent(id = "event-1", title = "Party", startDateTime = 1L))
        )
        val tombstones = setOf(
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_LISTS, "list-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_CATEGORIES, "cat-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_ITEMS, "item-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.EVENTS, "event-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-missing")
        )

        val toReconcile = deletedCloudDocumentsToReconcile(local, tombstones)

        assertEquals(5, toReconcile.size)
        assertTrue(toReconcile.none { it.documentId == "task-missing" })
    }

    @Test
    fun confirm_confirmsOnlyDocumentsAbsentOnRemote() {
        val remote = ExportData(
            tasks = listOf(ExportTask(id = "task-still-there", title = "Remote")),
            events = emptyList()
        )
        val tombstones = setOf(
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-still-there"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.EVENTS, "event-gone")
        )

        val confirmed = deletedCloudDocumentsConfirmedAbsentOnRemote(remote, tombstones)

        assertEquals(
            setOf("event-gone"),
            confirmed.map { it.documentId }.toSet()
        )
    }

    @Test
    fun reconcileAndConfirm_coverEveryPreMergeConfirmableCollection() {
        val snapshot = snapshotWithEveryGenericDocument()
        val tombstones = DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS.map { collection ->
            DeletedCloudDocument("family-a", collection, collection)
        }.toSet()

        assertEquals(tombstones, deletedCloudDocumentsToReconcile(snapshot, tombstones))
        assertTrue(deletedCloudDocumentsConfirmedAbsentOnRemote(snapshot, tombstones).isEmpty())
        assertEquals(
            tombstones,
            deletedCloudDocumentsConfirmedAbsentOnRemote(ExportData(), tombstones)
        )
    }

    @Test
    fun menuTombstones_useLogicalPresenceNotGenericPreMergeConfirmation() {
        val weeklyTombstone = DeletedCloudDocument(
            "family-a",
            DeletedCloudDocumentCollections.WEEKLY_MENUS,
            "wm-1",
        )
        assertTrue(
            deletedCloudDocumentsConfirmedAbsentOnRemote(
                ExportData(weeklyMenus = listOf(ExportWeeklyMenu(id = "wm-1"))),
                setOf(weeklyTombstone),
            ).isEmpty()
        )
        assertTrue(
            menuTombstonesConfirmedAbsentOnRawRemote(
                rawRemote = ExportData().toMenuCloudSnapshot(),
                tombstones = setOf(weeklyTombstone),
            ).contains(weeklyTombstone)
        )
    }

    @Test
    fun reconcile_unknownStorageCollection_failsClosed() {
        val document = DeletedCloudDocument(
            familyId = "family-a",
            collection = "future_menu_collection",
            documentId = "doc-1",
        )

        val error = runCatching {
            isDeletedCloudDocumentPresentLocally(ExportData(), document)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun reconcile_detectsLocallyRecreatedMenuDocuments() {
        val local = ExportData(
            weeklyMenus = listOf(ExportWeeklyMenu(id = "wm-1")),
            menuEntries = listOf(ExportMenuEntry(id = "me-1")),
            menuTemplates = listOf(ExportMenuTemplate(id = "mt-1")),
            menuTemplateEntries = listOf(ExportMenuTemplateEntry(id = "mte-1")),
            dailyAssignments = listOf(ExportDailyAssignment(id = "da-1")),
        )
        val tombstones = setOf(
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.WEEKLY_MENUS, "wm-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.MENU_ENTRIES, "me-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.MENU_TEMPLATES, "mt-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES, "mte-1"),
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS, "da-1"),
        )

        assertEquals(tombstones, deletedCloudDocumentsToReconcile(local, tombstones))
    }

    private fun snapshotWithEveryGenericDocument() = ExportData(
        tasks = listOf(ExportTask(id = DeletedCloudDocumentCollections.TASKS)),
        taskRotations = listOf(ExportTaskRotation(taskId = DeletedCloudDocumentCollections.TASK_ROTATIONS)),
        shoppingLists = listOf(ExportShoppingList(id = DeletedCloudDocumentCollections.SHOPPING_LISTS)),
        shoppingCategories = listOf(
            ExportShoppingCategory(id = DeletedCloudDocumentCollections.SHOPPING_CATEGORIES)
        ),
        shoppingItems = listOf(ExportShoppingItem(id = DeletedCloudDocumentCollections.SHOPPING_ITEMS)),
        events = listOf(ExportEvent(id = DeletedCloudDocumentCollections.EVENTS)),
        rewards = listOf(ExportReward(id = DeletedCloudDocumentCollections.REWARDS)),
        houseRules = listOf(ExportHouseRule(id = DeletedCloudDocumentCollections.HOUSE_RULES)),
        taskTemplates = listOf(ExportTaskTemplate(id = DeletedCloudDocumentCollections.TASK_TEMPLATES)),
        taskCategories = listOf(ExportTaskCategory(id = DeletedCloudDocumentCollections.TASK_CATEGORIES)),
        appreciations = listOf(ExportAppreciation(id = DeletedCloudDocumentCollections.APPRECIATIONS)),
        familyGoals = listOf(ExportFamilyGoal(id = DeletedCloudDocumentCollections.FAMILY_GOALS)),
        familyGoalStages = listOf(
            ExportFamilyGoalStage(id = DeletedCloudDocumentCollections.FAMILY_GOAL_STAGES)
        ),
        familyGoalSteps = listOf(
            ExportFamilyGoalStep(id = DeletedCloudDocumentCollections.FAMILY_GOAL_STEPS)
        ),
        emergencyContacts = listOf(
            ExportEmergencyContact(id = DeletedCloudDocumentCollections.EMERGENCY_CONTACTS)
        )
    )
}
