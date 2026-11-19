package com.familyhub.data.menu.cloud

import com.familyhub.data.export.ExportDailyAssignment
import com.familyhub.data.export.ExportMenuEntry
import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportMenuTemplateEntry
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.model.MemberRole

data class MenuCloudSnapshot(
    val weeklyMenus: List<ExportWeeklyMenu> = emptyList(),
    val menuEntries: List<ExportMenuEntry> = emptyList(),
    val menuTemplates: List<ExportMenuTemplate> = emptyList(),
    val menuTemplateEntries: List<ExportMenuTemplateEntry> = emptyList(),
    val dailyAssignments: List<ExportDailyAssignment> = emptyList(),
) {
    companion object {
        fun empty(): MenuCloudSnapshot = MenuCloudSnapshot()
    }
}

data class MenuCloudMemberRef(
    val userId: String,
    val role: MemberRole,
)

data class MenuCloudTombstone(
    val collection: String,
    val documentId: String,
)

data class MenuCloudMergeInput(
    val localSnapshot: MenuCloudSnapshot,
    val remoteSnapshot: MenuCloudSnapshot?,
    val tombstones: List<MenuCloudTombstone>,
    val currentUserId: String,
    val remoteSelfMember: MenuCloudMemberRef?,
    val localSelfMember: MenuCloudMemberRef?,
)

data class MenuCloudRemoteIdRef(
    val collection: String,
    val documentId: String,
)

enum class MenuCloudRemoteIdKind {
    OBSOLETE_ALIAS,
    IGNORED_ORPHAN,
}

data class MenuCloudRemoteIdClassification(
    val collection: String,
    val documentId: String,
    val kind: MenuCloudRemoteIdKind,
)

data class MenuCloudRawRemoteBase(
    val weeklyMenus: List<ExportWeeklyMenu>,
    val menuEntries: List<ExportMenuEntry>,
    val menuTemplates: List<ExportMenuTemplate>,
    val menuTemplateEntries: List<ExportMenuTemplateEntry>,
    val dailyAssignments: List<ExportDailyAssignment>,
) {
    companion object {
        fun fromSnapshot(snapshot: MenuCloudSnapshot): MenuCloudRawRemoteBase = MenuCloudRawRemoteBase(
            weeklyMenus = snapshot.weeklyMenus.map { it.copy() },
            menuEntries = snapshot.menuEntries.map { it.copy() },
            menuTemplates = snapshot.menuTemplates.map { it.copy() },
            menuTemplateEntries = snapshot.menuTemplateEntries.map { it.copy() },
            dailyAssignments = snapshot.dailyAssignments.map { it.copy() },
        )
    }
}

enum class MenuCloudReadOnlyReason {
    REMOTE_CHILD_ROLE,
    REMOTE_PENDING_ROLE,
    REMOTE_MEMBER_MISSING,
    BOOTSTRAP_REQUIRES_LOCAL_ADMIN,
}

data class MenuCloudWriteCapabilityResult(
    val canUploadMenu: Boolean,
    val readOnlyReason: MenuCloudReadOnlyReason?,
)

data class MenuCloudMergeResult(
    val localProjection: MenuCloudSnapshot,
    val cloudTarget: MenuCloudSnapshot,
    val rawRemoteBase: MenuCloudRawRemoteBase?,
    val remoteIdClassifications: List<MenuCloudRemoteIdClassification>,
    val capability: MenuCloudWriteCapabilityResult,
) {
    val obsoleteRemoteIds: List<MenuCloudRemoteIdRef>
        get() = remoteIdClassifications
            .filter { it.kind == MenuCloudRemoteIdKind.OBSOLETE_ALIAS }
            .map { MenuCloudRemoteIdRef(it.collection, it.documentId) }

    val ignoredOrphanIds: List<MenuCloudRemoteIdRef>
        get() = remoteIdClassifications
            .filter { it.kind == MenuCloudRemoteIdKind.IGNORED_ORPHAN }
            .map { MenuCloudRemoteIdRef(it.collection, it.documentId) }
}
