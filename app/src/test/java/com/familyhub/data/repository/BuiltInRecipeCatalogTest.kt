package com.familyhub.data.repository

import com.familyhub.data.export.ExportData
import com.familyhub.data.export.ExportRecipe
import com.familyhub.data.sync.PlannedUploadOperation
import com.familyhub.data.sync.buildUploadPlan
import com.familyhub.data.sync.countUploadWriteOperations
import com.familyhub.data.sync.isUploadWithinTransactionLimit
import com.familyhub.domain.utils.BuiltInRecipeImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRecipeCatalogTest {
    @Test
    fun catalog_hasThirtyStableUniqueRecipesAndImages() {
        val recipes = BuiltInRecipeCatalog.recipes

        assertEquals(30, recipes.size)
        assertEquals(30, recipes.map { it.id }.toSet().size)
        assertEquals(
            (1..30).map { "builtin-recipe-v1-${it.toString().padStart(2, '0')}" },
            recipes.map { it.id },
        )
        assertEquals(BuiltInRecipeImage.tokens, recipes.map { it.imageUrl })
    }

    @Test
    fun childRecords_areUniqueAndReferenceTheirRecipe() {
        val recipes = BuiltInRecipeCatalog.recipes
        val ingredientIds = recipes.flatMap { recipe -> recipe.ingredients.map { it.id } }
        val stepIds = recipes.flatMap { recipe -> recipe.steps.map { it.id } }

        assertEquals(ingredientIds.size, ingredientIds.toSet().size)
        assertEquals(stepIds.size, stepIds.toSet().size)
        recipes.forEach { recipe ->
            assertTrue(recipe.ingredients.isNotEmpty())
            assertTrue(recipe.steps.isNotEmpty())
            assertTrue(recipe.ingredients.all { it.recipeId == recipe.id })
            assertTrue(recipe.steps.all { it.recipeId == recipe.id })
        }
    }

    @Test
    fun records_haveValidNonBlankPayloads() {
        BuiltInRecipeCatalog.recipes.forEach { recipe ->
            assertTrue(recipe.title.isNotBlank())
            assertTrue(recipe.description.isNotBlank())
            assertTrue(recipe.servings in 1..99)
            assertTrue(recipe.prepTimeMinutes > 0)
            assertTrue(recipe.createdAt >= 0)
            assertTrue(recipe.updatedAt >= recipe.createdAt)
            assertTrue(recipe.tags.isNotEmpty())
            assertTrue(recipe.ingredients.all { it.name.isNotBlank() && it.quantity.isNotBlank() })
            assertTrue(recipe.steps.all { it.description.isNotBlank() })
        }
    }

    @Test
    fun firstCloudUpload_isThirtyRecipeWritesAndWithinTransactionLimit() {
        val target = ExportData(
            recipes = BuiltInRecipeCatalog.recipes.map { recipe ->
                ExportRecipe(
                    id = recipe.id,
                    title = recipe.title,
                    imageUrl = recipe.imageUrl,
                )
            }
        )

        val plan = buildUploadPlan(
            baseSnapshot = null,
            targetSnapshot = target,
            isMemberWriteAllowed = true,
            selfMember = null,
        )

        assertEquals(30, plan.size)
        assertTrue(
            plan.all { operation ->
                operation is PlannedUploadOperation.Set &&
                    operation.collection == "recipes"
            }
        )
        assertTrue(
            isUploadWithinTransactionLimit(
                countUploadWriteOperations(
                    plannedOperations = plan,
                    includesFamilyUpdatedAt = true,
                )
            )
        )
    }
}
