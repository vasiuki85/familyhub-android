package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntities
import com.familyhub.domain.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val syncSettingsDataStore: SyncSettingsDataStore
) {
    fun observeRecipes(): Flow<List<Recipe>> {
        return recipeDao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getRecipeById(id: String): Recipe? {
        return recipeDao.getById(id)?.toDomain()
    }

    suspend fun upsertRecipe(recipe: Recipe) {
        val updatedRecipe = recipe.copy(updatedAt = System.currentTimeMillis())
        val entities = updatedRecipe.toEntities()
        recipeDao.upsertRecipeWithDetails(entities)
        syncSettingsDataStore.removeDeletedRecipeForCurrentFamily(updatedRecipe.id)
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        val entities = recipe.toEntities()
        syncSettingsDataStore.addDeletedRecipeForCurrentFamily(recipe.id)
        recipeDao.deleteRecipe(entities.recipe)
    }

    suspend fun setFavorite(recipeId: String, isFavorite: Boolean) {
        recipeDao.updateFavorite(recipeId, isFavorite, System.currentTimeMillis())
        syncSettingsDataStore.removeDeletedRecipeForCurrentFamily(recipeId)
    }
}
