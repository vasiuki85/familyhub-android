package com.familyhub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyScopedDeletedIdCodecTest {

    @Test
    fun encodeDecode_roundTripsScopedId() {
        val id = FamilyScopedDeletedId(familyId = "family-a", documentId = "member-1")
        val encoded = encodeFamilyScopedDeletedId(id)

        assertEquals(id, decodeFamilyScopedDeletedId(encoded))
        assertEquals("family-a\u001fmember-1", encoded)
    }

    @Test
    fun encode_rejectsBlankFieldsAndSeparatorInFields() {
        assertThrows(IllegalArgumentException::class.java) {
            encodeFamilyScopedDeletedId(FamilyScopedDeletedId("", "doc"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeFamilyScopedDeletedId(FamilyScopedDeletedId("family", ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeFamilyScopedDeletedId(FamilyScopedDeletedId("family\u001fx", "doc"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeFamilyScopedDeletedId(FamilyScopedDeletedId("family", "doc\u001fy"))
        }
    }

    @Test
    fun decode_rejectsMalformedScopedEntries() {
        assertNull(decodeFamilyScopedDeletedId(""))
        assertNull(decodeFamilyScopedDeletedId("family\u001fdoc\u001fextra"))
        assertNull(decodeFamilyScopedDeletedId(" \u001fdoc"))
        assertNull(decodeFamilyScopedDeletedId("family\u001f "))
    }

    @Test
    fun familyIsolation_readReturnsOnlyRequestedFamily() {
        val entries = setOf(
            encodeFamilyScopedDeletedEntry("family-a", "member-a"),
            encodeFamilyScopedDeletedEntry("family-b", "member-b"),
            "member-legacy"
        )

        assertEquals(setOf("member-a"), readDeletedDocumentIdsForFamily(entries, "family-a"))
        assertEquals(setOf("member-b"), readDeletedDocumentIdsForFamily(entries, "family-b"))
        assertTrue(readDeletedDocumentIdsForFamily(entries, "family-c").isEmpty())
    }

    @Test
    fun malformedEntries_arePreservedButNeverRead() {
        val malformed = "family-a\u001f\u001fmember"
        val entries = setOf(
            encodeFamilyScopedDeletedEntry("family-a", "member-a"),
            malformed
        )

        assertEquals(setOf("member-a"), readDeletedDocumentIdsForFamily(entries, "family-a"))
        assertTrue(isMalformedFamilyScopedEntry(malformed))

        val migrated = migrateFamilyScopedDeletedEntries(entries, previousAccountId = "family-a")
        assertTrue(malformed in migrated)
    }

    @Test
    fun migration_matrix_scopedLegacyMalformed() {
        val scoped = encodeFamilyScopedDeletedEntry("family-a", "member-scoped")
        val legacy = "member-legacy"
        val malformed = "family-a\u001f\u001fmember"
        val entries = setOf(scoped, legacy, malformed)

        val migratedToA = migrateFamilyScopedDeletedEntries(entries, previousAccountId = "family-a")
        assertEquals(
            setOf(scoped, encodeFamilyScopedDeletedEntry("family-a", legacy), malformed),
            migratedToA
        )
        assertEquals(setOf("member-scoped", legacy), readDeletedDocumentIdsForFamily(migratedToA, "family-a"))

        val migratedWithoutPrevious = migrateFamilyScopedDeletedEntries(entries, previousAccountId = null)
        assertEquals(setOf(scoped, malformed), migratedWithoutPrevious)
        assertTrue(legacy !in migratedWithoutPrevious)
    }

    @Test
    fun migration_legacyRawIdsBindToPreviousFamilyOnAccountSwitch() {
        val legacyMember = "member-1"
        val legacyRecipe = "recipe-1"
        val memberEntries = setOf(legacyMember)
        val recipeEntries = setOf(legacyRecipe)

        val migratedMembers = migrateFamilyScopedDeletedEntries(memberEntries, previousAccountId = "family-a")
        val migratedRecipes = migrateFamilyScopedDeletedEntries(recipeEntries, previousAccountId = "family-a")

        assertEquals(setOf("member-1"), readDeletedDocumentIdsForFamily(migratedMembers, "family-a"))
        assertTrue(readDeletedDocumentIdsForFamily(migratedMembers, "family-b").isEmpty())
        assertEquals(setOf("recipe-1"), readDeletedDocumentIdsForFamily(migratedRecipes, "family-a"))
        assertTrue(readDeletedDocumentIdsForFamily(migratedRecipes, "family-b").isEmpty())
    }

    @Test
    fun migration_isIdempotent() {
        val entries = setOf("member-legacy", encodeFamilyScopedDeletedEntry("family-a", "member-scoped"))
        val once = migrateFamilyScopedDeletedEntries(entries, previousAccountId = "family-a")
        val twice = migrateFamilyScopedDeletedEntries(once, previousAccountId = "family-b")

        assertEquals(once, twice)
    }

    @Test
    fun retainFamilyScopedEntriesAfterClearingFamily_preservesOtherFamiliesAndMalformed() {
        val familyA = encodeFamilyScopedDeletedEntry("family-a", "member-a")
        val familyB = encodeFamilyScopedDeletedEntry("family-b", "member-b")
        val malformed = "family-a\u001f\u001fmember"
        val entries = setOf(familyA, familyB, malformed)

        val remaining = retainFamilyScopedEntriesAfterClearingFamily(entries, "family-a")

        assertEquals(setOf(familyB, malformed), remaining)
    }

    @Test
    fun recipeRecreateInFamilyBDoesNotClearFamilyATombstoneWithSameDocumentId() {
        val sharedId = "recipe-shared"
        val entries = setOf(encodeFamilyScopedDeletedEntry("family-a", sharedId))

        assertEquals(setOf(sharedId), readDeletedDocumentIdsForFamily(entries, "family-a"))
        assertTrue(readDeletedDocumentIdsForFamily(entries, "family-b").isEmpty())
    }

    @Test
    fun memberConfirmationScope_doesNotReadOtherFamilyTombstones() {
        val entries = setOf(
            encodeFamilyScopedDeletedEntry("family-a", "member-1"),
            encodeFamilyScopedDeletedEntry("family-b", "member-1")
        )

        val familyA = readDeletedDocumentIdsForFamily(entries, "family-a")
        val familyB = readDeletedDocumentIdsForFamily(entries, "family-b")

        assertEquals(setOf("member-1"), familyA)
        assertEquals(setOf("member-1"), familyB)
        assertEquals(
            setOf(encodeFamilyScopedDeletedEntry("family-a", "member-1")),
            entries.filter { decodeFamilyScopedDeletedId(it)?.familyId == "family-a" }.toSet()
        )
    }
}
