package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyhub.data.local.entity.ShoppingCategoryEntity
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.ShoppingListEntity
import com.familyhub.data.local.entity.ShoppingActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY createdAt DESC")
    fun getAll(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY createdAt DESC")
    suspend fun getAllOnce(listId: String): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLists(lists: List<ShoppingListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<ShoppingCategoryEntity>)

    @Delete
    suspend fun delete(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun clear(listId: String)

    @Query("DELETE FROM shopping_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM shopping_lists")
    suspend fun clearLists()

    @Query("DELETE FROM shopping_categories")
    suspend fun clearCategories()

    @Query("UPDATE shopping_items SET isBought = :isBought, boughtAt = :boughtAt WHERE id IN (:ids)")
    suspend fun updateBoughtStatus(ids: List<String>, isBought: Boolean, boughtAt: Long?)

    @Query("UPDATE shopping_items SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: String)

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt ASC")
    fun getAllLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun getListById(id: String): ShoppingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ShoppingListEntity)

    @Delete
    suspend fun deleteList(list: ShoppingListEntity)

    @Query("SELECT * FROM shopping_categories WHERE listId = :listId ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    fun getCategories(listId: String): Flow<List<ShoppingCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: ShoppingCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: ShoppingCategoryEntity)

    @Query("DELETE FROM shopping_categories WHERE listId = :listId")
    suspend fun deleteCategoriesByList(listId: String)

    @Query("SELECT * FROM shopping_categories WHERE listId = :listId ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    suspend fun getCategoriesOnce(listId: String): List<ShoppingCategoryEntity>

    @Query(
        """
        SELECT LOWER(title) AS normalizedTitle, title, COUNT(*) as usageCount, MAX(createdAt) as lastUsed
        FROM shopping_items
        WHERE title IS NOT NULL AND TRIM(title) <> ''
        GROUP BY normalizedTitle
        ORDER BY usageCount DESC, lastUsed DESC
        LIMIT :limit
        """
    )
    suspend fun getTopItemTitles(limit: Int): List<TitleSuggestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ShoppingActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ShoppingActivityEntity>)

    @Query(
        """
        SELECT * FROM shopping_activity
        WHERE listId = :listId
        ORDER BY occurredAt DESC
        LIMIT :limit
        """
    )
    fun observeActivity(listId: String, limit: Int): Flow<List<ShoppingActivityEntity>>

    @Query("SELECT * FROM shopping_activity WHERE listId = :listId ORDER BY occurredAt DESC")
    suspend fun getActivityOnce(listId: String): List<ShoppingActivityEntity>

    @Query("DELETE FROM shopping_activity")
    suspend fun clearActivity()

    @Transaction
    @Query("SELECT * FROM shopping_lists ORDER BY createdAt ASC")
    fun getAllListsWithArchived(): Flow<List<ShoppingListEntity>>
}

data class TitleSuggestion(
    val normalizedTitle: String,
    val title: String,
    val usageCount: Int,
    val lastUsed: Long
)

