package com.familyhub.data.menu.cloud

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.MenuSyncIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCloudCanonicalClassificationTest {

    private val userId = "user-admin"
    private val weekStart = 1_700_000_000_000L
    private val aliasParentId = "alias-parent-week"
    private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)
    private val canonicalEntryId = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST")
    private val templateId = "tpl-uuid-1"
    private val canonicalTemplateEntryId = MenuSyncIds.menuTemplateEntryId(templateId, 0, "BREAKFAST")
    private val canonicalDailyId = MenuSyncIds.dailyAssignmentId(weekStart, 0)

    @Test
    fun weeklyCanonicalLosesToNewerAlias_canonicalNotObsolete_targetUsesWinnerPayload() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = canonicalParentId, updatedAt = 10L),
                weeklyMenu(id = aliasParentId, updatedAt = 20L),
            ),
        )

        val result = mergeRemote(remote)

        assertEquals(canonicalParentId, result.cloudTarget.weeklyMenus.single().id)
        assertEquals(20L, result.cloudTarget.weeklyMenus.single().updatedAt)
        assertFalse(result.obsoleteRemoteIds.any { it.documentId == canonicalParentId })
        assertEquals(
            listOf(
                MenuCloudRemoteIdClassification(
                    DeletedCloudDocumentCollections.WEEKLY_MENUS,
                    aliasParentId,
                    MenuCloudRemoteIdKind.OBSOLETE_ALIAS,
                ),
            ),
            result.remoteIdClassifications,
        )
    }

    @Test
    fun menuEntryCanonicalLosesToNewerAlias_canonicalNotObsolete() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 1L)),
            menuEntries = listOf(
                menuEntry(id = canonicalEntryId, menuId = canonicalParentId, customDish = "old", updatedAt = 5L),
                menuEntry(id = "alias-entry", menuId = canonicalParentId, customDish = "new", updatedAt = 20L),
            ),
        )

        val result = mergeRemote(remote)

        assertEquals(canonicalEntryId, result.cloudTarget.menuEntries.single().id)
        assertEquals("new", result.cloudTarget.menuEntries.single().customDish)
        assertFalse(result.obsoleteRemoteIds.any { it.documentId == canonicalEntryId })
        assertTrue(result.obsoleteRemoteIds.any { it.documentId == "alias-entry" })
    }

    @Test
    fun templateEntryCanonicalLosesToNewerAlias_canonicalNotObsolete() {
        val remote = snapshot(
            menuTemplates = listOf(template(templateId, "Week")),
            menuTemplateEntries = listOf(
                templateEntry(id = canonicalTemplateEntryId, templateId = templateId, customDish = "old", updatedAt = 5L),
                templateEntry(id = "alias-tpl-entry", templateId = templateId, customDish = "new", updatedAt = 20L),
            ),
        )

        val result = mergeRemote(remote)

        assertEquals(canonicalTemplateEntryId, result.cloudTarget.menuTemplateEntries.single().id)
        assertEquals("new", result.cloudTarget.menuTemplateEntries.single().customDish)
        assertFalse(result.obsoleteRemoteIds.any { it.documentId == canonicalTemplateEntryId })
        assertTrue(result.obsoleteRemoteIds.any { it.documentId == "alias-tpl-entry" })
    }

    @Test
    fun dailyAssignmentCanonicalLosesToNewerAlias_canonicalNotObsolete() {
        val remote = snapshot(
            dailyAssignments = listOf(
                dailyAssignment(id = canonicalDailyId, memberId = "old", updatedAt = 5L),
                dailyAssignment(id = "alias-daily", memberId = "new", updatedAt = 20L),
            ),
        )

        val result = mergeRemote(remote)

        assertEquals(canonicalDailyId, result.cloudTarget.dailyAssignments.single().id)
        assertEquals("new", result.cloudTarget.dailyAssignments.single().assignedMemberId)
        assertFalse(result.obsoleteRemoteIds.any { it.documentId == canonicalDailyId })
        assertTrue(result.obsoleteRemoteIds.any { it.documentId == "alias-daily" })
    }

    @Test
    fun aliasTemplateEntryTombstone_suppressesCanonicalSlot() {
        val remote = snapshot(
            menuTemplates = listOf(template(templateId, "Week")),
            menuTemplateEntries = listOf(
                templateEntry(id = "alias-tpl-entry", templateId = templateId, customDish = "toast"),
            ),
        )
        val tombstones = listOf(
            MenuCloudTombstone(DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES, "alias-tpl-entry"),
        )

        val result = mergeRemote(remote, tombstones)

        assertTrue(result.cloudTarget.menuTemplateEntries.isEmpty())
    }

    @Test
    fun aliasDailyAssignmentTombstone_suppressesCanonicalSlot() {
        val remote = snapshot(
            dailyAssignments = listOf(
                dailyAssignment(id = "alias-daily", memberId = "member-a"),
            ),
        )
        val tombstones = listOf(
            MenuCloudTombstone(DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS, "alias-daily"),
        )

        val result = mergeRemote(remote, tombstones)

        assertTrue(result.cloudTarget.dailyAssignments.isEmpty())
    }

    @Test
    fun aliasWeeklyTombstone_suppressesCanonicalWeekAndChildren() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId)),
        )
        val tombstones = listOf(
            MenuCloudTombstone(DeletedCloudDocumentCollections.WEEKLY_MENUS, aliasParentId),
        )

        val result = mergeRemote(remote, tombstones)

        assertTrue(result.cloudTarget.weeklyMenus.isEmpty())
        assertTrue(result.cloudTarget.menuEntries.isEmpty())
    }

    @Test
    fun aliasMenuEntryTombstone_suppressesCanonicalEntry() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId, customDish = "toast")),
        )
        val tombstones = listOf(
            MenuCloudTombstone(DeletedCloudDocumentCollections.MENU_ENTRIES, "alias-entry"),
        )

        val result = mergeRemote(remote, tombstones)

        assertTrue(result.cloudTarget.menuEntries.isEmpty())
    }

    @Test
    fun reverseInputOrder_producesEqualClassificationLists() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = canonicalParentId, updatedAt = 10L),
                weeklyMenu(id = aliasParentId, updatedAt = 20L),
            ),
            menuEntries = listOf(
                menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 5L),
                menuEntry(id = "alias-entry", menuId = aliasParentId, updatedAt = 15L),
                menuEntry(id = "orphan-entry", menuId = "missing-parent"),
            ),
            menuTemplates = listOf(template(templateId, "Week")),
            menuTemplateEntries = listOf(
                templateEntry(id = canonicalTemplateEntryId, templateId = templateId, updatedAt = 4L),
                templateEntry(id = "alias-tpl-entry", templateId = templateId, updatedAt = 12L),
                templateEntry(id = "orphan-tpl-entry", templateId = "missing-template"),
            ),
            dailyAssignments = listOf(
                dailyAssignment(id = canonicalDailyId, updatedAt = 3L),
                dailyAssignment(id = "alias-daily", updatedAt = 11L),
            ),
        )
        val reversed = remote.copy(
            weeklyMenus = remote.weeklyMenus.reversed(),
            menuEntries = remote.menuEntries.reversed(),
            menuTemplateEntries = remote.menuTemplateEntries.reversed(),
            dailyAssignments = remote.dailyAssignments.reversed(),
        )

        val forward = mergeRemote(remote)
        val backward = mergeRemote(reversed)

        assertEquals(forward.remoteIdClassifications, backward.remoteIdClassifications)
    }

    @Test
    fun classifications_haveNoDuplicates() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = canonicalParentId, updatedAt = 10L),
                weeklyMenu(id = aliasParentId, updatedAt = 20L),
            ),
            menuEntries = listOf(
                menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 5L),
                menuEntry(id = "alias-entry", menuId = aliasParentId, updatedAt = 15L),
            ),
        )

        val result = mergeRemote(remote)
        val distinct = result.remoteIdClassifications.distinctBy { Triple(it.collection, it.documentId, it.kind) }

        assertEquals(distinct.size, result.remoteIdClassifications.size)
    }

    @Test
    fun futureDeltaTargetSet_doesNotClassifyCanonicalIdAsObsolete() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = canonicalParentId, updatedAt = 10L),
                weeklyMenu(id = aliasParentId, updatedAt = 20L),
            ),
            menuEntries = listOf(
                menuEntry(id = canonicalEntryId, menuId = canonicalParentId, updatedAt = 5L),
                menuEntry(id = "alias-entry", menuId = aliasParentId, updatedAt = 15L),
            ),
        )

        val result = mergeRemote(remote)
        val targetIds = buildSet {
            result.cloudTarget.weeklyMenus.forEach { add(it.id) }
            result.cloudTarget.menuEntries.forEach { add(it.id) }
        }

        result.obsoleteRemoteIds.forEach { obsolete ->
            assertFalse(
                "canonical target ${obsolete.documentId} must not also be obsolete",
                obsolete.documentId in targetIds,
            )
        }
        assertTrue(canonicalParentId in targetIds)
        assertTrue(canonicalEntryId in targetIds)
    }

    private fun mergeRemote(
        remote: MenuCloudSnapshot,
        tombstones: List<MenuCloudTombstone> = emptyList(),
    ) = MenuCloudMergeEngine.merge(
        MenuCloudMergeInput(
            localSnapshot = MenuCloudSnapshot.empty(),
            remoteSnapshot = remote,
            tombstones = tombstones,
            currentUserId = userId,
            remoteSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
            localSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
        ),
    )

    private fun snapshot(
        weeklyMenus: List<ExportWeeklyMenu> = emptyList(),
        menuEntries: List<ExportMenuEntry> = emptyList(),
        menuTemplates: List<ExportMenuTemplate> = emptyList(),
        menuTemplateEntries: List<ExportMenuTemplateEntry> = emptyList(),
        dailyAssignments: List<ExportDailyAssignment> = emptyList(),
    ) = MenuCloudSnapshot(
        weeklyMenus = weeklyMenus,
        menuEntries = menuEntries,
        menuTemplates = menuTemplates,
        menuTemplateEntries = menuTemplateEntries,
        dailyAssignments = dailyAssignments,
    )

    private fun weeklyMenu(
        id: String,
        updatedAt: Long = 1L,
        startDateMillis: Long = weekStart,
    ) = ExportWeeklyMenu(
        id = id,
        startDateMillis = startDateMillis,
        isAutoGenerated = false,
        createdAt = 1L,
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

    private fun template(id: String, name: String) = ExportMenuTemplate(
        id = id,
        name = name,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun templateEntry(
        id: String,
        templateId: String,
        customDish: String = "toast",
        updatedAt: Long = 1L,
    ) = ExportMenuTemplateEntry(
        id = id,
        templateId = templateId,
        dayOfWeek = 0,
        mealType = "BREAKFAST",
        customDish = customDish,
        servings = 2,
        updatedAt = updatedAt,
    )

    private fun dailyAssignment(
        id: String,
        memberId: String? = null,
        updatedAt: Long = 1L,
    ) = ExportDailyAssignment(
        id = id,
        weekStartMillis = weekStart,
        dayOfWeek = 0,
        assignedMemberId = memberId,
        updatedAt = updatedAt,
    )
}
