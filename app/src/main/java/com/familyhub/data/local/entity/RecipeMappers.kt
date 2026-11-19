package com.familyhub.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.model.RecipeIngredient
import com.familyhub.domain.model.RecipeStep
import com.familyhub.util.fixMojibakeIfNeeded

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId"
    )
    val ingredients: List<RecipeIngredientEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId"
    )
    val steps: List<RecipeStepEntity>
)

fun RecipeWithDetails.toDomain(): Recipe = Recipe(
    id = recipe.id,
    title = recipe.title.fixMojibakeIfNeeded(),
    description = recipe.description.fixMojibakeIfNeeded(),
    servings = recipe.servings,
    prepTimeMinutes = recipe.prepTimeMinutes,
    difficulty = RecipeDifficulty.valueOf(recipe.difficulty),
    isFavorite = recipe.isFavorite,
    imageUrl = recipe.imageUrl,
    notes = recipe.notes.fixMojibakeIfNeeded(),
    tags = recipe.tags,
    ingredients = ingredients.map { entity ->
        RecipeIngredient(
            id = entity.id,
            recipeId = entity.recipeId,
            name = entity.name.fixMojibakeIfNeeded(),
            quantity = entity.quantity
        )
    },
    steps = steps.sortedBy { it.stepOrder }.map { entity ->
        RecipeStep(
            id = entity.id,
            recipeId = entity.recipeId,
            order = entity.stepOrder,
            description = entity.description.fixMojibakeIfNeeded()
        )
    },
    category = RecipeCategory.valueOf(recipe.category),
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt
)

data class RecipeEntities(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
    val steps: List<RecipeStepEntity>
)

fun Recipe.toEntities(): RecipeEntities {
    val recipeEntity = RecipeEntity(
        id = id,
        title = title,
        description = description,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        difficulty = difficulty.name,
        isFavorite = isFavorite,
        imageUrl = imageUrl,
        notes = notes,
        tags = tags,
        category = category.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    val ingredientEntities = ingredients.map { ingredient ->
        RecipeIngredientEntity(
            id = ingredient.id,
            recipeId = ingredient.recipeId,
            name = ingredient.name,
            quantity = ingredient.quantity
        )
    }
    val stepEntities = steps.map { step ->
        RecipeStepEntity(
            id = step.id,
            recipeId = step.recipeId,
            stepOrder = step.order,
            description = step.description
        )
    }
    return RecipeEntities(
        recipe = recipeEntity,
        ingredients = ingredientEntities,
        steps = stepEntities
    )
}
