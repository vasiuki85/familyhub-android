package com.familyhub.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.Recipe
import com.familyhub.ui.components.FhIcons
import com.familyhub.ui.screens.food.MealTypeIconMapper

/** Maximum height budget for one meal row at phone width (see [WeeklyMenuMealRowLayoutTest]). */
const val WEEKLY_MENU_MEAL_ROW_MAX_HEIGHT_DP = 168

/** Combined height budget for four meal rows on one screen. */
const val WEEKLY_MENU_FOUR_MEALS_MAX_HEIGHT_DP = 664

@Composable
fun WeeklyMenuMealRow(
    mealType: MealType,
    entry: MenuEntry?,
    recipe: Recipe?,
    canManageMenu: Boolean,
    readOnlyEditCd: String,
    onOpen: () -> Unit,
    onOpenRecipe: () -> Unit,
    onReplace: () -> Unit,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dishTitle = when {
        recipe != null -> recipe.title
        entry?.customDish?.isNotBlank() == true -> entry.customDish
        else -> null
    }
    val hasDish = dishTitle != null
    val openDescription = if (hasDish) {
        stringResource(R.string.menu_edit_meal_cd)
    } else {
        stringResource(R.string.menu_add_meal_cd)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val moreActionsLabel = stringResource(
        R.string.menu_meal_more_actions_cd,
        mealLabel(mealType),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = canManageMenu, onClick = onOpen)
            .semantics {
                contentDescription = if (canManageMenu) openDescription else readOnlyEditCd
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = MealTypeIconMapper.iconFor(mealType),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = mealLabel(mealType),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasDish) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = canManageMenu,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = FhIcons.MoreVert,
                                contentDescription = moreActionsLabel,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (recipe != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_meal_open_recipe)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenRecipe()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_meal_replace_dish)) },
                                onClick = {
                                    menuExpanded = false
                                    onReplace()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_meal_assign_cook)) },
                                onClick = {
                                    menuExpanded = false
                                    onAssign()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_meal_remove)) },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = dishTitle ?: stringResource(R.string.menu_add_meal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasDish) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry != null && entry.servings > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.menu_meal_servings,
                        entry.servings,
                        entry.servings,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun mealLabel(mealType: MealType): String = stringResource(
    when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snack
    },
)
