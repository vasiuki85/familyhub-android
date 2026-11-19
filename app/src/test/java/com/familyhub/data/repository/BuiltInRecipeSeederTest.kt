package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.RecipeDao
import com.familyhub.data.local.entity.RecipeEntities
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BuiltInRecipeSeederTest {
    private val database = mockk<FamilyHubDatabase>()
    private val recipeDao = mockk<RecipeDao>(relaxed = true)
    private lateinit var seeder: BuiltInRecipeSeeder

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery {
            database.withTransaction<BuiltInRecipeSeeder.Result>(any())
        } coAnswers {
            secondArg<suspend () -> BuiltInRecipeSeeder.Result>().invoke()
        }
        seeder = BuiltInRecipeSeeder(database, recipeDao)
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun allRecipesMissing_insertsThirtyInsideTransaction() = runBlocking {
        coEvery { recipeDao.getById(any()) } returns null

        val result = seeder.seedMissing()

        assertEquals(BuiltInRecipeSeeder.Result(30, 0), result)
        coVerify(exactly = 30) {
            recipeDao.upsertRecipeWithDetails(any<RecipeEntities>())
        }
    }

    @Test
    fun existingRecipe_isNeverOverwritten() = runBlocking {
        val existing = BuiltInRecipeCatalog.recipes.first()
        coEvery { recipeDao.getById(existing.id) } returns mockk()
        coEvery {
            recipeDao.getById(match { recipeId -> recipeId != existing.id })
        } returns null

        val result = seeder.seedMissing()

        assertEquals(BuiltInRecipeSeeder.Result(29, 1), result)
        coVerify(exactly = 0) {
            recipeDao.upsertRecipeWithDetails(
                match { entities -> entities.recipe.id == existing.id }
            )
        }
        coVerify(exactly = 29) {
            recipeDao.upsertRecipeWithDetails(any<RecipeEntities>())
        }
    }
}
