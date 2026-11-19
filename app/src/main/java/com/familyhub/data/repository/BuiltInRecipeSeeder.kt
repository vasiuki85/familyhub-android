package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.entity.toEntities
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts only missing built-in recipes in one transaction.
 *
 * Existing rows are never overwritten, so edits imported or received from another device win.
 * The initialization marker prevents intentionally deleted built-ins from returning later.
 */
@Singleton
class BuiltInRecipeSeeder @Inject constructor(
    private val database: FamilyHubDatabase,
    private val recipeDao: RecipeDao,
) {
    data class Result(
        val insertedCount: Int,
        val existingCount: Int,
    )

    suspend fun seedMissing(): Result = database.withTransaction {
        var inserted = 0
        var existing = 0
        BuiltInRecipeCatalog.recipes.forEach { recipe ->
            if (recipeDao.getById(recipe.id) == null) {
                recipeDao.upsertRecipeWithDetails(recipe.toEntities())
                inserted += 1
            } else {
                existing += 1
            }
        }
        Result(
            insertedCount = inserted,
            existingCount = existing,
        )
    }
}
