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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCloudMergeEngineTest {

    private val userId = "user-admin"
    private val weekStart = 1_700_000_000_000L
    private val aliasParentId = "alias-parent-week"
    private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)

    @Test
    fun admin_mergeLocalAndRemote_picksDeterministicWinner() {
        val local = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 100L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "local",
                    updatedAt = 50L,
                ),
            ),
        )
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 200L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "remote",
                    updatedAt = 80L,
                ),
            ),
        )

        val result = mergeAs(adminInput(local, remote))

        assertEquals("remote", result.cloudTarget.menuEntries.single().customDish)
        assertEquals(result.cloudTarget, result.localProjection)
        assertTrue(result.capability.canUploadMenu)
    }

    @Test
    fun child_usesRemoteOnlyProjection_localChangesAndTombstonesIgnored() {
        val local = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 9_999L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "local-only",
                    updatedAt = 9_999L,
                ),
            ),
        )
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 10L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "remote",
                    updatedAt = 10L,
                ),
            ),
        )
        val tombstones = listOf(
            tombstone(DeletedCloudDocumentCollections.MENU_ENTRIES, MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST")),
        )

        val result = mergeAs(
            childInput(local, remote).copy(tombstones = tombstones),
        )

        assertEquals("remote", result.cloudTarget.menuEntries.single().customDish)
        assertEquals(result.cloudTarget, result.localProjection)
        assertFalse(result.capability.canUploadMenu)
    }

    @Test
    fun pendingMember_readOnly() {
        val result = mergeAs(
            MenuCloudMergeInput(
                localSnapshot = snapshot(weeklyMenus = listOf(weeklyMenu())),
                remoteSnapshot = snapshot(weeklyMenus = listOf(weeklyMenu())),
                tombstones = emptyList(),
                currentUserId = userId,
                remoteSelfMember = MenuCloudMemberRef(userId, MemberRole.PENDING),
                localSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
            ),
        )
        assertFalse(result.capability.canUploadMenu)
    }

    @Test
    fun bootstrapLocalAdmin_writesLocalWhenRemoteAbsent() {
        val local = snapshot(
            weeklyMenus = listOf(weeklyMenu(customDishParentUpdatedAt = 30L)),
            menuEntries = listOf(
                menuEntry(
                    id = "entry-local",
                    menuId = aliasParentId,
                    customDish = "bootstrap",
                ),
            ),
        )

        val result = mergeAs(
            MenuCloudMergeInput(
                localSnapshot = local,
                remoteSnapshot = null,
                tombstones = emptyList(),
                currentUserId = userId,
                remoteSelfMember = null,
                localSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
            ),
        )

        assertTrue(result.capability.canUploadMenu)
        assertEquals("bootstrap", result.cloudTarget.menuEntries.single().customDish)
        assertNull(result.rawRemoteBase)
    }

    @Test
    fun remoteAbsentLocalChild_readOnlyWithEmptyCloudTarget() {
        val local = snapshot(weeklyMenus = listOf(weeklyMenu()))

        val result = mergeAs(
            MenuCloudMergeInput(
                localSnapshot = local,
                remoteSnapshot = null,
                tombstones = emptyList(),
                currentUserId = userId,
                remoteSelfMember = null,
                localSelfMember = MenuCloudMemberRef(userId, MemberRole.CHILD),
            ),
        )

        assertFalse(result.capability.canUploadMenu)
        assertTrue(result.cloudTarget.weeklyMenus.isEmpty())
        assertEquals(1, result.localProjection.weeklyMenus.size)
    }

    @Test
    fun aliasWeeklyParentAndChild_canonicalizeReferences() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(
                menuEntry(
                    id = "alias-entry",
                    menuId = aliasParentId,
                    customDish = "toast",
                    cookingTaskId = "cook-1",
                    prepTaskId = "prep-1",
                ),
            ),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        assertEquals(canonicalParentId, result.cloudTarget.weeklyMenus.single().id)
        assertEquals(canonicalParentId, result.cloudTarget.menuEntries.single().menuId)
        assertEquals(MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"), result.cloudTarget.menuEntries.single().id)
        assertEquals("cook-1", result.cloudTarget.menuEntries.single().cookingTaskId)
    }

    @Test
    fun rawRemoteBase_preservesAliasIds() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId)),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        assertNotNull(result.rawRemoteBase)
        assertEquals(aliasParentId, result.rawRemoteBase!!.weeklyMenus.single().id)
        assertEquals("alias-entry", result.rawRemoteBase!!.menuEntries.single().id)
        assertEquals(aliasParentId, result.rawRemoteBase!!.menuEntries.single().menuId)
    }

    @Test
    fun aliasClassifiedObsolete_forWeeklyParentAndEntry() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId, updatedAt = 5L)),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        assertTrue(
            result.obsoleteRemoteIds.contains(
                MenuCloudRemoteIdRef(DeletedCloudDocumentCollections.WEEKLY_MENUS, aliasParentId),
            ),
        )
        assertTrue(
            result.obsoleteRemoteIds.contains(
                MenuCloudRemoteIdRef(DeletedCloudDocumentCollections.MENU_ENTRIES, "alias-entry"),
            ),
        )
    }

    @Test
    fun futureDeltaExample_aliasBaseToCanonicalTarget() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId, customDish = "toast")),
        )
        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        val canonicalEntryId = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST")
        assertEquals(canonicalParentId, result.cloudTarget.weeklyMenus.single().id)
        assertEquals(canonicalEntryId, result.cloudTarget.menuEntries.single().id)
        assertTrue(result.obsoleteRemoteIds.any { it.documentId == aliasParentId })
        assertTrue(result.obsoleteRemoteIds.any { it.documentId == "alias-entry" })
        assertNotEquals(result.rawRemoteBase!!.weeklyMenus.single().id, result.cloudTarget.weeklyMenus.single().id)
    }

    @Test
    fun duplicateWeek_sameWinnerRegardlessOfInputOrder() {
        val first = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = "low", updatedAt = 5L, isAutoGenerated = true),
                weeklyMenu(id = "high", updatedAt = 20L, isAutoGenerated = false),
            ),
        )
        val second = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = "high", updatedAt = 20L, isAutoGenerated = false),
                weeklyMenu(id = "low", updatedAt = 5L, isAutoGenerated = true),
            ),
        )

        val a = mergeAs(adminInput(first, MenuCloudSnapshot.empty()))
        val b = mergeAs(adminInput(second, MenuCloudSnapshot.empty()))

        assertEquals(a.cloudTarget.weeklyMenus, b.cloudTarget.weeklyMenus)
        assertEquals(20L, a.cloudTarget.weeklyMenus.single().updatedAt)
    }

    @Test
    fun duplicateSlot_sameWinnerRegardlessOfInputOrder() {
        val first = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(id = "low", menuId = canonicalParentId, customDish = "a", updatedAt = 1L),
                menuEntry(id = "high", menuId = canonicalParentId, customDish = "b", updatedAt = 9L),
            ),
        )
        val second = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(id = "high", menuId = canonicalParentId, customDish = "b", updatedAt = 9L),
                menuEntry(id = "low", menuId = canonicalParentId, customDish = "a", updatedAt = 1L),
            ),
        )

        val a = mergeAs(adminInput(first, MenuCloudSnapshot.empty()))
        val b = mergeAs(adminInput(second, MenuCloudSnapshot.empty()))

        assertEquals(a.cloudTarget.menuEntries, b.cloudTarget.menuEntries)
        assertEquals("b", a.cloudTarget.menuEntries.single().customDish)
    }

    @Test
    fun tombstoneCanonicalWeeklyId_suppressesLogicalWeek() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId)),
        )
        val tombstones = listOf(tombstone(DeletedCloudDocumentCollections.WEEKLY_MENUS, canonicalParentId))

        val result = mergeAs(
            adminInput(MenuCloudSnapshot.empty(), remote).copy(tombstones = tombstones),
        )

        assertTrue(result.cloudTarget.weeklyMenus.isEmpty())
        assertTrue(result.cloudTarget.menuEntries.isEmpty())
    }

    @Test
    fun templateTombstone_worksByDocumentId() {
        val templateId = "tpl-uuid-1"
        val remote = snapshot(
            menuTemplates = listOf(template(id = templateId, name = "Week")),
            menuTemplateEntries = listOf(
                templateEntry(id = "tpl-entry", templateId = templateId, cookingTaskId = "cook", prepTaskId = "prep"),
            ),
        )
        val tombstones = listOf(tombstone(DeletedCloudDocumentCollections.MENU_TEMPLATES, templateId))

        val result = mergeAs(
            adminInput(MenuCloudSnapshot.empty(), remote).copy(tombstones = tombstones),
        )

        assertTrue(result.cloudTarget.menuTemplates.isEmpty())
        assertTrue(result.cloudTarget.menuTemplateEntries.isEmpty())
    }

    @Test
    fun templateTaskRefs_normalizedToNull_weeklyTaskRefsPreserved() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(
                    id = "weekly-entry",
                    menuId = canonicalParentId,
                    cookingTaskId = "cook-weekly",
                    prepTaskId = "prep-weekly",
                ),
            ),
            menuTemplates = listOf(template(id = "tpl-1", name = "T")),
            menuTemplateEntries = listOf(
                templateEntry(
                    id = "tpl-entry",
                    templateId = "tpl-1",
                    cookingTaskId = "cook-template",
                    prepTaskId = "prep-template",
                ),
            ),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        val weekly = result.cloudTarget.menuEntries.single()
        assertEquals("cook-weekly", weekly.cookingTaskId)
        assertEquals("prep-weekly", weekly.prepTaskId)
        val templateEntry = result.cloudTarget.menuTemplateEntries.single()
        assertNull(templateEntry.cookingTaskId)
        assertNull(templateEntry.prepTaskId)
    }

    @Test
    fun orphanExcludedAndMarkedIgnored_notObsolete() {
        val remote = snapshot(
            menuEntries = listOf(menuEntry(id = "orphan-entry", menuId = "missing-parent")),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        assertTrue(result.cloudTarget.menuEntries.isEmpty())
        assertTrue(
            result.ignoredOrphanIds.contains(
                MenuCloudRemoteIdRef(DeletedCloudDocumentCollections.MENU_ENTRIES, "orphan-entry"),
            ),
        )
        assertFalse(
            result.obsoleteRemoteIds.contains(
                MenuCloudRemoteIdRef(DeletedCloudDocumentCollections.MENU_ENTRIES, "orphan-entry"),
            ),
        )
    }

    @Test
    fun malformedLocal_negativeTimestamp_failClosed() {
        val local = snapshot(weeklyMenus = listOf(weeklyMenu(updatedAt = -1L)))
        assertThrows(MenuCloudSnapshotInvalidException::class.java) {
            mergeAs(adminInput(local, MenuCloudSnapshot.empty()))
        }
    }

    @Test
    fun malformedRemote_invalidMealType_failClosed() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(
                menuEntry(id = "bad-meal", menuId = canonicalParentId, mealType = "NOT_A_MEAL"),
            ),
        )
        assertThrows(MenuCloudSnapshotInvalidException::class.java) {
            mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))
        }
    }

    @Test
    fun malformedRemote_dayOutOfRange_failClosed() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(menuEntry(id = "bad-day", menuId = canonicalParentId, dayOfWeek = 9)),
        )
        assertThrows(MenuCloudSnapshotInvalidException::class.java) {
            mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))
        }
    }

    @Test
    fun malformedRemote_invalidFirestoreId_failClosed() {
        val remote = snapshot(weeklyMenus = listOf(weeklyMenu(id = "bad/id")))
        assertThrows(MenuCloudSnapshotInvalidException::class.java) {
            mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))
        }
    }

    @Test
    fun mergeCommutative_forSymmetricWriterPolicy() {
        val local = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 100L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "local",
                    updatedAt = 100L,
                ),
            ),
        )
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId, updatedAt = 50L)),
            menuEntries = listOf(
                menuEntry(
                    id = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
                    menuId = canonicalParentId,
                    customDish = "remote",
                    updatedAt = 50L,
                ),
            ),
        )

        val forward = mergeAs(adminInput(local, remote))
        val reverse = mergeAs(adminInput(remote, local))

        assertEquals(forward.cloudTarget, reverse.cloudTarget)
    }

    @Test
    fun repeatedMerge_isIdempotent() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = aliasParentId, updatedAt = 10L)),
            menuEntries = listOf(menuEntry(id = "alias-entry", menuId = aliasParentId, customDish = "toast")),
        )
        val first = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))
        val second = mergeAs(
            adminInput(
                local = first.cloudTarget,
                remote = first.cloudTarget,
            ),
        )
        assertEquals(first.cloudTarget, second.cloudTarget)
        assertTrue(second.obsoleteRemoteIds.isEmpty())
    }

    @Test
    fun allPermutations_produceSameListsAndClassifications() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(id = "parent-a", updatedAt = 10L),
                weeklyMenu(id = "parent-b", updatedAt = 12L, startDateMillis = weekStart + 7),
            ),
            menuEntries = listOf(
                menuEntry(id = "e1", menuId = "parent-a", customDish = "one", updatedAt = 3L),
                menuEntry(id = "e2", menuId = "parent-a", customDish = "two", updatedAt = 8L, dayOfWeek = 1, mealType = "LUNCH"),
            ),
        )
        val permutations = listOf(
            adminInput(MenuCloudSnapshot.empty(), remote),
            adminInput(MenuCloudSnapshot.empty(), remote.copy(
                weeklyMenus = remote.weeklyMenus.reversed(),
                menuEntries = remote.menuEntries.reversed(),
            )),
        ).map { mergeAs(it) }

        permutations.drop(1).forEach { result ->
            assertEquals(permutations.first().cloudTarget, result.cloudTarget)
            assertEquals(permutations.first().remoteIdClassifications, result.remoteIdClassifications)
        }
    }

    @Test
    fun canonicalRemote_pinsImmutableCreatedAtWhileContentWinnerKeepsLocalValues() {
        val local = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(
                    id = canonicalParentId,
                    updatedAt = 200L,
                    createdAt = 2_000L,
                    isAutoGenerated = true,
                ),
            ),
        )
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(
                    id = canonicalParentId,
                    updatedAt = 100L,
                    createdAt = 1_000L,
                    isAutoGenerated = false,
                ),
            ),
        )

        val forward = mergeAs(adminInput(local, remote))
        val reverse = mergeAs(adminInput(remote, local))

        assertEquals(200L, forward.cloudTarget.weeklyMenus.single().updatedAt)
        assertTrue(forward.cloudTarget.weeklyMenus.single().isAutoGenerated)
        assertEquals(1_000L, forward.localProjection.weeklyMenus.single().createdAt)
        assertEquals(1_000L, forward.cloudTarget.weeklyMenus.single().createdAt)
        assertEquals(forward.cloudTarget.weeklyMenus.single().updatedAt, reverse.cloudTarget.weeklyMenus.single().updatedAt)
        assertEquals(forward.cloudTarget.weeklyMenus.single().isAutoGenerated, reverse.cloudTarget.weeklyMenus.single().isAutoGenerated)
    }

    @Test
    fun aliasOnlyRemote_preservesWinnerCreatedAtForCanonicalCreate() {
        val remote = snapshot(
            weeklyMenus = listOf(
                weeklyMenu(
                    id = aliasParentId,
                    updatedAt = 10L,
                    createdAt = 2_500L,
                ),
            ),
        )

        val result = mergeAs(adminInput(MenuCloudSnapshot.empty(), remote))

        assertEquals(canonicalParentId, result.cloudTarget.weeklyMenus.single().id)
        assertEquals(2_500L, result.cloudTarget.weeklyMenus.single().createdAt)
        assertEquals(2_500L, result.localProjection.weeklyMenus.single().createdAt)
    }

    @Test
    fun templateRemote_pinsImmutableCreatedAtWhileContentWinnerKeepsLocalValues() {
        val templateId = "tpl-uuid-1"
        val local = snapshot(
            menuTemplates = listOf(
                template(id = templateId, name = "Local name", updatedAt = 200L, createdAt = 2_000L),
            ),
        )
        val remote = snapshot(
            menuTemplates = listOf(
                template(id = templateId, name = "Remote name", updatedAt = 100L, createdAt = 1_000L),
            ),
        )

        val result = mergeAs(adminInput(local, remote))

        assertEquals("Local name", result.cloudTarget.menuTemplates.single().name)
        assertEquals(200L, result.cloudTarget.menuTemplates.single().updatedAt)
        assertEquals(1_000L, result.localProjection.menuTemplates.single().createdAt)
        assertEquals(1_000L, result.cloudTarget.menuTemplates.single().createdAt)
    }

    @Test
    fun unmatchedTombstone_doesNotDeleteUnrelatedRecords() {
        val remote = snapshot(
            weeklyMenus = listOf(weeklyMenu(id = canonicalParentId)),
            menuEntries = listOf(menuEntry(id = "entry-1", menuId = canonicalParentId)),
        )
        val tombstones = listOf(tombstone(DeletedCloudDocumentCollections.WEEKLY_MENUS, "unknown-week-id"))

        val result = mergeAs(
            adminInput(MenuCloudSnapshot.empty(), remote).copy(tombstones = tombstones),
        )

        assertEquals(1, result.cloudTarget.weeklyMenus.size)
        assertEquals(1, result.cloudTarget.menuEntries.size)
    }

    private fun adminInput(local: MenuCloudSnapshot, remote: MenuCloudSnapshot) = MenuCloudMergeInput(
        localSnapshot = local,
        remoteSnapshot = remote,
        tombstones = emptyList(),
        currentUserId = userId,
        remoteSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
        localSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
    )

    private fun childInput(local: MenuCloudSnapshot, remote: MenuCloudSnapshot) = MenuCloudMergeInput(
        localSnapshot = local,
        remoteSnapshot = remote,
        tombstones = emptyList(),
        currentUserId = userId,
        remoteSelfMember = MenuCloudMemberRef(userId, MemberRole.CHILD),
        localSelfMember = MenuCloudMemberRef(userId, MemberRole.ADMIN),
    )

    private fun mergeAs(input: MenuCloudMergeInput) = MenuCloudMergeEngine.merge(input)

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
        id: String = aliasParentId,
        startDateMillis: Long = weekStart,
        updatedAt: Long = 1L,
        createdAt: Long = 1L,
        isAutoGenerated: Boolean = false,
        customDishParentUpdatedAt: Long = updatedAt,
    ) = ExportWeeklyMenu(
        id = id,
        startDateMillis = startDateMillis,
        isAutoGenerated = isAutoGenerated,
        createdAt = createdAt,
        updatedAt = customDishParentUpdatedAt,
    )

    private fun menuEntry(
        id: String = "entry-1",
        menuId: String = aliasParentId,
        dayOfWeek: Int = 0,
        mealType: String = "BREAKFAST",
        customDish: String = "toast",
        updatedAt: Long = 1L,
        cookingTaskId: String? = null,
        prepTaskId: String? = null,
    ) = ExportMenuEntry(
        id = id,
        menuId = menuId,
        dayOfWeek = dayOfWeek,
        mealType = mealType,
        customDish = customDish,
        servings = 2,
        cookingTaskId = cookingTaskId,
        prepTaskId = prepTaskId,
        updatedAt = updatedAt,
    )

    private fun template(id: String, name: String, updatedAt: Long = 1L, createdAt: Long = 1L) = ExportMenuTemplate(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun templateEntry(
        id: String,
        templateId: String,
        dayOfWeek: Int = 0,
        mealType: String = "BREAKFAST",
        cookingTaskId: String? = null,
        prepTaskId: String? = null,
    ) = ExportMenuTemplateEntry(
        id = id,
        templateId = templateId,
        dayOfWeek = dayOfWeek,
        mealType = mealType,
        servings = 2,
        cookingTaskId = cookingTaskId,
        prepTaskId = prepTaskId,
        updatedAt = 1L,
    )

    private fun tombstone(collection: String, documentId: String) =
        MenuCloudTombstone(collection, documentId)
}
