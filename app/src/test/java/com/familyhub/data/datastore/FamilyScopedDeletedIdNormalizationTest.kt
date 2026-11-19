package com.familyhub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyScopedDeletedIdNormalizationTest {

    @Test
    fun enableSameFamily_migratesRawMemberAndRecipeIdsToStoredAccount() {
        val storedAccount = "family-a"
        val members = normalizeFamilyScopedDeletedEntries(setOf("member-1"), storedAccount)
        val recipes = normalizeFamilyScopedDeletedEntries(setOf("recipe-1"), storedAccount)

        assertEquals(setOf(encodeFamilyScopedDeletedEntry(storedAccount, "member-1")), members)
        assertEquals(setOf(encodeFamilyScopedDeletedEntry(storedAccount, "recipe-1")), recipes)
        assertEquals(setOf("member-1"), readDeletedDocumentIdsForFamily(members, storedAccount))
        assertEquals(setOf("recipe-1"), readDeletedDocumentIdsForFamily(recipes, storedAccount))
    }

    @Test
    fun firstReadForStoredAccount_migratesRawMemberWithoutPriorEnable() {
        val storedAccount = "family-a"
        val (normalized, ids) = readNormalizedDeletedDocumentIdsForFamily(
            entries = setOf("member-1"),
            storedAccountId = storedAccount,
            familyId = storedAccount
        )

        assertTrue(familyScopedDeletedEntriesChanged(setOf("member-1"), normalized))
        assertEquals(setOf(encodeFamilyScopedDeletedEntry(storedAccount, "member-1")), normalized)
        assertEquals(setOf("member-1"), ids)
    }

    @Test
    fun firstReadForStoredAccount_migratesRawRecipeWithoutPriorEnable() {
        val storedAccount = "family-a"
        val (normalized, ids) = readNormalizedDeletedDocumentIdsForFamily(
            entries = setOf("recipe-1"),
            storedAccountId = storedAccount,
            familyId = storedAccount
        )

        assertTrue(familyScopedDeletedEntriesChanged(setOf("recipe-1"), normalized))
        assertEquals(setOf(encodeFamilyScopedDeletedEntry(storedAccount, "recipe-1")), normalized)
        assertEquals(setOf("recipe-1"), ids)
    }

    @Test
    fun removeDeletedMember_migratesRawIdThenClearsItBeforeAccountSwitch() {
        val storedAccount = "family-a"
        val rawEntries = setOf("member-1")

        val afterRemove = removeDeletedDocumentIdForFamily(rawEntries, storedAccount, storedAccount, "member-1")
        assertTrue(afterRemove.isEmpty())

        val afterSwitchToB = normalizeFamilyScopedDeletedEntries(afterRemove, storedAccountId = storedAccount)
        assertTrue(afterSwitchToB.isEmpty())
        assertTrue(readDeletedDocumentIdsForFamily(afterSwitchToB, "family-b").isEmpty())
    }

    @Test
    fun removeDeletedRecipeForCurrentFamily_removesLegacyRawId() {
        val storedAccount = "family-a"
        val rawEntries = setOf("recipe-1")

        val afterRemove = removeDeletedDocumentIdForFamily(rawEntries, storedAccount, storedAccount, "recipe-1")

        assertTrue(afterRemove.isEmpty())
        assertFalse(rawEntries.single() in afterRemove)
    }

    @Test
    fun clearDeletedMembers_afterNormalizationClearsOnlyRequestedFamily() {
        val familyA = encodeFamilyScopedDeletedEntry("family-a", "member-a")
        val familyB = encodeFamilyScopedDeletedEntry("family-b", "member-b")
        val malformed = "family-a\u001f\u001fmember"
        val entries = setOf(familyA, familyB, "member-legacy", malformed)

        val cleared = clearDeletedDocumentIdsForFamily(entries, storedAccountId = "family-a", familyId = "family-a")

        assertEquals(setOf(familyB, malformed), cleared)
        assertTrue(familyA !in cleared)
        assertTrue("member-legacy" !in cleared)
        assertTrue(malformed in cleared)
    }

    @Test
    fun clearDeletedRecipes_afterNormalizationClearsOnlyRequestedFamily() {
        val familyA = encodeFamilyScopedDeletedEntry("family-a", "recipe-a")
        val familyB = encodeFamilyScopedDeletedEntry("family-b", "recipe-b")
        val malformed = "family-a\u001f\u001frecipe"
        val entries = setOf(familyA, familyB, "recipe-legacy", malformed)

        val cleared = clearDeletedDocumentIdsForFamily(entries, storedAccountId = "family-a", familyId = "family-a")

        assertEquals(setOf(familyB, malformed), cleared)
    }

    @Test
    fun storedAccountOperationsForOtherFamily_doNotAssignLegacyEntriesToArgumentFamily() {
        val storedAccount = "family-a"
        val legacyEntries = setOf("member-legacy")

        val normalized = normalizeFamilyScopedDeletedEntries(legacyEntries, storedAccount)
        assertEquals(setOf("member-legacy"), readDeletedDocumentIdsForFamily(normalized, storedAccount))
        assertTrue(readDeletedDocumentIdsForFamily(normalized, "family-b").isEmpty())

        val afterAddToB = addDeletedDocumentIdForFamily(legacyEntries, storedAccount, "family-b", "member-b")
        assertEquals(
            setOf("member-legacy"),
            readDeletedDocumentIdsForFamily(afterAddToB, storedAccount)
        )
        assertEquals(setOf("member-b"), readDeletedDocumentIdsForFamily(afterAddToB, "family-b"))

        val afterRemoveFromB = removeDeletedDocumentIdForFamily(afterAddToB, storedAccount, "family-b", "member-b")
        assertEquals(
            setOf(encodeFamilyScopedDeletedEntry(storedAccount, "member-legacy")),
            normalizeFamilyScopedDeletedEntries(afterRemoveFromB, storedAccount)
        )
    }

    @Test
    fun withoutStoredAccount_rawIdsAreDroppedAndMalformedPreserved() {
        val malformed = "family-a\u001f\u001fmember"
        val entries = setOf("member-1", "recipe-1", malformed)

        val normalized = normalizeFamilyScopedDeletedEntries(entries, storedAccountId = null)

        assertEquals(setOf(malformed), normalized)
        assertTrue("member-1" !in normalized)
        assertTrue("recipe-1" !in normalized)
    }

    @Test
    fun accountSwitchToB_stillBindsLegacyRawIdsToPreviousFamilyA() {
        val legacy = setOf("member-1")
        val boundToA = normalizeFamilyScopedDeletedEntries(legacy, storedAccountId = "family-a")

        assertEquals(setOf("member-1"), readDeletedDocumentIdsForFamily(boundToA, "family-a"))
        assertTrue(readDeletedDocumentIdsForFamily(boundToA, "family-b").isEmpty())
    }

    @Test
    fun normalization_isIdempotent() {
        val entries = setOf("member-legacy", encodeFamilyScopedDeletedEntry("family-a", "member-scoped"))
        val once = normalizeFamilyScopedDeletedEntries(entries, storedAccountId = "family-a")
        val twice = normalizeFamilyScopedDeletedEntries(once, storedAccountId = "family-b")

        assertEquals(once, twice)
    }

    @Test
    fun removeForOtherFamily_doesNotClearLegacyBoundToStoredAccount() {
        val storedAccount = "family-a"
        val entries = setOf("member-1")

        val afterRemoveForB = removeDeletedDocumentIdForFamily(entries, storedAccount, "family-b", "member-1")

        assertEquals(
            setOf(encodeFamilyScopedDeletedEntry(storedAccount, "member-1")),
            afterRemoveForB
        )
    }
}
