package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.data.local.entity.ShoppingCategoryEntity
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.fixMojibakeIfNeeded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ShoppingRepository @Inject constructor(
    private val dao: ShoppingItemDao,
    private val idGenerator: IdGenerator,
    private val cloudDeletionLedger: CloudDeletionLedger
) {
    companion object {
        const val DEFAULT_LIST_ID = "default_list"
    }

    fun observeItems(listId: String): Flow<List<ShoppingItem>> {
        return dao.getAll(listId).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getItemById(id: String): ShoppingItem? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun upsertItem(item: ShoppingItem) {
        dao.upsert(item.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.SHOPPING_ITEMS, item.id)
    }

    suspend fun upsertItems(items: List<ShoppingItem>) {
        if (items.isEmpty()) return
        dao.upsertAll(items.map { it.toEntity() })
        cloudDeletionLedger.clearDeletions(
            DeletedCloudDocumentCollections.SHOPPING_ITEMS,
            items.map { it.id }
        )
    }

    suspend fun getItemsSnapshot(listId: String): List<ShoppingItem> {
        return dao.getAllOnce(listId).map { it.toDomain() }
    }

    suspend fun deleteItem(item: ShoppingItem) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.SHOPPING_ITEMS, item.id)
        dao.delete(item.toEntity())
    }

    suspend fun updateBoughtStatus(items: List<ShoppingItem>, isBought: Boolean, timestamp: Long?) {
        if (items.isEmpty()) return
        dao.updateBoughtStatus(items.map { it.id }, isBought, timestamp)
    }

    suspend fun clearList(listId: String) {
        val items = dao.getAllOnce(listId)
        cloudDeletionLedger.recordDeletions(
            DeletedCloudDocumentCollections.SHOPPING_ITEMS,
            items.map { it.id }
        )
        dao.clear(listId)
    }

    fun observeLists(): Flow<List<ShoppingList>> {
        return dao.getAllListsWithArchived().map { lists -> lists.map { it.toDomain() } }
    }

    suspend fun getListById(id: String): ShoppingList? {
        return dao.getListById(id)?.toDomain()
    }

    suspend fun upsertList(list: ShoppingList) {
        dao.upsertList(list.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.SHOPPING_LISTS, list.id)
    }

    suspend fun deleteList(list: ShoppingList) {
        val items = dao.getAllOnce(list.id)
        val categories = dao.getCategoriesOnce(list.id)
        cloudDeletionLedger.recordDeletions(
            buildList {
                add(DeletedCloudDocumentCollections.SHOPPING_LISTS to list.id)
                categories.forEach { category ->
                    add(DeletedCloudDocumentCollections.SHOPPING_CATEGORIES to category.id)
                }
                items.forEach { item ->
                    add(DeletedCloudDocumentCollections.SHOPPING_ITEMS to item.id)
                }
            }
        )
        dao.clear(list.id)
        dao.deleteCategoriesByList(list.id)
        dao.deleteList(list.toEntity())
    }

    fun observeCategories(listId: String): Flow<List<ShoppingCategory>> {
        return dao.getCategories(listId).map { categories -> categories.map { it.toDomain() } }
    }

    suspend fun upsertCategory(category: ShoppingCategory) {
        dao.upsertCategory(category.toEntity())
        cloudDeletionLedger.clearDeletion(DeletedCloudDocumentCollections.SHOPPING_CATEGORIES, category.id)
    }

    suspend fun deleteCategory(category: ShoppingCategory) {
        cloudDeletionLedger.recordDeletion(DeletedCloudDocumentCollections.SHOPPING_CATEGORIES, category.id)
        dao.detachCategory(category.id)
        dao.deleteCategory(category.toEntity())
    }

    suspend fun getCategoriesSnapshot(listId: String): List<ShoppingCategory> {
        return dao.getCategoriesOnce(listId).map { it.toDomain() }
    }

    suspend fun duplicateList(source: ShoppingList, target: ShoppingList) {
        val categories = dao.getCategoriesOnce(source.id)
        val items = dao.getAllOnce(source.id)

        dao.upsertList(target.toEntity())

        val categoryIdMapping = if (categories.isNotEmpty()) {
            val mapping = mutableMapOf<String, String>()
            val duplicatedCategories = categories.map { category ->
                val newId = idGenerator.newId()
                mapping[category.id] = newId
                category.copy(
                    id = newId,
                    listId = target.id,
                    sortOrder = category.sortOrder
                )
            }
            dao.upsertCategories(duplicatedCategories)
            mapping
        } else {
            emptyMap()
        }

        if (items.isNotEmpty()) {
            val duplicatedItems = items.map { entity ->
                val newId = idGenerator.newId()
                entity.copy(
                    id = newId,
                    listId = target.id,
                    isBought = false,
                    boughtAt = null,
                    createdAt = System.currentTimeMillis(),
                    categoryId = entity.categoryId?.let { categoryIdMapping[it] }
                )
            }
            dao.upsertAll(duplicatedItems)
        }
    }

    suspend fun archiveList(listId: String, archivedAt: Long) {
        val list = dao.getListById(listId)?.toDomain() ?: return
        dao.upsertList(list.copy(isArchived = true, archivedAt = archivedAt).toEntity())
    }

    suspend fun restoreList(listId: String) {
        val list = dao.getListById(listId)?.toDomain() ?: return
        dao.upsertList(list.copy(isArchived = false, archivedAt = null).toEntity())
    }

    suspend fun updateParticipants(listId: String, participantIds: List<String>) {
        val list = dao.getListById(listId)?.toDomain() ?: return
        dao.upsertList(list.copy(participantIds = participantIds).toEntity())
    }

    suspend fun logActivity(activity: ShoppingActivity) {
        dao.insertActivity(activity.toEntity())
    }

    fun observeActivity(listId: String, limit: Int): Flow<List<ShoppingActivity>> {
        return dao.observeActivity(listId, limit).map { entries -> entries.map { it.toDomain() } }
    }

    suspend fun getActivitySnapshot(listId: String): List<ShoppingActivity> {
        return dao.getActivityOnce(listId).map { it.toDomain() }
    }

    suspend fun replaceActivities(activities: List<ShoppingActivity>) {
        if (activities.isEmpty()) return
        dao.insertActivities(activities.map { it.toEntity() })
    }

    suspend fun clearAllActivity() {
        dao.clearActivity()
    }

    suspend fun loadTopItemTitles(limit: Int): List<String> {
        return dao.getTopItemTitles(limit).map { it.title.fixMojibakeIfNeeded() }
    }

    suspend fun ensureDefaultList(): ShoppingList {
        val existing = dao.getListById(DEFAULT_LIST_ID)?.toDomain()
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val defaultList = ShoppingList(
            id = DEFAULT_LIST_ID,
            title = "Основной",
            colorHex = "#4CAF50",
            iconName = "cart",
            createdAt = now,
            participantIds = emptyList()
        )

        dao.upsertList(defaultList.toEntity())

        val defaultCategories = listOf(
            ShoppingCategoryEntity("cat_produce", DEFAULT_LIST_ID, "Овощи и фрукты", "#4CAF50", "fruits", 0),
            ShoppingCategoryEntity("cat_dairy", DEFAULT_LIST_ID, "Молочные", "#FFB300", "dairy", 1),
            ShoppingCategoryEntity("cat_household", DEFAULT_LIST_ID, "Быт", "#607D8B", "household", 2)
        )
        defaultCategories.forEach { dao.upsertCategory(it) }

        return defaultList
    }
}
