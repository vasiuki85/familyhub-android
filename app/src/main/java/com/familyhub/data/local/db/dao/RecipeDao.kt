package com.familyhub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyhub.data.local.entity.RecipeEntities
import com.familyhub.data.local.entity.RecipeEntity
import com.familyhub.data.local.entity.RecipeIngredientEntity
import com.familyhub.data.local.entity.RecipeStepEntity
import com.familyhub.data.local.entity.RecipeWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY title")
    fun observeAll(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecipeWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<RecipeStepEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: String)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    suspend fun deleteStepsForRecipe(recipeId: String)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("UPDATE recipes SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM recipes")
    suspend fun clear()

    @Transaction
    suspend fun upsertRecipeWithDetails(entities: RecipeEntities) {
        upsertRecipe(entities.recipe)
        deleteIngredientsForRecipe(entities.recipe.id)
        deleteStepsForRecipe(entities.recipe.id)
        if (entities.ingredients.isNotEmpty()) {
            upsertIngredients(entities.ingredients)
        }
        if (entities.steps.isNotEmpty()) {
            upsertSteps(entities.steps)
        }
    }
}
