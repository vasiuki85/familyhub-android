package com.familyhub.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class MenuSyncIdsTest {

    @Test
    fun weeklyMenuId_isStableAndFixedLength() {
        val a = MenuSyncIds.weeklyMenuId(1_700_000_000_000L)
        val b = MenuSyncIds.weeklyMenuId(1_700_000_000_000L)
        assertEquals(a, b)
        assertEquals(MenuSyncIds.CANONICAL_ID_LENGTH, a.length)
        assertTrue(a.startsWith("wm"))
        assertTrue(a.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun menuEntryId_differsBySlot() {
        val breakfast = MenuSyncIds.menuEntryId(1000L, 0, "BREAKFAST")
        val lunch = MenuSyncIds.menuEntryId(1000L, 0, "LUNCH")
        assertEquals(34, breakfast.length)
        assertTrue(breakfast.startsWith("me"))
        assertTrue(breakfast != lunch)
    }

    @Test
    fun templateEntryId_handles1500Utf8ByteTemplateId() {
        val templateId = "a".repeat(1500)
        assertEquals(1500, templateId.toByteArray(StandardCharsets.UTF_8).size)
        val id = MenuSyncIds.menuTemplateEntryId(templateId, 3, "DINNER")
        assertEquals(34, id.length)
        assertTrue(id.startsWith("te"))
    }

    @Test
    fun templateEntryId_rejectsOver1500Utf8Bytes() {
        val templateId = "a".repeat(1501)
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.menuTemplateEntryId(templateId, 0, "BREAKFAST")
        }
    }

    @Test
    fun crossType_sameNumericPayload_differentIds() {
        val week = MenuSyncIds.weeklyMenuId(42L)
        // daily assignment uses weekStart + day — still different entityType byte
        val daily = MenuSyncIds.dailyAssignmentId(42L, 0)
        assertTrue(week != daily)
        assertTrue(week.startsWith("wm"))
        assertTrue(daily.startsWith("da"))
    }

    @Test
    fun delimiterCollision_lengthPrefixedAvoidsAmbiguity() {
        // Naive join of "ab"+"c" vs "a"+"bc" would collide; length-prefix must not.
        val a = MenuSyncIds.menuTemplateEntryId("ab", 1, "BREAKFAST")
        val b = MenuSyncIds.menuTemplateEntryId("a", 1, "BREAKFAST")
        assertTrue(a != b)
    }

    @Test
    fun invalidDayOfWeek_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.menuEntryId(1L, 7, "BREAKFAST")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.dailyAssignmentId(1L, -1)
        }
    }

    @Test
    fun invalidMealType_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.menuEntryId(1L, 0, "brunch")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.menuEntryId(1L, 0, "BREAKFAST ")
        }
    }

    @Test
    fun invalidSurrogate_rejected() {
        val bad = "x\uD800y"
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.menuTemplateEntryId(bad, 0, "LUNCH")
        }
    }

    @Test
    fun reservedFirestoreId_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSyncIds.requireValidFirestoreDocumentId("__foo__")
        }
    }
}
