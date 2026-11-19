package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.WeeklyMenuEntities
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyMenuDao {
    @Transaction
    @Query("SELECT * FROM weekly_menus WHERE startDateMillis = :startDate LIMIT 1")
    fun observeByStartDate(startDate: Long): Flow<WeeklyMenuWithEntries?>

    @Transaction
    @Query("SELECT * FROM weekly_menus WHERE startDateMillis = :startDate LIMIT 1")
    suspend fun getByStartDate(startDate: Long): WeeklyMenuWithEntries?

    @Transaction
    @Query("SELECT * FROM weekly_menus WHERE id = :menuId LIMIT 1")
    suspend fun getById(menuId: String): WeeklyMenuWithEntries?

    @Transaction
    @Query("SELECT * FROM weekly_menus ORDER BY startDateMillis DESC")
    fun observeAll(): Flow<List<WeeklyMenuWithEntries>>

    @Transaction
    @Query("SELECT * FROM weekly_menus ORDER BY startDateMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<WeeklyMenuWithEntries>

    @Transaction
    @Query("SELECT * FROM weekly_menus ORDER BY startDateMillis DESC")
    suspend fun getAllWithEntries(): List<WeeklyMenuWithEntries>

    @Query("DELETE FROM menu_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM weekly_menus")
    suspend fun clearMenus()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenu(menu: WeeklyMenuEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<MenuEntryEntity>)

    @Query("DELETE FROM menu_entries WHERE menuId = :menuId")
    suspend fun deleteEntriesForMenu(menuId: String)

    @Query("DELETE FROM menu_entries WHERE id IN (:ids)")
    suspend fun deleteEntriesByIds(ids: List<String>)

    @Query("DELETE FROM weekly_menus WHERE id = :menuId")
    suspend fun deleteMenu(menuId: String)

    @Transaction
    suspend fun upsertMenuWithEntries(entities: WeeklyMenuEntities) {
        upsertMenu(entities.menu)
        deleteEntriesForMenu(entities.menu.id)
        if (entities.entries.isNotEmpty()) {
            upsertEntries(entities.entries)
        }
    }
}
