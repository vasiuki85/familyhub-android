package com.familyhub.ui.screens.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyhub.R
import com.familyhub.domain.model.Recipe
import com.familyhub.domain.model.RecipeCategory
import com.familyhub.domain.model.RecipeDifficulty
import com.familyhub.domain.utils.BuiltInRecipeImage

import com.familyhub.domain.model.RecipeIngredient
import com.familyhub.ui.components.FhEmptyState
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.components.FhScrollableHorizontalRow
import com.familyhub.ui.components.DeleteConfirmationDialog
import com.familyhub.ui.navigation.FoodSectionTab
import com.familyhub.ui.screens.food.FoodSectionTabs
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import com.familyhub.ui.components.RemoteImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookScreen(
    onNavigateToWeeklyMenu: () -> Unit = {},
    viewModel: RecipeBookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedCategoryIndex by rememberSaveable { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeToEdit by remember { mutableStateOf<Recipe?>(null) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }
    var recipeForIngredientExport by remember { mutableStateOf<Recipe?>(null) }

    FhScreenScaffold(
        title = stringResource(R.string.food_hub_title),
        collapsingTopBar = false,
        errorMessage = state.errorMessage,
        onErrorDismiss = { viewModel.clearError() },
        floatingActionButton = {
            if (state.canMutateFamily) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = FhTheme.modules.food.accent,
                    contentColor = FhTheme.modules.food.onAccent,
                ) {
                    Icon(
                        imageVector = FhIcons.Add,
                        contentDescription = stringResource(R.string.recipes_add_recipe)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clipToBounds()
        ) {
            FoodSectionTabs(
                selectedTab = FoodSectionTab.RECIPES,
                onTabSelected = { tab ->
                    if (tab == FoodSectionTab.WEEKLY_MENU) {
                        onNavigateToWeeklyMenu()
                    }
                },
            )
            val categoryFilters = recipeCategoryFilters
            RecipeBookFilterBar(
                searchQuery = state.searchQuery,
                selectedCategoryIndex = selectedCategoryIndex,
                categoryFilters = categoryFilters,
                onSearchQueryChanged = viewModel::setSearchQuery,
                onCategorySelected = { selectedCategoryIndex = it },
            )

            val selectedFilter = categoryFilters[selectedCategoryIndex.coerceIn(categoryFilters.indices)]
            val recipesToShow = state.filteredRecipes.filter { recipe ->
                recipe.matchesCategory(selectedFilter)
            }

            if (state.isLoading) {
                FhListSkeleton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (recipesToShow.isEmpty()) {
                FhEmptyState(
                    icon = FhIcons.MenuBook,
                    title = stringResource(
                        if (state.searchQuery.isNotBlank() || selectedCategoryIndex != 0) {
                            R.string.recipes_v2_empty_filtered
                        } else {
                            R.string.recipes_empty
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = FhTheme.spacing.lg,
                        top = FhTheme.spacing.xs,
                        end = FhTheme.spacing.lg,
                        bottom = RecipeBookLayoutPolicy.listBottomPadding(
                            hasFloatingActionButton = state.canMutateFamily,
                        ),
                    ),
                    verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                ) {
                    items(recipesToShow, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            modifier = Modifier.animateItem(),
                            onShowDetails = { selectedRecipe = recipe },
                            onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                            onDelete = { recipeToDelete = recipe },
                            canManage = state.canMutateFamily,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && state.canMutateFamily) {
        RecipeEditorDialog(
            titleRes = R.string.recipes_dialog_title_add,
            initialInput = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { input ->
                viewModel.addRecipe(input)
                showAddDialog = false
            }
        )
    }

    selectedRecipe?.let { recipe ->
        RecipeDetailsDialog(
            recipe = recipe,
            onDismiss = { selectedRecipe = null },
            onDelete = {
                recipeToDelete = recipe
                selectedRecipe = null
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(recipe)
            },
            onEdit = {
                recipeToEdit = recipe
                selectedRecipe = null
            },
            onExportIngredients = {
                recipeForIngredientExport = recipe
            },
            canManage = state.canMutateFamily,
        )
    }

    recipeToEdit?.takeIf { state.canMutateFamily }?.let { recipe ->
        RecipeEditorDialog(
            titleRes = R.string.recipes_dialog_title_edit,
            initialInput = RecipeFormInput.fromRecipe(recipe),
            onDismiss = { recipeToEdit = null },
            onConfirm = { input ->
                viewModel.updateRecipe(recipe, input)
                recipeToEdit = null
            }
        )
    }

    recipeToDelete?.takeIf { state.canMutateFamily }?.let { recipe ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.recipes_v2_delete_title),
            message = stringResource(R.string.recipes_v2_delete_message, recipe.title),
            onDismiss = { recipeToDelete = null },
            onConfirm = { viewModel.deleteRecipe(recipe) },
        )
    }


    recipeForIngredientExport?.takeIf { state.canMutateFamily }?.let { recipe ->
        IngredientSelectionDialog(
            ingredients = recipe.ingredients,
            onDismiss = { recipeForIngredientExport = null },
            onConfirm = { selectedIds ->
                viewModel.exportIngredientsToShopping(recipe, selectedIds)
                recipeForIngredientExport = null
            }
        )
    }
}

@Composable
internal fun RecipeBookFilterBar(
    searchQuery: String,
    selectedCategoryIndex: Int,
    categoryFilters: List<RecipeCategoryFilter>,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(RecipeBookLayoutPolicy.FILTER_BAR_Z_INDEX),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column {
            val searchContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = FhIcons.Close,
                                contentDescription = stringResource(R.string.recipes_v2_clear_search_cd),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FhTheme.spacing.lg,
                        top = FhTheme.spacing.xs,
                        end = FhTheme.spacing.lg,
                    ),
                singleLine = true,
                shape = RoundedCornerShape(FhTheme.radius.field),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = searchContainerColor,
                    unfocusedContainerColor = searchContainerColor,
                    disabledContainerColor = searchContainerColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = FhTheme.modules.food.accent,
                    focusedLeadingIconColor = FhTheme.modules.food.accent,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            FhScrollableHorizontalRow(
                selectedIndex = selectedCategoryIndex,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = FhTheme.spacing.lg,
                    top = FhTheme.spacing.xs,
                    end = FhTheme.spacing.lg,
                    bottom = FhTheme.spacing.xs,
                ),
                horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
                fadeColor = MaterialTheme.colorScheme.surface,
            ) {
                items(categoryFilters.size) { index ->
                    val category = categoryFilters[index]
                    val isSelected = selectedCategoryIndex == index
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(index) },
                        label = { Text(stringResource(category.labelRes)) },
                        shape = RoundedCornerShape(FhTheme.radius.chip),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = FhTheme.modules.food.container,
                            selectedLabelColor = FhTheme.modules.food.onContainer,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                FhTheme.modules.food.accent.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecipeCard(
    recipe: Recipe,
    modifier: Modifier = Modifier,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    canManage: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onShowDetails,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FhTheme.radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val layout = RecipeBookLayoutPolicy.cardLayout(maxWidth)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(layout.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(layout.horizontalSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Box(
                modifier = Modifier
                    .size(layout.imageSize)
                    .clip(RoundedCornerShape(FhTheme.radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FhTheme.modules.food.container,
                    contentColor = FhTheme.modules.food.accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = FhIcons.RestaurantMenu,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                recipe.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    RemoteImage(
                        url = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canManage) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = FhIcons.MoreVert,
                                    contentDescription = stringResource(R.string.recipes_v2_more_actions_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (recipe.isFavorite) {
                                                    R.string.recipes_unfavorite_cd
                                                } else {
                                                    R.string.recipes_favorite_cd
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (recipe.isFavorite) {
                                                FhIcons.Favorite
                                            } else {
                                                FhIcons.FavoriteBorder
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFavorite()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.recipes_delete_cd)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = FhIcons.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(
                        R.string.recipes_v2_meta,
                        recipe.prepTimeMinutes,
                        recipe.servings,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = FhTheme.modules.food.accent,
                    maxLines = 1,
                )

                if (recipe.description.isNotBlank()) {
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (recipe.tags.isNotEmpty()) {
                    Text(
                        text = recipe.tags.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            }
        }
    }
}

internal data class RecipeCardLayout(
    val imageSize: Dp,
    val contentPadding: Dp,
    val horizontalSpacing: Dp,
)

internal object RecipeBookLayoutPolicy {
    const val FILTER_BAR_Z_INDEX = 1f

    private val ExtraCompactWidth = 300.dp
    private val CompactWidth = 360.dp

    fun cardLayout(availableWidth: Dp): RecipeCardLayout =
        if (availableWidth < ExtraCompactWidth) {
            RecipeCardLayout(
                imageSize = 56.dp,
                contentPadding = 8.dp,
                horizontalSpacing = 8.dp,
            )
        } else if (availableWidth < CompactWidth) {
            RecipeCardLayout(
                imageSize = 64.dp,
                contentPadding = 12.dp,
                horizontalSpacing = 8.dp,
            )
        } else {
            RecipeCardLayout(
                imageSize = 86.dp,
                contentPadding = 12.dp,
                horizontalSpacing = 12.dp,
            )
        }

    fun listBottomPadding(hasFloatingActionButton: Boolean): Dp =
        FhLayout.listBottomPadding(hasFloatingActionButton)
}

internal data class RecipeCategoryFilter(
    val category: RecipeCategory?,
    val labelRes: Int,
    val tagHints: List<String>
)

private val recipeCategoryFilters = listOf(
    RecipeCategoryFilter(null, R.string.recipes_category_all, emptyList()),
    RecipeCategoryFilter(
        RecipeCategory.FIRST,
        R.string.recipes_category_first,
        listOf("первое", "первые", "суп", "first", "soup")
    ),
    RecipeCategoryFilter(
        RecipeCategory.SECOND,
        R.string.recipes_category_second,
        listOf("второе", "основное", "main", "second")
    ),
    RecipeCategoryFilter(
        RecipeCategory.APPETIZER,
        R.string.recipes_category_snacks,
        listOf("закуска", "snack", "appetizer")
    ),
    RecipeCategoryFilter(
        RecipeCategory.SALAD,
        R.string.recipes_category_salads,
        listOf("салат", "salad")
    )
)

private fun Recipe.matchesCategory(filter: RecipeCategoryFilter): Boolean {
    val targetCategory = filter.category ?: return true
    if (category == targetCategory) return true
    val normalizedTags = tags.map { it.lowercase(Locale.getDefault()) }
    return filter.tagHints.any { hint ->
        normalizedTags.any { it.contains(hint) }
    }
}

@Composable
private fun RecipeCategorySelector(
    selected: RecipeCategory,
    onSelected: (RecipeCategory) -> Unit
) {
    val categories = recipeCategoryFilters.mapNotNull { it.category }
    val selectedIndex = categories.indexOf(selected).coerceAtLeast(0)
    Column {
        Text(
            text = stringResource(R.string.recipes_field_category),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        FhScrollableHorizontalRow(
            selectedIndex = selectedIndex,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories.size) { index ->
                val category = categories[index]
                val definition = recipeCategoryFilters.first { it.category == category }
                val isSelected = category == selected
                AssistChip(
                    onClick = { onSelected(category) },
                    label = { Text(stringResource(definition.labelRes)) },
                    shape = MaterialTheme.shapes.small,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        labelColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}

@Composable
internal fun RecipeDetailsDialog(
    recipe: Recipe,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onExportIngredients: () -> Unit,
    canManage: Boolean,
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = FhIcons.Close,
                        contentDescription = stringResource(R.string.common_close)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                item {
                    if (recipe.description.isNotBlank()) {
                        Text(
                            text = recipe.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.recipes_details_servings_format, recipe.servings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.recipes_details_prep_time_format, recipe.prepTimeMinutes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.recipes_details_difficulty_format,
                            difficultyLabel(recipe.difficulty)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    recipe.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        Spacer(modifier = Modifier.height(12.dp))
                        RemoteImage(
                            url = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (recipe.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.recipes_notes_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = recipe.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.recipes_details_ingredients),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(recipe.ingredients, key = { it.id }) { ingredient ->
                    val ingredientText = if (ingredient.quantity.isNotBlank() && ingredient.quantity != ingredient.name) {
                        "${ingredient.name} — ${ingredient.quantity}"
                    } else {
                        ingredient.name
                    }
                    Text(
                        modifier = Modifier.animateItem(),
                        text = "• $ingredientText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.recipes_details_steps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(recipe.steps.sortedBy { it.order }, key = { it.id }) { step ->
                    Text(
                        modifier = Modifier.animateItem(),
                        text = "${step.order + 1}. ${step.description}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onExportIngredients) {
                        Icon(
                            imageVector = FhIcons.ShoppingBag,
                            contentDescription = stringResource(R.string.recipes_export_to_shopping)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = FhIcons.Edit,
                            contentDescription = stringResource(R.string.recipes_edit_recipe)
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (recipe.isFavorite) FhIcons.Favorite else FhIcons.FavoriteBorder,
                            contentDescription = if (recipe.isFavorite) stringResource(R.string.recipes_unfavorite_cd)
                            else stringResource(R.string.recipes_favorite_cd)
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (canManage) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = FhIcons.Delete,
                        contentDescription = stringResource(R.string.recipes_delete_cd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditorDialog(
    titleRes: Int,
    initialInput: RecipeFormInput?,
    onDismiss: () -> Unit,
    onConfirm: (RecipeFormInput) -> Unit
) {
    var title by remember(initialInput) { mutableStateOf(initialInput?.title ?: "") }
    var titleError by remember { mutableStateOf<String?>(null) }
    val titleRequiredMessage = stringResource(R.string.validation_recipe_title_empty)
    var description by remember(initialInput) { mutableStateOf(initialInput?.description ?: "") }
    var servings by remember(initialInput) { mutableStateOf(initialInput?.servings ?: "4") }
    var prepTime by remember(initialInput) { mutableStateOf(initialInput?.prepTimeMinutes ?: "30") }
    var ingredients by remember(initialInput) { mutableStateOf(initialInput?.ingredientsText ?: "") }
    var steps by remember(initialInput) { mutableStateOf(initialInput?.stepsText ?: "") }
    var tags by remember(initialInput) { mutableStateOf(initialInput?.tags ?: "") }
    val bundledImageToken = remember(initialInput) {
        initialInput?.imageUrl?.takeIf(BuiltInRecipeImage::isToken)
    }
    var imageUrl by remember(initialInput) {
        mutableStateOf(initialInput?.imageUrl?.takeUnless(BuiltInRecipeImage::isToken) ?: "")
    }
    var notes by remember(initialInput) { mutableStateOf(initialInput?.notes ?: "") }
    var selectedDifficulty by remember(initialInput) { mutableStateOf(initialInput?.difficulty ?: RecipeDifficulty.MEDIUM) }
    var selectedCategory by remember(initialInput) { mutableStateOf(initialInput?.category ?: RecipeCategory.FIRST) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(titleRes),
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = FhIcons.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = FhTheme.spacing.lg,
                                    vertical = FhTheme.spacing.md,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.recipes_cancel))
                            }
                            Button(
                                onClick = {
                                    if (title.isBlank()) {
                                        titleError = titleRequiredMessage
                                        return@Button
                                    }
                                    titleError = null
                                    onConfirm(
                                        RecipeFormInput(
                                            title = title,
                                            description = description,
                                            servings = servings,
                                            prepTimeMinutes = prepTime,
                                            ingredientsText = ingredients,
                                            stepsText = steps,
                                            tags = tags,
                                            difficulty = selectedDifficulty,
                                            category = selectedCategory,
                                            imageUrl = imageUrl.trim().ifBlank {
                                                bundledImageToken.orEmpty()
                                            },
                                            notes = notes,
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.recipes_save))
                            }
                        }
                    }
                },
            ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .imePadding()
                    .padding(horizontal = FhTheme.spacing.lg),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = FhTheme.spacing.md,
                    bottom = FhTheme.spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            titleError = null
                        },
                        label = { Text(stringResource(R.string.recipes_field_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = titleError != null,
                        supportingText = titleError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.recipes_field_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                item {
                    RecipeMetricsFields(
                        servings = servings,
                        onServingsChanged = { value ->
                            servings = value.filter { character -> character.isDigit() }
                        },
                        prepTime = prepTime,
                        onPrepTimeChanged = { value ->
                            prepTime = value.filter { character -> character.isDigit() }
                        },
                    )
                }
                item {
                    DifficultySelector(
                        selected = selectedDifficulty,
                        onSelected = { selectedDifficulty = it }
                    )
                }
                item {
                    RecipeCategorySelector(
                        selected = selectedCategory,
                        onSelected = { selectedCategory = it }
                    )
                }
                item {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text(stringResource(R.string.recipes_field_image_url)) },
                        placeholder = { Text(stringResource(R.string.recipes_field_image_url_hint)) },
                        supportingText = {
                            Text(stringResource(R.string.recipes_field_image_url_help))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = ingredients,
                        onValueChange = { ingredients = it },
                        label = { Text(stringResource(R.string.recipes_field_ingredients)) },
                        placeholder = { Text(stringResource(R.string.recipes_ingredients_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                item {
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it },
                        label = { Text(stringResource(R.string.recipes_field_steps)) },
                        placeholder = { Text(stringResource(R.string.recipes_steps_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                item {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text(stringResource(R.string.recipes_field_tags)) },
                        placeholder = { Text(stringResource(R.string.recipes_tags_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.recipes_field_notes)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RecipeMetricsFields(
    servings: String,
    onServingsChanged: (String) -> Unit,
    prepTime: String,
    onPrepTimeChanged: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useVerticalLayout = maxWidth < 300.dp
        if (useVerticalLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.md)) {
                RecipeMetricField(
                    value = servings,
                    onValueChanged = onServingsChanged,
                    labelRes = R.string.recipes_field_servings,
                    modifier = Modifier.fillMaxWidth(),
                )
                RecipeMetricField(
                    value = prepTime,
                    onValueChanged = onPrepTimeChanged,
                    labelRes = R.string.recipes_field_prep_time,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md)) {
                RecipeMetricField(
                    value = servings,
                    onValueChanged = onServingsChanged,
                    labelRes = R.string.recipes_field_servings,
                    modifier = Modifier.weight(1f),
                )
                RecipeMetricField(
                    value = prepTime,
                    onValueChanged = onPrepTimeChanged,
                    labelRes = R.string.recipes_field_prep_time,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecipeMetricField(
    value: String,
    onValueChanged: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(stringResource(labelRes)) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifficultySelector(
    selected: RecipeDifficulty,
    onSelected: (RecipeDifficulty) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.recipes_field_difficulty),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecipeDifficulty.values().forEach { difficulty ->
                AssistChip(
                    onClick = { onSelected(difficulty) },
                    label = { Text(difficultyLabel(difficulty)) },
                    leadingIcon = null,
                    shape = MaterialTheme.shapes.small,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (difficulty == selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        labelColor = if (difficulty == selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: RecipeDifficulty): String {
    return when (difficulty) {
        RecipeDifficulty.EASY -> stringResource(R.string.recipes_difficulty_easy)
        RecipeDifficulty.MEDIUM -> stringResource(R.string.recipes_difficulty_medium)
        RecipeDifficulty.HARD -> stringResource(R.string.recipes_difficulty_hard)
    }
}

@Composable
private fun IngredientSelectionDialog(
    ingredients: List<RecipeIngredient>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    // Initially select all
    val selectedIds = remember { mutableStateOf(ingredients.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.recipes_details_ingredients))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ingredients, key = { it.id }) { ingredient ->
                    val isSelected = selectedIds.value.contains(ingredient.id)
                    val ingredientToggleLabel = stringResource(
                        R.string.recipes_ingredient_toggle_cd,
                        ingredient.name,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clickable {
                                val current = selectedIds.value
                                selectedIds.value = if (isSelected) {
                                    current - ingredient.id
                                } else {
                                    current + ingredient.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                val current = selectedIds.value
                                selectedIds.value = if (checked) {
                                    current + ingredient.id
                                } else {
                                    current - ingredient.id
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = ingredientToggleLabel },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ingredient.name + if (ingredient.quantity.isNotBlank() && ingredient.quantity != ingredient.name) " (${ingredient.quantity})" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIds.value) },
                enabled = selectedIds.value.isNotEmpty()
            ) {
                Text(stringResource(R.string.recipes_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
