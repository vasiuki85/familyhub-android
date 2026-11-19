package com.familyhub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedCloudDocumentCollectionsTest {

    @Test
    fun preMergeConfirmable_containsExactlyFifteenNonMenuCollections() {
        val expected = setOf(
            "tasks",
            "task_rotations",
            "shopping_lists",
            "shopping_categories",
            "shopping_items",
            "events",
            "rewards",
            "house_rules",
            "task_templates",
            "task_categories",
            "appreciations",
            "family_goals",
            "family_goal_stages",
            "family_goal_steps",
            "emergency_contacts"
        )

        assertEquals(15, DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS.size)
        assertEquals(expected, DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS)
    }

    @Test
    fun remoteActive_containsPreMergePlusMenuCollections() {
        assertEquals(20, DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS.size)
        assertTrue(
            DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS.containsAll(
                DeletedCloudDocumentCollections.PRE_MERGE_CONFIRMABLE_COLLECTIONS
            )
        )
        assertTrue(
            DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS.containsAll(
                DeletedCloudDocumentCollections.MENU_COLLECTIONS
            )
        )
        assertEquals(5, DeletedCloudDocumentCollections.MENU_COLLECTIONS.size)
    }

    @Test
    fun storageAllowlist_matchesRemoteActive() {
        assertEquals(20, DeletedCloudDocumentCollections.STORAGE_ALLOWLIST.size)
        assertEquals(
            DeletedCloudDocumentCollections.REMOTE_ACTIVE_COLLECTIONS,
            DeletedCloudDocumentCollections.STORAGE_ALLOWLIST,
        )
    }

    @Test
    fun menuCollections_areRemoteActive() {
        DeletedCloudDocumentCollections.MENU_COLLECTIONS.forEach { collection ->
            assertTrue(DeletedCloudDocumentCollections.isRemoteActive(collection))
            assertFalse(DeletedCloudDocumentCollections.isPreMergeConfirmable(collection))
            assertTrue(DeletedCloudDocumentCollections.isStorageAllowed(collection))
        }
    }
}
