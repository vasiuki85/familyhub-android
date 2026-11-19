package com.familyhub.ui.screens.recipes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.repository.RecipeRepository
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.domain.usecase.SyncChangesUseCase
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.model.RecipeIngredient
import com.familyhub.domain.model.RecipeStep
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.utils.IdGenerator
import com.familyhub.util.TimeUtils
import com.familyhub.ui.common.UserFacingErrorContext
import com.familyhub.ui.common.UserFacingErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class RecipeBookUiState(
    val isLoading: Boolean = true,
    val recipes: List<Recipe> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val currentUserId: String = "",
    val currentUserRole: MemberRole = MemberRole.PENDING,
    val activeShoppingListId: String = ""
) {
    val canMutateFamily: Boolean
        get() = currentUserRole != MemberRole.PENDING

    val filteredRecipes: List<Recipe>
        get() = if (searchQuery.isBlank()) {
            recipes
        } else {
            recipes.filter { recipe ->
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                    recipe.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
}

@HiltViewModel
class RecipeBookViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val memberRepository: FamilyMemberRepository,
    private val shoppingRepository: ShoppingRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val syncChangesUseCase: SyncChangesUseCase,
    private val idGenerator: IdGenerator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeBookUiState())
    val state: StateFlow<RecipeBookUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            try {
                combine(
                    recipeRepository.observeRecipes(),
                    appSettingsDataStore.settingsFlow,
                    memberRepository.getAllMembers(),
                ) { recipes, settings, members ->
                    Triple(recipes, settings, members)
                }.collect { (recipes, settings, members) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            recipes = recipes,
                            currentUserId = settings.currentUserId,
                            currentUserRole = members
                                .firstOrNull { member -> member.id == settings.currentUserId }
                                ?.role
                                ?: MemberRole.PENDING,
                            activeShoppingListId = settings.activeShoppingListId,
                            errorMessage = null
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = context.getString(R.string.recipes_error_load),
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun addRecipe(input: RecipeFormInput) {
        runRecipeMutation {
            val recipe = buildRecipeFromInput(input, original = null) ?: return@runRecipeMutation
            recipeRepository.upsertRecipe(recipe)
        }
    }

    fun updateRecipe(original: Recipe, input: RecipeFormInput) {
        runRecipeMutation {
            val updatedRecipe = buildRecipeFromInput(input, original) ?: return@runRecipeMutation
            recipeRepository.upsertRecipe(updatedRecipe)
        }
    }

    private fun buildRecipeFromInput(input: RecipeFormInput, original: Recipe?): Recipe? {
        val title = input.title.trim()
        val description = input.description.trim()
        val servings = input.servings.trim().toIntOrNull() ?: original?.servings ?: 4
        val prepTime = input.prepTimeMinutes.trim().toIntOrNull() ?: original?.prepTimeMinutes ?: 0
            val tags = input.tags.split(',')
            .mapNotNull { tag ->
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) trimmed else null
            }
        val ingredientsInput = input.ingredientsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val stepsInput = input.stepsText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (title.isBlank() || stepsInput.isEmpty()) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.recipes_error_form))
            }
            return null
        }

        val now = TimeUtils.now()
        val recipeId = original?.id ?: idGenerator.newId()

        val normalizedIngredients = ingredientsInput.mapIndexed { index, line ->
            val existing = original?.ingredients?.getOrNull(index)
            val name = line.substringBefore(':', line).trim().ifEmpty { line }
            val quantity = line.substringAfter(':', "").trim().ifEmpty { line }
            RecipeIngredient(
                id = existing?.id ?: idGenerator.newId(),
                recipeId = recipeId,
                name = name,
                quantity = quantity
            )
        }

        val normalizedSteps = stepsInput.mapIndexed { index, text ->
            val existing = original?.steps?.sortedBy { it.order }?.getOrNull(index)
            RecipeStep(
                id = existing?.id ?: idGenerator.newId(),
                recipeId = recipeId,
                order = index,
                description = text
            )
        }

        return Recipe(
            id = recipeId,
            title = title,
            description = description,
            servings = servings,
            prepTimeMinutes = prepTime,
            difficulty = input.difficulty,
            isFavorite = original?.isFavorite ?: false,
            imageUrl = input.imageUrl.trim().takeIf { it.isNotEmpty() },
            notes = input.notes.trim(),
            tags = tags,
            ingredients = normalizedIngredients,
            steps = normalizedSteps,
            category = input.category,
            createdAt = original?.createdAt ?: now,
            updatedAt = now
        )
    }

    fun deleteRecipe(recipe: Recipe) {
        runRecipeMutation {
            recipeRepository.deleteRecipe(recipe)
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        runRecipeMutation {
            recipeRepository.setFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    private fun runRecipeMutation(
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            runCatching {
                block()
                syncChangesUseCase(force = true)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Recipe,
                            tag = "RecipeBookViewModel",
                        ),
                    )
                }
            }
        }
    }

    fun exportIngredientsToShopping(recipe: Recipe, selectedIngredientIds: Set<String>? = null) {
        if (!_state.value.canMutateFamily) return
        viewModelScope.launch {
            val currentUserId = _state.value.currentUserId
            if (currentUserId.isBlank()) {
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.error_no_current_user))
                }
                return@launch
            }
            if (recipe.ingredients.isEmpty()) {
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.recipes_error_export_empty))
                }
                return@launch
            }

            runCatching {
                val listId = if (_state.value.activeShoppingListId.isNotBlank()) {
                    _state.value.activeShoppingListId
                } else {
                    shoppingRepository.ensureDefaultList().id
                }
                val existingTitles = shoppingRepository.getItemsSnapshot(listId)
                    .map { it.title.lowercase() }
                    .toSet()
                val now = TimeUtils.now()
                
                val sourceIngredients = if (selectedIngredientIds != null) {
                    recipe.ingredients.filter { selectedIngredientIds.contains(it.id) }
                } else {
                    recipe.ingredients
                }

                val items = sourceIngredients
                    .mapNotNull { ingredient ->
                        ingredient.name.trim().takeIf { it.isNotEmpty() }
                    }
                    .map { name ->
                        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    .distinct()
                    .filterNot { existingTitles.contains(it.lowercase()) }
                    .map { title ->
                        ShoppingItem(
                            id = idGenerator.newId(),
                            listId = listId,
                            title = title,
                            createdByUserId = currentUserId,
                            isBought = false,
                            createdAt = now
                        )
                    }

                if (items.isEmpty()) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.recipes_export_nothing_new))
                    }
                    return@runCatching
                }

                shoppingRepository.upsertItems(items)
                if (_state.value.activeShoppingListId.isBlank()) {
                    appSettingsDataStore.setActiveShoppingList(listId)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorMessage = UserFacingErrorMapper.message(
                            context,
                            error,
                            UserFacingErrorContext.Recipe,
                            tag = "RecipeBookViewModel",
                        ),
                    )
                }
            }
        }
    }
}

data class RecipeFormInput(
    val title: String,
    val description: String,
    val servings: String,
    val prepTimeMinutes: String,
    val ingredientsText: String,
    val stepsText: String,
    val tags: String,
    val difficulty: RecipeDifficulty,
    val category: RecipeCategory,
    val imageUrl: String,
    val notes: String
) {
    companion object {
        fun fromRecipe(recipe: Recipe): RecipeFormInput {
            return RecipeFormInput(
                title = recipe.title,
                description = recipe.description,
                servings = recipe.servings.toString(),
                prepTimeMinutes = recipe.prepTimeMinutes.toString(),
                ingredientsText = recipe.ingredients.joinToString(separator = "\n") {
                    "${it.name}: ${it.quantity}"
                },
                stepsText = recipe.steps
                    .sortedBy { it.order }
                    .joinToString(separator = "\n") { it.description },
                tags = recipe.tags.joinToString(separator = ","),
                difficulty = recipe.difficulty,
            category = recipe.category,
                imageUrl = recipe.imageUrl.orEmpty(),
                notes = recipe.notes
            )
        }
    }
}
