package com.familyhub.ui.screens.food

import com.familyhub.ui.components.FhIcons
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhub.R
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.Recipe
import com.familyhub.ui.components.FhListSkeleton
import com.familyhub.ui.components.FhScreenScaffold
import com.familyhub.ui.screens.menu.WeeklyMenuViewModel
import com.familyhub.ui.theme.FhLayout
import com.familyhub.ui.theme.FhTheme
import com.familyhub.util.TimeUtils

@Composable
fun FoodScreen(
    onOpenWeeklyMenu: () -> Unit,
    onOpenRecipes: () -> Unit,
    viewModel: WeeklyMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uiModel = remember(state.menu, state.recipes) {
        FoodHubUiMapper.map(
            menu = state.menu,
            recipes = state.recipes,
            nowMillis = TimeUtils.now(),
        )
    }

    FhScreenScaffold(
        title = stringResource(R.string.food_hub_title),
        errorMessage = state.errorMessage,
        onErrorDismiss = viewModel::clearError,
    ) { paddingValues ->
        if (state.isLoading) {
            FhListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = FhTheme.spacing.lg,
                    top = FhTheme.spacing.sm,
                    end = FhTheme.spacing.lg,
                    bottom = FhLayout.listBottomPadding(hasFloatingActionButton = false),
                ),
                verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.lg),
            ) {
                item(key = "food_intro") {
                    FoodIntroCard()
                }

                item(key = "today_header") {
                    FoodSectionHeader(
                        title = stringResource(R.string.food_hub_today_title),
                        actionLabel = stringResource(R.string.food_hub_open_weekly_menu),
                        onAction = onOpenWeeklyMenu,
                    )
                }

                item(key = "today_menu") {
                    TodayMenuCard(
                        meals = uiModel.todayMeals,
                        onOpenWeeklyMenu = onOpenWeeklyMenu,
                    )
                }

                item(key = "recipes_header") {
                    FoodSectionHeader(
                        title = stringResource(R.string.food_hub_recipe_preview_title),
                        actionLabel = stringResource(R.string.food_hub_open_recipes),
                        onAction = onOpenRecipes,
                    )
                }

                if (uiModel.recipePreviews.isEmpty()) {
                    item(key = "recipes_empty") {
                        FoodEmptyCard(
                            icon = FhIcons.MenuBook,
                            message = stringResource(R.string.food_hub_recipes_empty),
                            actionLabel = stringResource(R.string.food_hub_open_recipes),
                            onClick = onOpenRecipes,
                        )
                    }
                } else {
                    item(key = "recipe_previews") {
                        LazyRow(
                            contentPadding = PaddingValues(end = FhTheme.spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                        ) {
                            items(uiModel.recipePreviews, key = Recipe::id) { recipe ->
                                RecipePreviewCard(
                                    recipe = recipe,
                                    onClick = onOpenRecipes,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodIntroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = FhTheme.modules.food.container,
        contentColor = FhTheme.modules.food.onContainer,
    ) {
        BoxWithConstraints {
            val layout = FoodScreenLayoutPolicy.introLayout(maxWidth)
            Row(
                modifier = Modifier.padding(layout.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(layout.spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.fh_illustration_food_v2),
                    contentDescription = stringResource(R.string.food_hub_illustration_cd),
                    modifier = Modifier
                        .width(layout.illustrationWidth)
                        .height(layout.illustrationHeight),
                    contentScale = ContentScale.Fit,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.food_hub_intro_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.food_hub_intro_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FhTheme.modules.food.onContainer.copy(alpha = 0.78f),
                    )
                }
                if (layout.showAccentIcon) {
                    Icon(
                        imageVector = FhIcons.AutoAwesome,
                        contentDescription = null,
                        tint = FhTheme.modules.food.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (FoodScreenLayoutPolicy.stackSectionHeader(maxWidth)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(actionLabel)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayMenuCard(
    meals: List<FoodMealPreview>,
    onOpenWeeklyMenu: () -> Unit,
) {
    if (meals.isEmpty()) {
        FoodEmptyCard(
            icon = FhIcons.RestaurantMenu,
            message = stringResource(R.string.food_hub_today_empty),
            actionLabel = stringResource(R.string.food_hub_plan_today),
            onClick = onOpenWeeklyMenu,
        )
        return
    }

    Surface(
        onClick = onOpenWeeklyMenu,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = FhTheme.modules.food.container,
        contentColor = FhTheme.modules.food.onContainer,
    ) {
        Column(modifier = Modifier.padding(FhTheme.spacing.md)) {
            meals.forEach { meal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = FhTheme.spacing.sm,
                            vertical = FhTheme.spacing.sm,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = FhTheme.modules.food.accent.copy(alpha = 0.14f),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MealTypeIconMapper.iconFor(meal.mealType),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = FhTheme.modules.food.onContainer.copy(alpha = 0.72f),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mealTypeLabel(meal.mealType),
                            style = MaterialTheme.typography.labelMedium,
                            color = FhTheme.modules.food.onContainer.copy(alpha = 0.72f),
                        )
                        Text(
                            text = meal.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipePreviewCard(
    recipe: Recipe,
    onClick: () -> Unit,
) {
    val description = stringResource(
        R.string.food_hub_recipe_card_cd,
        recipe.title,
        recipe.prepTimeMinutes,
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(210.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(FhTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = FhIcons.MenuBook,
                    contentDescription = null,
                    tint = FhTheme.modules.food.accent,
                )
                if (recipe.isFavorite) {
                    Icon(
                        imageVector = FhIcons.Star,
                        contentDescription = stringResource(R.string.food_hub_favorite_recipe_cd),
                        tint = FhTheme.modules.reward.accent,
                    )
                }
            }
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.food_hub_recipe_time,
                    recipe.prepTimeMinutes,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FoodEmptyCard(
    icon: ImageVector,
    message: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints {
            if (FoodScreenLayoutPolicy.stackEmptyCard(maxWidth)) {
                Column(
                    modifier = Modifier.padding(FhTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = FhTheme.modules.food.accent,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(actionLabel)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(FhTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = FhTheme.modules.food.accent,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClick) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

internal data class FoodIntroLayout(
    val illustrationWidth: androidx.compose.ui.unit.Dp,
    val illustrationHeight: androidx.compose.ui.unit.Dp,
    val contentPadding: androidx.compose.ui.unit.Dp,
    val spacing: androidx.compose.ui.unit.Dp,
    val showAccentIcon: Boolean,
)

internal object FoodScreenLayoutPolicy {
    private val CompactWidth = 360.dp
    private val CompactSectionHeaderWidth = 320.dp

    fun introLayout(availableWidth: androidx.compose.ui.unit.Dp): FoodIntroLayout =
        if (availableWidth < CompactWidth) {
            FoodIntroLayout(
                illustrationWidth = 82.dp,
                illustrationHeight = 72.dp,
                contentPadding = 16.dp,
                spacing = 12.dp,
                showAccentIcon = false,
            )
        } else {
            FoodIntroLayout(
                illustrationWidth = 112.dp,
                illustrationHeight = 92.dp,
                contentPadding = 20.dp,
                spacing = 16.dp,
                showAccentIcon = true,
            )
        }

    fun stackEmptyCard(availableWidth: androidx.compose.ui.unit.Dp): Boolean =
        availableWidth < CompactWidth

    fun stackSectionHeader(availableWidth: androidx.compose.ui.unit.Dp): Boolean =
        availableWidth < CompactSectionHeaderWidth
}

@Composable
private fun mealTypeLabel(mealType: MealType): String = stringResource(
    when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.food_hub_meal_snack
    },
)
