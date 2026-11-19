package com.familyhub.data.sync

import com.familyhub.data.datastore.DeletedCloudDocument
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportMember
import com.familyhub.data.sync.logic.TaskStatusMerge
import com.familyhub.data.menu.cloud.MenuCloudMemberRef
import com.familyhub.data.menu.cloud.MenuCloudMergeEngine
import com.familyhub.data.menu.cloud.MenuCloudMergeInput
import com.familyhub.data.menu.cloud.MenuCloudMergeResult
import com.familyhub.data.menu.cloud.MenuCloudRawRemoteBase
import com.familyhub.data.menu.cloud.MenuCloudRemoteIdRef
import com.familyhub.data.menu.cloud.MenuCloudSnapshot
import com.familyhub.data.menu.cloud.MenuCloudTombstone
import com.familyhub.data.menu.cloud.MenuCloudUploadContext
import com.familyhub.data.menu.cloud.MenuCloudUploadPlanInput
import com.familyhub.data.menu.cloud.MenuCloudUploadPlanner
import com.familyhub.data.menu.cloud.MenuCloudWriteCapabilityResult
import com.familyhub.domain.model.MemberRole

private val menuCollectionByOutboxKey = linkedMapOf(
    "weeklyMenus" to DeletedCloudDocumentCollections.WEEKLY_MENUS,
    "menuEntries" to DeletedCloudDocumentCollections.MENU_ENTRIES,
    "menuTemplates" to DeletedCloudDocumentCollections.MENU_TEMPLATES,
    "menuTemplateEntries" to DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES,
    "dailyAssignments" to DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS,
)

internal fun ExportData.toMenuCloudSnapshot(): MenuCloudSnapshot {
    return MenuCloudSnapshot(
        weeklyMenus = weeklyMenus,
        menuEntries = menuEntries,
        menuTemplates = menuTemplates,
        menuTemplateEntries = menuTemplateEntries,
        dailyAssignments = dailyAssignments,
    )
}

internal fun ExportData.withMenuSnapshot(menu: MenuCloudSnapshot): ExportData {
    return copy(
        weeklyMenus = menu.weeklyMenus,
        menuEntries = menu.menuEntries,
        menuTemplates = menu.menuTemplates,
        menuTemplateEntries = menu.menuTemplateEntries,
        dailyAssignments = menu.dailyAssignments,
    )
}

internal fun ExportData.withoutMenuFields(): ExportData {
    return copy(
        weeklyMenus = emptyList(),
        menuEntries = emptyList(),
        menuTemplates = emptyList(),
        menuTemplateEntries = emptyList(),
        dailyAssignments = emptyList(),
    )
}

internal fun ExportMember.toMenuCloudMemberRef(): MenuCloudMemberRef? {
    val role = runCatching { MemberRole.valueOf(role) }.getOrNull() ?: return null
    return MenuCloudMemberRef(userId = id, role = role)
}

internal fun menuTombstonesFrom(
    deletedDocuments: Set<DeletedCloudDocument>,
    pendingDeletedEntityIdsByCollection: Map<String, Set<String>> = emptyMap(),
): List<MenuCloudTombstone> {
    val legacyTombstones = deletedDocuments
        .filter { it.collection in DeletedCloudDocumentCollections.MENU_COLLECTIONS }
        .map { MenuCloudTombstone(collection = it.collection, documentId = it.documentId) }
    val outboxTombstones = menuCollectionByOutboxKey.flatMap { (outboxKey, cloudCollection) ->
        pendingDeletedEntityIdsByCollection[outboxKey]
            .orEmpty()
            .map { documentId ->
                MenuCloudTombstone(collection = cloudCollection, documentId = documentId)
            }
    }
    return (legacyTombstones + outboxTombstones)
        .distinct()
        .sortedWith(compareBy(MenuCloudTombstone::collection, MenuCloudTombstone::documentId))
}

internal fun stagedMenuTombstones(deletedDocuments: Set<DeletedCloudDocument>): Set<DeletedCloudDocument> {
    return deletedDocuments.filter { it.collection in DeletedCloudDocumentCollections.MENU_COLLECTIONS }.toSet()
}

internal fun mergeMenuCloud(
    localSnapshot: MenuCloudSnapshot,
    remoteSnapshot: MenuCloudSnapshot?,
    tombstones: List<MenuCloudTombstone>,
    currentUserId: String,
    remoteSelfMember: MenuCloudMemberRef?,
    localSelfMember: MenuCloudMemberRef?,
): MenuCloudMergeResult {
    return MenuCloudMergeEngine.merge(
        MenuCloudMergeInput(
            localSnapshot = localSnapshot,
            remoteSnapshot = remoteSnapshot,
            tombstones = tombstones,
            currentUserId = currentUserId,
            remoteSelfMember = remoteSelfMember,
            localSelfMember = localSelfMember,
        ),
    )
}

internal fun buildMenuCloudUploadContext(
    menuResult: MenuCloudMergeResult,
): MenuCloudUploadContext {
    val operations = MenuCloudUploadPlanner.plan(
        MenuCloudUploadPlanInput(
            rawRemoteBase = menuResult.rawRemoteBase,
            cloudTarget = menuResult.cloudTarget,
            ignoredOrphanIds = menuResult.ignoredOrphanIds.toSet(),
            canUploadMenu = menuResult.capability.canUploadMenu,
        ),
    )
    return MenuCloudUploadContext(
        capability = menuResult.capability,
        operations = operations,
    )
}

internal data class MenuCloudSyncDecisions(
    val nonMenuNoDiff: Boolean,
    val menuCloudNeedsWrite: Boolean,
    val localDiffersFromMerged: Boolean,
    val existingNonMenuUploadRequired: Boolean,
    val writableMenuTombstoneNeedsVerification: Boolean,
) {
    val uploadRequired: Boolean
        get() = existingNonMenuUploadRequired || menuCloudNeedsWrite || writableMenuTombstoneNeedsVerification

    val shouldRunUploadTransaction: Boolean
        get() = existingNonMenuUploadRequired || menuCloudNeedsWrite

    val verificationOnly: Boolean
        get() = writableMenuTombstoneNeedsVerification && !shouldRunUploadTransaction

    val verificationDownloadRequired: Boolean
        get() = shouldRunUploadTransaction || writableMenuTombstoneNeedsVerification
}

internal fun evaluateMenuCloudSyncDecisions(
    datedLocal: ExportData,
    remoteData: ExportData?,
    mergedForLocal: ExportData,
    menuUploadContext: MenuCloudUploadContext,
    hasPendingChanges: Boolean,
    remoteAvailable: Boolean,
    isCreatingFamily: Boolean,
    stagedMenuTombstonesPresent: Boolean,
    normalizedForComparison: (ExportData) -> ExportData,
): MenuCloudSyncDecisions {
    val alignedMerged = remoteData?.let {
        mergedForLocal.copy(
            exportDate = it.exportDate,
            version = it.version,
            versionNanos = it.versionNanos,
        )
    } ?: mergedForLocal

    val alignedRemote = remoteData?.copy(
        exportDate = alignedMerged.exportDate,
        version = alignedMerged.version,
        versionNanos = alignedMerged.versionNanos,
    )

    val nonMenuNoDiff = alignedRemote != null &&
        normalizedForComparison(alignedMerged.withoutMenuFields()) ==
        normalizedForComparison(alignedRemote.withoutMenuFields())

    val alignedLocal = datedLocal.copy(
        exportDate = mergedForLocal.exportDate,
        version = mergedForLocal.version,
        versionNanos = mergedForLocal.versionNanos,
    )
    val localDiffersFromMerged =
        normalizedForComparison(alignedLocal) != normalizedForComparison(alignedMerged)

    val menuCloudNeedsWrite = menuUploadContext.canUploadMenu && menuUploadContext.operations.isNotEmpty()
    val mergedTaskStatusAheadOfRemote = remoteData != null &&
        TaskStatusMerge.hasMergedTaskStatusAheadOfRemote(mergedForLocal, remoteData)
    val existingNonMenuUploadRequired = (remoteAvailable && hasPendingChanges) ||
        isCreatingFamily ||
        mergedTaskStatusAheadOfRemote
    val writableMenuTombstoneNeedsVerification =
        menuUploadContext.canUploadMenu && stagedMenuTombstonesPresent

    return MenuCloudSyncDecisions(
        nonMenuNoDiff = nonMenuNoDiff,
        menuCloudNeedsWrite = menuCloudNeedsWrite,
        localDiffersFromMerged = localDiffersFromMerged,
        existingNonMenuUploadRequired = existingNonMenuUploadRequired,
        writableMenuTombstoneNeedsVerification = writableMenuTombstoneNeedsVerification,
    )
}

internal fun rawRemoteMenuSnapshot(remoteData: ExportData?): MenuCloudSnapshot? {
    return remoteData?.toMenuCloudSnapshot()
}

internal fun menuRawRemoteBase(remoteData: ExportData?): MenuCloudRawRemoteBase? {
    val snapshot = remoteData?.toMenuCloudSnapshot() ?: return null
    return MenuCloudRawRemoteBase.fromSnapshot(snapshot)
}

internal fun isAuthoritativeMenuWriter(capability: MenuCloudWriteCapabilityResult): Boolean {
    return capability.canUploadMenu
}

internal fun isAuthoritativeMenuWriterRole(member: ExportMember?): Boolean {
    val role = member?.role?.let { runCatching { MemberRole.valueOf(it) }.getOrNull() } ?: return false
    return role == MemberRole.ADMIN || role == MemberRole.PARENT
}

internal fun selfMemberForUpload(
    localSelf: ExportMember?,
    remoteSelf: ExportMember?,
): ExportMember? {
    if (remoteSelf != null) {
        val local = localSelf ?: remoteSelf
        return mergeSelfMemberProfileForUpload(local, remoteSelf)
    }
    return localSelf
}

internal fun mergeSelfMemberProfileForUpload(
    local: ExportMember,
    remote: ExportMember,
): ExportMember {
    return local.copy(
        id = remote.id,
        role = remote.role,
    )
}

internal fun menuTombstonesConfirmedAbsentOnRawRemote(
    rawRemote: MenuCloudSnapshot,
    tombstones: Set<DeletedCloudDocument>,
): Set<DeletedCloudDocument> {
    if (tombstones.isEmpty()) return emptySet()
    val batch = buildMenuTombstonePresenceBatch(rawRemote) ?: return emptySet()
    return tombstones.filter { document ->
        val presence = evaluateMenuTombstoneRemotePresence(
            MenuCloudTombstone(document.collection, document.documentId),
            batch,
        )
        presence == MenuTombstoneRemotePresence.ABSENT
    }.toSet()
}

internal fun ignoredOrphanRefs(menuResult: MenuCloudMergeResult): Set<MenuCloudRemoteIdRef> {
    return menuResult.ignoredOrphanIds.toSet()
}
