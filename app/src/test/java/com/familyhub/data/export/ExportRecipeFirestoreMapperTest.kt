package com.familyhub.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportRecipeFirestoreMapperTest {

    @Test
    fun toExportRecipe_readsLegacyFavoriteField() {
        val recipe = mapOf<String, Any?>(
            "title" to "Soup",
            "favorite" to true,
            "ingredients" to listOf(
                mapOf(
                    "id" to "ing_1",
                    "name" to "Water",
                    "quantity" to "1 l"
                )
            ),
            "steps" to listOf(
                mapOf(
                    "id" to "step_1",
                    "description" to "Boil",
                    "order" to 1L
                )
            )
        ).toExportRecipe("recipe_1")

        assertEquals("recipe_1", recipe.id)
        assertTrue(recipe.isFavorite)
        assertEquals("ing_1", recipe.ingredients.single().id)
        assertEquals(1, recipe.steps.single().order)
    }
}