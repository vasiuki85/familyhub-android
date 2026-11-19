package com.familyhub.data.sync

import com.familyhub.data.datastore.encodeFamilyScopedDeletedEntry
import com.familyhub.data.datastore.normalizeFamilyScopedDeletedEntries
import com.familyhub.data.datastore.readDeletedDocumentIdsForFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyBindingTombstoneIsolationTest {

    @Test
    fun tombstonesForFamilyASurviveBindingTransitionToB() {
        val familyATombstone = encodeFamilyScopedDeletedEntry("family-a", "member-1")
        val familyBTombstone = encodeFamilyScopedDeletedEntry("family-b", "member-2")
        val entries = setOf(familyATombstone, familyBTombstone, "legacy-member")

        val normalizedForA = normalizeFamilyScopedDeletedEntries(entries, "family-a")

        assertEquals(
            setOf("member-1", "legacy-member"),
            readDeletedDocumentIdsForFamily(normalizedForA, "family-a")
        )
        assertEquals(setOf("member-2"), readDeletedDocumentIdsForFamily(normalizedForA, "family-b"))
        assertTrue(familyATombstone in normalizedForA)
        assertTrue(familyBTombstone in normalizedForA)
    }
}
