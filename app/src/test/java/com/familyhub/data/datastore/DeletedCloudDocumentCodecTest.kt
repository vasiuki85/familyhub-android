package com.familyhub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedCloudDocumentCodecTest {

    @Test
    fun encodeDecode_roundTripsDocument() {
        val document = DeletedCloudDocument(
            familyId = "family-1",
            collection = DeletedCloudDocumentCollections.TASKS,
            documentId = "task-1"
        )

        val encoded = encodeDeletedCloudDocument(document)
        assertEquals(document, decodeDeletedCloudDocument(encoded))
    }

    @Test
    fun encodeDecode_roundTripsAllAllowlistedCollections() {
        val collections = DeletedCloudDocumentCollections.STORAGE_ALLOWLIST
        collections.forEachIndexed { index, collection ->
            val document = DeletedCloudDocument(
                familyId = "family-$index",
                collection = collection,
                documentId = "doc-$index"
            )
            assertEquals(document, decodeDeletedCloudDocument(encodeDeletedCloudDocument(document)))
        }
    }

    @Test
    fun decode_rejectsMalformedEntries() {
        assertNull(decodeDeletedCloudDocument(""))
        assertNull(decodeDeletedCloudDocument("only-two${'\u001f'}parts"))
        assertNull(decodeDeletedCloudDocument("family${'\u001f'}unknown${'\u001f'}doc"))
        assertNull(decodeDeletedCloudDocument(" ${'\u001f'}tasks${'\u001f'}doc"))
        assertNull(decodeDeletedCloudDocument("family${'\u001f'}tasks${'\u001f'} "))
    }

    @Test
    fun encode_rejectsUnknownCollection() {
        val document = DeletedCloudDocument(
            familyId = "family-1",
            collection = "members",
            documentId = "member-1"
        )

        assertThrows(IllegalArgumentException::class.java) {
            encodeDeletedCloudDocument(document)
        }
    }

    @Test
    fun decodeDeletedCloudDocuments_filtersUnknownCollections() {
        val valid = encodeDeletedCloudDocument(
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.EVENTS, "event-1")
        )
        val invalid = "family-a${'\u001f'}members${'\u001f'}member-1"

        val decoded = decodeDeletedCloudDocuments(setOf(valid, invalid))

        assertEquals(1, decoded.size)
        assertEquals("event-1", decoded.single().documentId)
    }

    @Test
    fun familyIsolation_decodedEntriesFilterByFamilyId() {
        val entries = encodeDeletedCloudDocuments(
            setOf(
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-a"),
                DeletedCloudDocument("family-b", DeletedCloudDocumentCollections.TASKS, "task-b")
            )
        )

        val familyA = decodeDeletedCloudDocuments(entries).filter { it.familyId == "family-a" }.toSet()
        val familyB = decodeDeletedCloudDocuments(entries).filter { it.familyId == "family-b" }.toSet()

        assertEquals(setOf("task-a"), familyA.map { it.documentId }.toSet())
        assertEquals(setOf("task-b"), familyB.map { it.documentId }.toSet())
        assertTrue(familyA.none { it.familyId == "family-b" })
    }

    @Test
    fun encode_rejectsSeparatorInFamilyId() {
        val document = DeletedCloudDocument(
            familyId = "family${'\u001f'}1",
            collection = DeletedCloudDocumentCollections.TASKS,
            documentId = "task-1"
        )

        assertThrows(IllegalArgumentException::class.java) {
            encodeDeletedCloudDocument(document)
        }
    }

    @Test
    fun encode_rejectsSeparatorInDocumentId() {
        val document = DeletedCloudDocument(
            familyId = "family-1",
            collection = DeletedCloudDocumentCollections.TASKS,
            documentId = "task${'\u001f'}1"
        )

        assertThrows(IllegalArgumentException::class.java) {
            encodeDeletedCloudDocument(document)
        }
    }

    @Test
    fun retainDeletedDocumentEntriesAfterClearingFamily_preservesMalformedAndOtherFamilies() {
        val validFamilyA = encodeDeletedCloudDocument(
            DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-a")
        )
        val validFamilyB = encodeDeletedCloudDocument(
            DeletedCloudDocument("family-b", DeletedCloudDocumentCollections.EVENTS, "event-b")
        )
        val malformed = "family-a${'\u001f'}members${'\u001f'}member-1"
        val entries = setOf(validFamilyA, validFamilyB, malformed)

        val remaining = retainDeletedDocumentEntriesAfterClearingFamily(entries, "family-a")

        assertEquals(setOf(validFamilyB, malformed), remaining)
    }

    @Test
    fun groupDeletedDocumentIdsByCollection_groupsIdsPerCollection() {
        val grouped = groupDeletedDocumentIdsByCollection(
            setOf(
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.TASKS, "task-1"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.EVENTS, "event-1"),
                DeletedCloudDocument("family-a", DeletedCloudDocumentCollections.SHOPPING_ITEMS, "item-1")
            )
        )

        assertEquals(setOf("task-1"), grouped[DeletedCloudDocumentCollections.TASKS])
        assertEquals(setOf("event-1"), grouped[DeletedCloudDocumentCollections.EVENTS])
        assertEquals(setOf("item-1"), grouped[DeletedCloudDocumentCollections.SHOPPING_ITEMS])
    }
}
