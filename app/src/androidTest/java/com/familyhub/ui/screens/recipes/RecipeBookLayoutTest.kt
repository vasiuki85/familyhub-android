package com.familyhub.ui.screens.recipes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.utils.BuiltInRecipeImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeBookLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun narrowRecipeCard_keepsTitleAndActionsVisibleWithBuiltInImage() {
        val recipe = sampleRecipe(
            imageUrl = BuiltInRecipeImage.tokens.first(),
        )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(240.dp)) {
                    RecipeCard(
                        recipe = recipe,
                        onShowDetails = {},
                        onToggleFavorite = {},
                        onDelete = {},
                        canManage = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText(recipe.title).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.recipes_v2_more_actions_cd),
        ).assertIsDisplayed()
    }

    @Test
    fun narrowFilterBar_scrollsCategoriesAndInvokesSelection() {
        val filters = listOf(
            RecipeCategoryFilter(
                category = null,
                labelRes = R.string.recipes_category_all,
                tagHints = emptyList(),
            ),
            RecipeCategoryFilter(
                category = RecipeCategory.FIRST,
                labelRes = R.string.recipes_category_first,
                tagHints = emptyList(),
            ),
            RecipeCategoryFilter(
                category = RecipeCategory.SECOND,
                labelRes = R.string.recipes_category_second,
                tagHints = emptyList(),
            ),
        )
        var selectedIndex = 0

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(280.dp)) {
                    RecipeBookFilterBar(
                        searchQuery = "",
                        selectedCategoryIndex = selectedIndex,
                        categoryFilters = filters,
                        onSearchQueryChanged = {},
                        onCategorySelected = { selectedIndex = it },
                    )
                }
            }
        }

        val all = composeRule.activity.getString(R.string.recipes_category_all)
        val first = composeRule.activity.getString(R.string.recipes_category_first)
        composeRule.onNodeWithText(all).assertIsSelected()
        composeRule.onNodeWithText(first).performClick()
        composeRule.runOnIdle {
            assertEquals(1, selectedIndex)
        }
    }

    private fun sampleRecipe(imageUrl: String?): Recipe = Recipe(
        id = "recipe",
        title = "A very long family recipe title for a narrow phone",
        description = "Description",
        servings = 4,
        prepTimeMinutes = 30,
        difficulty = RecipeDifficulty.EASY,
        isFavorite = false,
        imageUrl = imageUrl,
        notes = "",
        tags = listOf("family"),
        ingredients = emptyList(),
        steps = emptyList(),
        category = RecipeCategory.FIRST,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
