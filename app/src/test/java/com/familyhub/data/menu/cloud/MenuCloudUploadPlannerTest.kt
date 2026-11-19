package com.familyhub.data.menu.cloud

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.utils.MenuSyncIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCloudUploadPlannerTest {

    private val weekStart = 1_700_000_000_000L
    private val weekB = weekStart + 604_800_000L
    private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)
    private val canonicalWeekBId = MenuSyncIds.weeklyMenuId(weekB)
    private val canonicalEntryId = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST")
    private val templateId = "menu-template-planner"

    @Test
    fun aliasBase_producesDeleteAliasAndSetCanonical() {
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = listOf(weeklyMenu(id = "alias-week", updatedAt = 10L)),
            menuEntries = listOf(
                menuEntry(id = "alias-entry", menuId = "alias-week", customDish = "toast", updatedAt = 5L),
            ),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val target = MenuCloudSnapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 10L)),
            menuEntries = listOf(
                menuEntry(
                    id = canonicalEntryId,
                    menuId = canonicalParentId,
                    customDish = "toast",
                    updatedAt = 5L,
                ),
            ),
        )

        val plan = plan(base, target, canUpload = true)

        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == "alias-week" })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == "alias-entry" })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Set && it.documentId == canonicalParentId })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Set && it.documentId == canonicalEntryId })
        assertFalse(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == canonicalParentId })
    }

    @Test
    fun canonicalLoserNewerAlias_setsCanonicalAndDeletesAliasOnly() {
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = listOf(
                weeklyMenu(id = canonicalParentId, updatedAt = 10L),
                weeklyMenu(id = "alias-week", updatedAt = 20L),
            ),
            menuEntries = emptyList(),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val target = MenuCloudSnapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 20L)),
            menuEntries = emptyList(),
        )

        val plan = plan(base, target, canUpload = true)

        assertTrue(plan.any { it is MenuCloudWriteOperation.Set && it.documentId == canonicalParentId })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == "alias-week" })
        assertFalse(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == canonicalParentId })
    }

    @Test
    fun readOnlyWriter_returnsEmptyPlan() {
        val plan = plan(
            base = MenuCloudRawRemoteBase.fromSnapshot(MenuCloudSnapshot.empty()),
            target = MenuCloudSnapshot(weeklyMenus = listOf(weeklyMenu())),
            canUpload = false,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun bootstrapNullBase_setsAllTargetDocs() {
        val target = MenuCloudSnapshot(weeklyMenus = listOf(weeklyMenu()))
        val plan = plan(base = null, target = target, canUpload = true)
        assertEquals(1, plan.size)
        assertTrue(plan.single() is MenuCloudWriteOperation.Set)
    }

    @Test
    fun ignoredOrphan_doesNotProduceDelete() {
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = emptyList(),
            menuEntries = listOf(menuEntry(id = "orphan-entry", menuId = "missing-parent")),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val plan = plan(
            base = base,
            target = MenuCloudSnapshot.empty(),
            canUpload = true,
            ignored = setOf(
                MenuCloudRemoteIdRef(DeletedCloudDocumentCollections.MENU_ENTRIES, "orphan-entry"),
            ),
        )
        assertFalse(plan.any { it is MenuCloudWriteOperation.Delete })
    }

    @Test
    fun unchangedCanonical_isNoOp() {
        val menu = weeklyMenu(id = canonicalParentId, updatedAt = 10L)
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = listOf(menu),
            menuEntries = emptyList(),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val plan = plan(base, MenuCloudSnapshot(weeklyMenus = listOf(menu)), canUpload = true)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun crossLogicalWeeklyCollision_throwsPlanException() {
        val error = assertThrows(MenuCloudUploadPlanException::class.java) {
            plan(
                base = MenuCloudRawRemoteBase(
                    weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, startDateMillis = weekB)),
                    menuEntries = emptyList(),
                    menuTemplates = emptyList(),
                    menuTemplateEntries = emptyList(),
                    dailyAssignments = emptyList(),
                ),
                target = MenuCloudSnapshot(
                    weeklyMenus = listOf(
                        weeklyMenu(id = canonicalParentId, startDateMillis = weekStart),
                        weeklyMenu(id = canonicalWeekBId, startDateMillis = weekB),
                    ),
                ),
                canUpload = true,
            )
        }
        assertTrue(error.message!!.contains("Conflicting upload operations"))
        assertTrue(error.message!!.contains(canonicalParentId))
    }

    @Test
    fun crossLogicalMenuEntryCollision_throwsPlanException() {
        val canonicalEntryWeekB = MenuSyncIds.menuEntryId(weekB, 0, "BREAKFAST")
        val error = assertThrows(MenuCloudUploadPlanException::class.java) {
            plan(
                base = MenuCloudRawRemoteBase(
                    weeklyMenus = listOf(weeklyMenu(id = canonicalWeekBId, startDateMillis = weekB)),
                    menuEntries = listOf(
                        menuEntry(
                            id = canonicalEntryId,
                            menuId = canonicalWeekBId,
                            updatedAt = 1L,
                        ),
                    ),
                    menuTemplates = emptyList(),
                    menuTemplateEntries = emptyList(),
                    dailyAssignments = emptyList(),
                ),
                target = MenuCloudSnapshot(
                    weeklyMenus = listOf(
                        weeklyMenu(id = canonicalParentId),
                        weeklyMenu(id = canonicalWeekBId, startDateMillis = weekB),
                    ),
                    menuEntries = listOf(
                        menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 2L),
                        menuEntry(id = canonicalEntryWeekB, menuId = canonicalWeekBId, updatedAt = 3L),
                    ),
                ),
                canUpload = true,
            )
        }
        assertTrue(error.message!!.contains("Conflicting upload operations"))
        assertTrue(error.message!!.contains(canonicalEntryId))
    }

    @Test
    fun crossLogicalTemplateEntryCollision_throwsPlanException() {
        val canonicalTemplateEntryA = MenuSyncIds.menuTemplateEntryId(templateId, 0, "LUNCH")
        val canonicalTemplateEntryB = MenuSyncIds.menuTemplateEntryId(templateId, 1, "LUNCH")
        val error = assertThrows(MenuCloudUploadPlanException::class.java) {
            plan(
                base = MenuCloudRawRemoteBase(
                    weeklyMenus = emptyList(),
                    menuEntries = emptyList(),
                    menuTemplates = listOf(template(templateId)),
                    menuTemplateEntries = listOf(
                        templateEntry(
                            id = canonicalTemplateEntryA,
                            templateId = templateId,
                            dayOfWeek = 1,
                            mealType = "LUNCH",
                        ),
                    ),
                    dailyAssignments = emptyList(),
                ),
                target = MenuCloudSnapshot(
                    menuTemplates = listOf(template(templateId)),
                    menuTemplateEntries = listOf(
                        templateEntry(
                            id = canonicalTemplateEntryA,
                            templateId = templateId,
                            dayOfWeek = 0,
                            mealType = "LUNCH",
                        ),
                        templateEntry(
                            id = canonicalTemplateEntryB,
                            templateId = templateId,
                            dayOfWeek = 1,
                            mealType = "LUNCH",
                        ),
                    ),
                ),
                canUpload = true,
            )
        }
        assertTrue(error.message!!.contains("Conflicting upload operations"))
        assertTrue(error.message!!.contains(canonicalTemplateEntryA))
    }

    @Test
    fun crossLogicalDailyAssignmentCollision_throwsPlanException() {
        val canonicalDailyA = MenuSyncIds.dailyAssignmentId(weekStart, 0)
        val canonicalDailyB = MenuSyncIds.dailyAssignmentId(weekB, 0)
        val error = assertThrows(MenuCloudUploadPlanException::class.java) {
            plan(
                base = MenuCloudRawRemoteBase(
                    weeklyMenus = emptyList(),
                    menuEntries = emptyList(),
                    menuTemplates = emptyList(),
                    menuTemplateEntries = emptyList(),
                    dailyAssignments = listOf(
                        dailyAssignment(id = canonicalDailyA, weekStartMillis = weekB),
                    ),
                ),
                target = MenuCloudSnapshot(
                    dailyAssignments = listOf(
                        dailyAssignment(id = canonicalDailyA, weekStartMillis = weekStart),
                        dailyAssignment(id = canonicalDailyB, weekStartMillis = weekB),
                    ),
                ),
                canUpload = true,
            )
        }
        assertTrue(error.message!!.contains("Conflicting upload operations"))
        assertTrue(error.message!!.contains(canonicalDailyA))
    }

    @Test
    fun baseMenuIdLookup_usesBaseWeeklyOnly() {
        val entryWeekB = MenuSyncIds.menuEntryId(weekB, 0, "BREAKFAST")
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = listOf(weeklyMenu(id = "alias-week-b", startDateMillis = weekB)),
                menuEntries = listOf(
                    menuEntry(id = entryWeekB, menuId = "alias-week-b", updatedAt = 1L),
                ),
                menuTemplates = emptyList(),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(
                weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
                menuEntries = listOf(
                    menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 2L),
                ),
            ),
            canUpload = true,
        )
        assertFalse(plan.any { it.documentId == canonicalEntryId && it is MenuCloudWriteOperation.Delete })
        assertTrue(plan.any { it.documentId == entryWeekB && it is MenuCloudWriteOperation.Delete })
    }

    @Test
    fun setPayloadIdMatchesOperationDocumentId() {
        val target = MenuCloudSnapshot(weeklyMenus = listOf(weeklyMenu()))
        val set = plan(base = null, target = target, canUpload = true).single() as MenuCloudWriteOperation.Set
        assertEquals(canonicalParentId, set.documentId)
        assertEquals(canonicalParentId, set.fields["id"])
    }

    @Test
    fun deletedLogicalRecord_producesDelete() {
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = listOf(weeklyMenu()),
            menuEntries = listOf(menuEntry(id = canonicalEntryId, menuId = canonicalParentId)),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val plan = plan(base, MenuCloudSnapshot.empty(), canUpload = true)
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == canonicalParentId })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == canonicalEntryId })
    }

    @Test
    fun remoteDuplicateAliases_deletesAllNonCanonicalDocs() {
        val base = MenuCloudRawRemoteBase(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(id = "alias-a", menuId = canonicalParentId, updatedAt = 1L),
                menuEntry(id = "alias-b", menuId = canonicalParentId, updatedAt = 2L),
            ),
            menuTemplates = emptyList(),
            menuTemplateEntries = emptyList(),
            dailyAssignments = emptyList(),
        )
        val target = MenuCloudSnapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 2L),
            ),
        )
        val plan = plan(base, target, canUpload = true)
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == "alias-a" })
        assertTrue(plan.any { it is MenuCloudWriteOperation.Delete && it.documentId == "alias-b" })
    }

    @Test
    fun deterministicOrdering_sortsByCollectionThenDocumentIdThenKind() {
        val target = MenuCloudSnapshot(
            weeklyMenus = listOf(weeklyMenu()),
            menuEntries = listOf(menuEntry(id = canonicalEntryId, menuId = canonicalParentId)),
            menuTemplates = listOf(template(templateId)),
        )
        val plan = plan(base = null, target = target, canUpload = true)
        val collectionOrder = listOf(
            DeletedCloudDocumentCollections.WEEKLY_MENUS,
            DeletedCloudDocumentCollections.MENU_ENTRIES,
            DeletedCloudDocumentCollections.MENU_TEMPLATES,
        )
        val indices = plan.map { collectionOrder.indexOf(it.collection) }
        assertEquals(indices, indices.sorted())
    }

    @Test
    fun weekly_createdAtOnlyDifference_isNoOp() {
        val baseMenu = weeklyMenu(id = canonicalParentId, updatedAt = 10L, createdAt = 1_000L)
        val targetMenu = weeklyMenu(id = canonicalParentId, updatedAt = 10L, createdAt = 2_000L)
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = listOf(baseMenu),
                menuEntries = emptyList(),
                menuTemplates = emptyList(),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(weeklyMenus = listOf(targetMenu)),
            canUpload = true,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun weekly_contentChangedWithDivergentCreatedAt_setsWithBaseCreatedAt() {
        val baseMenu = weeklyMenu(id = canonicalParentId, updatedAt = 10L, createdAt = 1_000L)
        val targetMenu = weeklyMenu(
            id = canonicalParentId,
            updatedAt = 20L,
            createdAt = 2_000L,
            isAutoGenerated = true,
        )
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = listOf(baseMenu),
                menuEntries = emptyList(),
                menuTemplates = emptyList(),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(weeklyMenus = listOf(targetMenu)),
            canUpload = true,
        )
        val set = plan.single() as MenuCloudWriteOperation.Set
        assertEquals(canonicalParentId, set.documentId)
        assertEquals(1_000L, set.fields["createdAt"])
        assertEquals(20L, set.fields["updatedAt"])
        assertEquals(true, set.fields["isAutoGenerated"])
    }

    @Test
    fun weekly_missingBaseCanonicalCreate_usesTargetCreatedAt() {
        val targetMenu = weeklyMenu(id = canonicalParentId, updatedAt = 10L, createdAt = 2_500L)
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = listOf(weeklyMenu(id = "alias-week", updatedAt = 5L, createdAt = 100L)),
                menuEntries = emptyList(),
                menuTemplates = emptyList(),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(weeklyMenus = listOf(targetMenu)),
            canUpload = true,
        )
        val set = plan.single { it is MenuCloudWriteOperation.Set && it.documentId == canonicalParentId }
            as MenuCloudWriteOperation.Set
        assertEquals(2_500L, set.fields["createdAt"])
    }

    @Test
    fun template_createdAtOnlyDifference_isNoOp() {
        val baseTemplate = template(templateId, createdAt = 1_000L, updatedAt = 10L)
        val targetTemplate = template(templateId, createdAt = 2_000L, updatedAt = 10L)
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = emptyList(),
                menuEntries = emptyList(),
                menuTemplates = listOf(baseTemplate),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(menuTemplates = listOf(targetTemplate)),
            canUpload = true,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun template_contentChangedWithDivergentCreatedAt_setsWithBaseCreatedAt() {
        val baseTemplate = template(templateId, createdAt = 1_000L, updatedAt = 10L)
        val targetTemplate = ExportMenuTemplate(
            id = templateId,
            name = "Renamed",
            createdAt = 2_000L,
            updatedAt = 20L,
        )
        val plan = plan(
            base = MenuCloudRawRemoteBase(
                weeklyMenus = emptyList(),
                menuEntries = emptyList(),
                menuTemplates = listOf(baseTemplate),
                menuTemplateEntries = emptyList(),
                dailyAssignments = emptyList(),
            ),
            target = MenuCloudSnapshot(menuTemplates = listOf(targetTemplate)),
            canUpload = true,
        )
        val set = plan.single() as MenuCloudWriteOperation.Set
        assertEquals(templateId, set.documentId)
        assertEquals(1_000L, set.fields["createdAt"])
        assertEquals("Renamed", set.fields["name"])
        assertEquals(20L, set.fields["updatedAt"])
    }

    @Test
    fun template_missingBaseCreate_usesTargetCreatedAt() {
        val targetTemplate = template(templateId, createdAt = 2_500L, updatedAt = 10L)
        val plan = plan(
            base = null,
            target = MenuCloudSnapshot(menuTemplates = listOf(targetTemplate)),
            canUpload = true,
        )
        val set = plan.single() as MenuCloudWriteOperation.Set
        assertEquals(2_500L, set.fields["createdAt"])
    }

    @Test
    fun invalidCanonicalTarget_throwsBeforePlanning() {
        assertThrows(MenuCloudUploadPlanException::class.java) {
            plan(
                base = null,
                target = MenuCloudSnapshot(
                    weeklyMenus = listOf(weeklyMenu(id = "not-canonical")),
                ),
                canUpload = true,
            )
        }
    }

    private fun plan(
        base: MenuCloudRawRemoteBase?,
        target: MenuCloudSnapshot,
        canUpload: Boolean,
        ignored: Set<MenuCloudRemoteIdRef> = emptySet(),
    ) = MenuCloudUploadPlanner.plan(
        MenuCloudUploadPlanInput(
            rawRemoteBase = base,
            cloudTarget = target,
            ignoredOrphanIds = ignored,
            canUploadMenu = canUpload,
        ),
    )

    private fun weeklyMenu(
        id: String = canonicalParentId,
        startDateMillis: Long = weekStart,
        updatedAt: Long = 10L,
        createdAt: Long = 1L,
        isAutoGenerated: Boolean = false,
    ) = ExportWeeklyMenu(
        id = id,
        startDateMillis = startDateMillis,
        isAutoGenerated = isAutoGenerated,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun menuEntry(
        id: String,
        menuId: String,
        customDish: String = "toast",
        updatedAt: Long = 1L,
    ) = ExportMenuEntry(
        id = id,
        menuId = menuId,
        dayOfWeek = 0,
        mealType = "BREAKFAST",
        customDish = customDish,
        servings = 2,
        updatedAt = updatedAt,
    )

    private fun template(
        id: String,
        createdAt: Long = 1L,
        updatedAt: Long = 1L,
    ) = ExportMenuTemplate(
        id = id,
        name = "Template",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun templateEntry(
        id: String,
        templateId: String,
        dayOfWeek: Int = 0,
        mealType: String = "BREAKFAST",
    ) = ExportMenuTemplateEntry(
        id = id,
        templateId = templateId,
        dayOfWeek = dayOfWeek,
        mealType = mealType,
        servings = 2,
        updatedAt = 1L,
    )

    private fun dailyAssignment(
        id: String,
        weekStartMillis: Long = weekStart,
        dayOfWeek: Int = 0,
    ) = ExportDailyAssignment(
        id = id,
        weekStartMillis = weekStartMillis,
        dayOfWeek = dayOfWeek,
        isRated = false,
        updatedAt = 1L,
    )
}
