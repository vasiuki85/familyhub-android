package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyhub.data.local.entity.MenuTemplateEntities
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries

@Dao
interface MenuTemplateDao {
    @Transaction
    @Query("SELECT * FROM menu_templates ORDER BY createdAt DESC")
    fun observeAllTemplates(): kotlinx.coroutines.flow.Flow<List<MenuTemplateWithEntries>>

    @Transaction
    @Query("SELECT * FROM menu_templates WHERE id = :templateId LIMIT 1")
    suspend fun getById(templateId: String): MenuTemplateWithEntries?

    @Transaction
    @Query("SELECT * FROM menu_templates ORDER BY createdAt DESC")
    suspend fun getAllWithEntries(): List<MenuTemplateWithEntries>

    @Query("DELETE FROM menu_template_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM menu_templates")
    suspend fun clearTemplates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MenuTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MenuTemplateEntryEntity>)

    @Query("DELETE FROM menu_template_entries WHERE templateId = :templateId")
    suspend fun deleteEntries(templateId: String)

    @Query("DELETE FROM menu_template_entries WHERE id IN (:ids)")
    suspend fun deleteEntriesByIds(ids: List<String>)

    @Query("DELETE FROM menu_templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: String)

    @Transaction
    suspend fun upsertTemplate(entities: MenuTemplateEntities) {
        insertTemplate(entities.template)
        deleteEntries(entities.template.id)
        if (entities.entries.isNotEmpty()) {
            insertEntries(entities.entries)
        }
    }
}
