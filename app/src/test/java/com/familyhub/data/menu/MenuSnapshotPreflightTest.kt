package com.familyhub.data.menu

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import org.junit.Assert.assertThrows
import org.junit.Test

class MenuSnapshotPreflightTest {

    @Test
    fun duplicateRawIds_rejectedForAllFiveCollections() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuTemplates = listOf(
                        ExportMenuTemplate(id = "t", name = "A", createdAt = 1L),
                        ExportMenuTemplate(id = "t", name = "B", createdAt = 2L)
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuTemplates = listOf(ExportMenuTemplate(id = "tpl", name = "A", createdAt = 1L)),
                    menuTemplateEntries = listOf(
                        ExportMenuTemplateEntry(
                            id = "te",
                            templateId = "tpl",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1
                        ),
                        ExportMenuTemplateEntry(
                            id = "te",
                            templateId = "tpl",
                            dayOfWeek = 1,
                            mealType = "LUNCH",
                            servings = 1
                        )
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    weeklyMenus = listOf(
                        ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L),
                        ExportWeeklyMenu(id = "wm", startDateMillis = 2L, createdAt = 2L)
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    weeklyMenus = listOf(
                        ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L)
                    ),
                    menuEntries = listOf(
                        ExportMenuEntry(
                            id = "me",
                            menuId = "wm",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1
                        ),
                        ExportMenuEntry(
                            id = "me",
                            menuId = "wm",
                            dayOfWeek = 1,
                            mealType = "LUNCH",
                            servings = 1
                        )
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    dailyAssignments = listOf(
                        ExportDailyAssignment(
                            id = "da",
                            weekStartMillis = 1L,
                            dayOfWeek = 0,
                            assignedMemberId = "a"
                        ),
                        ExportDailyAssignment(
                            id = "da",
                            weekStartMillis = 1L,
                            dayOfWeek = 1,
                            assignedMemberId = "b"
                        )
                    )
                )
            )
        }
    }

    @Test
    fun duplicateRawWeeklyParentId_acrossDifferentWeeks_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    weeklyMenus = listOf(
                        ExportWeeklyMenu(id = "same", startDateMillis = 1000L, createdAt = 1L),
                        ExportWeeklyMenu(id = "same", startDateMillis = 2000L, createdAt = 2L)
                    ),
                    menuEntries = listOf(
                        ExportMenuEntry(
                            id = "child",
                            menuId = "same",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1
                        )
                    )
                )
            )
        }
    }

    @Test
    fun orphanMenuEntry_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuEntries = listOf(
                        ExportMenuEntry(
                            id = "orphan",
                            menuId = "missing",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1
                        )
                    )
                )
            )
        }
    }

    @Test
    fun orphanTemplateEntry_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuTemplateEntries = listOf(
                        ExportMenuTemplateEntry(
                            id = "orphan",
                            templateId = "missing",
                            dayOfWeek = 0,
                            mealType = "LUNCH",
                            servings = 1
                        )
                    )
                )
            )
        }
    }

    @Test
    fun negativeUpdatedAt_rejectedForAllFiveCollections() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuTemplates = listOf(
                        ExportMenuTemplate(id = "t", name = "A", createdAt = 1L, updatedAt = -1L)
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    menuTemplates = listOf(ExportMenuTemplate(id = "tpl", name = "A", createdAt = 1L)),
                    menuTemplateEntries = listOf(
                        ExportMenuTemplateEntry(
                            id = "te",
                            templateId = "tpl",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1,
                            updatedAt = -1L
                        )
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    weeklyMenus = listOf(
                        ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L, updatedAt = -1L)
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    weeklyMenus = listOf(
                        ExportWeeklyMenu(id = "wm", startDateMillis = 1L, createdAt = 1L)
                    ),
                    menuEntries = listOf(
                        ExportMenuEntry(
                            id = "me",
                            menuId = "wm",
                            dayOfWeek = 0,
                            mealType = "BREAKFAST",
                            servings = 1,
                            updatedAt = -2L
                        )
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuSnapshotPreflight.validate(
                ExportData(
                    dailyAssignments = listOf(
                        ExportDailyAssignment(
                            id = "da",
                            weekStartMillis = 1L,
                            dayOfWeek = 0,
                            updatedAt = -1L
                        )
                    )
                )
            )
        }
    }

    @Test
    fun distinctRawIdsSameLogicalSlot_allowedByPreflight() {
        MenuSnapshotPreflight.validate(
            ExportData(
                weeklyMenus = listOf(
                    ExportWeeklyMenu(id = "a", startDateMillis = 1000L, createdAt = 1L),
                    ExportWeeklyMenu(id = "b", startDateMillis = 1000L, createdAt = 2L)
                ),
                menuEntries = listOf(
                    ExportMenuEntry(
                        id = "e1",
                        menuId = "a",
                        dayOfWeek = 0,
                        mealType = "BREAKFAST",
                        servings = 1
                    ),
                    ExportMenuEntry(
                        id = "e2",
                        menuId = "b",
                        dayOfWeek = 0,
                        mealType = "BREAKFAST",
                        servings = 2
                    )
                )
            )
        )
    }
}
