package com.familyhub.data.menu.cloud

import com.familyhub.data.export.ExportMenuTemplate
import com.familyhub.data.export.ExportWeeklyMenu
import com.familyhub.domain.utils.MenuSyncIds

/**
 * Pins immutable parent [createdAt] from raw remote base for existing documents.
 *
 * Alias-only weekly remotes without a canonical base document keep the merge winner [createdAt]
 * so canonical create operations can supply the winner timestamp.
 */
internal object MenuCloudImmutableParentFields {

    fun pinCreatedAt(
        snapshot: MenuCloudSnapshot,
        rawRemoteBase: MenuCloudRawRemoteBase?,
    ): MenuCloudSnapshot {
        if (rawRemoteBase == null) return snapshot
        return snapshot.copy(
            weeklyMenus = pinWeeklyCreatedAt(snapshot.weeklyMenus, rawRemoteBase),
            menuTemplates = pinTemplateCreatedAt(snapshot.menuTemplates, rawRemoteBase),
        )
    }

    fun effectiveWeeklyMenu(
        targetMenu: ExportWeeklyMenu,
        baseDocs: List<ExportWeeklyMenu>,
    ): ExportWeeklyMenu {
        val baseCanonical = baseDocs.firstOrNull { it.id == MenuSyncIds.weeklyMenuId(targetMenu.startDateMillis) }
        return if (baseCanonical != null) {
            targetMenu.copy(createdAt = baseCanonical.createdAt)
        } else {
            targetMenu
        }
    }

    fun effectiveMenuTemplate(
        targetTemplate: ExportMenuTemplate,
        baseTemplate: ExportMenuTemplate?,
    ): ExportMenuTemplate {
        return if (baseTemplate != null) {
            targetTemplate.copy(createdAt = baseTemplate.createdAt)
        } else {
            targetTemplate
        }
    }

    private fun pinWeeklyCreatedAt(
        menus: List<ExportWeeklyMenu>,
        rawRemoteBase: MenuCloudRawRemoteBase,
    ): List<ExportWeeklyMenu> {
        return menus.map { menu ->
            val canonicalId = MenuSyncIds.weeklyMenuId(menu.startDateMillis)
            val baseCanonical = rawRemoteBase.weeklyMenus.firstOrNull { it.id == canonicalId }
            if (baseCanonical != null) {
                menu.copy(createdAt = baseCanonical.createdAt)
            } else {
                menu
            }
        }
    }

    private fun pinTemplateCreatedAt(
        templates: List<ExportMenuTemplate>,
        rawRemoteBase: MenuCloudRawRemoteBase,
    ): List<ExportMenuTemplate> {
        val baseById = rawRemoteBase.menuTemplates.associateBy { it.id }
        return templates.map { template ->
            val baseTemplate = baseById[template.id]
            if (baseTemplate != null) {
                template.copy(createdAt = baseTemplate.createdAt)
            } else {
                template
            }
        }
    }
}
