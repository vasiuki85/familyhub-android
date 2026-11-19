package com.familyhub.data.repository

import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.entity.RecipeEntity
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RecipeRepositoryTombstoneTest {

    private val dao = mockk<RecipeDao>(relaxed = true)
    private val syncSettingsDataStore = mockk<SyncSettingsDataStore>(relaxed = true)
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        coEvery { syncSettingsDataStore.addDeletedRecipeForCurrentFamily(any()) } just Runs
        coEvery { syncSettingsDataStore.removeDeletedRecipeForCurrentFamily(any()) } just Runs
        repository = RecipeRepository(dao, syncSettingsDataStore)
    }

    private fun sampleRecipe(id: String = "recipe-1") = Recipe(
        id = id,
        title = "Soup",
        description = "",
        servings = 2,
        prepTimeMinutes = 30,
        difficulty = RecipeDifficulty.EASY,
        isFavorite = false,
        imageUrl = null,
        notes = "",
        tags = emptyList(),
        ingredients = emptyList(),
        steps = emptyList(),
        category = RecipeCategory.FIRST,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun deleteRecipe_recordsCurrentFamilyTombstoneBeforeDaoDelete() = runTest {
        val recipe = sampleRecipe()
        coEvery { dao.deleteRecipe(any<RecipeEntity>()) } just Runs

        repository.deleteRecipe(recipe)

        coVerifyOrder {
            syncSettingsDataStore.addDeletedRecipeForCurrentFamily("recipe-1")
            dao.deleteRecipe(any())
        }
    }

    @Test
    fun upsertRecipe_clearsCurrentFamilyTombstoneAfterDaoMutation() = runTest {
        val recipe = sampleRecipe()
        coEvery { dao.upsertRecipeWithDetails(any()) } just Runs

        repository.upsertRecipe(recipe)

        coVerifyOrder {
            dao.upsertRecipeWithDetails(any())
            syncSettingsDataStore.removeDeletedRecipeForCurrentFamily("recipe-1")
        }
    }

    @Test
    fun setFavorite_clearsCurrentFamilyTombstoneAfterDaoMutation() = runTest {
        coEvery { dao.updateFavorite(any(), any(), any()) } just Runs

        repository.setFavorite("recipe-1", isFavorite = true)

        coVerifyOrder {
            dao.updateFavorite("recipe-1", true, any())
            syncSettingsDataStore.removeDeletedRecipeForCurrentFamily("recipe-1")
        }
    }

    @Test
    fun deleteRecipe_usesAtomicCurrentFamilyApiNotScopedFamilyParameter() = runTest {
        val recipe = sampleRecipe()
        coEvery { dao.deleteRecipe(any<RecipeEntity>()) } just Runs

        repository.deleteRecipe(recipe)

        coVerify(exactly = 1) { syncSettingsDataStore.addDeletedRecipeForCurrentFamily("recipe-1") }
        coVerify(exactly = 0) { syncSettingsDataStore.addDeletedRecipe(any(), any()) }
    }
}
