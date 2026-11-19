package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WeeklyMenuRepository @Inject constructor(
    private val weeklyMenuDao: WeeklyMenuDao,
    private val menuTemplateDao: MenuTemplateDao,
    private val menuMutationCoordinator: MenuMutationCoordinator,
) {
    fun observeMenu(startDateMillis: Long): Flow<WeeklyMenu?> {
        return weeklyMenuDao.observeByStartDate(startDateMillis).map { entity -> entity?.toDomain() }
    }

    fun observeMenuHistory(): Flow<List<WeeklyMenu>> {
        return weeklyMenuDao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getMenu(startDateMillis: Long): WeeklyMenu? {
        return weeklyMenuDao.getByStartDate(startDateMillis)?.toDomain()
    }

    suspend fun getRecentMenus(limit: Int): List<WeeklyMenu> {
        return weeklyMenuDao.getRecent(limit).map { it.toDomain() }
    }

    suspend fun upsertMenu(actorUserId: String, menu: WeeklyMenu) {
        menuMutationCoordinator.replaceWeeklyMenu(actorUserId, menu)
    }

    suspend fun deleteMenu(actorUserId: String, menuId: String) {
        menuMutationCoordinator.deleteWeeklyMenu(actorUserId, menuId)
    }

    fun observeMenuTemplates(): Flow<List<MenuTemplate>> {
        return menuTemplateDao.observeAllTemplates().map { entries -> entries.map { it.toDomain() } }
    }

    suspend fun saveMenuTemplate(actorUserId: String, template: MenuTemplate) {
        menuMutationCoordinator.replaceMenuTemplate(actorUserId, template)
    }

    suspend fun deleteMenuTemplate(actorUserId: String, templateId: String) {
        menuMutationCoordinator.deleteMenuTemplate(actorUserId, templateId)
    }

    suspend fun getMenuTemplate(templateId: String): MenuTemplate? {
        return menuTemplateDao.getById(templateId)?.toDomain()
    }
}
