package com.familyhub.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.familyhub.R
import com.familyhub.ui.theme.FhModulePalette
import com.familyhub.ui.theme.FhTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreDestinationsSheet(
    navController: NavController,
    barRoutes: List<String>,
    currentRoute: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groupedItems = getGroupedMoreDestinationItems(barRoutes)
    val sectionGap = FhTheme.spacing.md - FhTheme.spacing.sm

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = FhTheme.spacing.lg,
                vertical = FhTheme.spacing.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.nav_more_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.foundation_v2_more_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = sectionGap),
                )
            }

            groupedItems.forEach { (group, items) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            top = sectionGap,
                            bottom = sectionGap,
                        ),
                    )
                }
                items(items, key = { it.route }) { item ->
                    MoreDestinationTile(
                        item = item,
                        selected = isBottomNavRouteActive(currentRoute, item.route),
                        onClick = {
                            navController.navigateToBottomNavDestination(item.route)
                            onDismiss()
                        },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = {
                        navController.navigateToBottomNavDestination(NavRoutes.SETTINGS)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = sectionGap)
                        .heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = FamilyHubNavIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(FhTheme.spacing.sm))
                    Text(stringResource(R.string.foundation_v2_more_settings))
                }
            }
        }
    }
}

internal fun getMoreDestinationItems(barRoutes: List<String>): List<BottomNavItem> =
    bottomNavItems.filter { item ->
        item.route !in barRoutes && item.route != NavRoutes.SETTINGS
    }

internal enum class MoreDestinationTone {
    ACCENT,
    TASK,
    SHOPPING,
    FOOD,
    REWARD,
    GOALS,
    ATTENTION,
}

internal fun moreDestinationTone(route: String): MoreDestinationTone = when (route) {
    NavRoutes.TASKS,
    NavRoutes.RULES,
    NavRoutes.APPRECIATIONS,
    NavRoutes.CONTACTS -> MoreDestinationTone.TASK
    NavRoutes.GOALS -> MoreDestinationTone.GOALS
    NavRoutes.SHOPPING,
    NavRoutes.CALENDAR,
    NavRoutes.STATS,
    NavRoutes.REPORT -> MoreDestinationTone.SHOPPING
    NavRoutes.FOOD,
    NavRoutes.MENU,
    NavRoutes.RECIPES -> MoreDestinationTone.FOOD
    NavRoutes.RATING,
    NavRoutes.REWARDS,
    NavRoutes.AWESOMENESS -> MoreDestinationTone.REWARD
    NavRoutes.PENALTIES -> MoreDestinationTone.ATTENTION
    else -> MoreDestinationTone.ACCENT
}

@Composable
private fun moreDestinationPalette(route: String): FhModulePalette = when (moreDestinationTone(route)) {
    MoreDestinationTone.TASK -> FhTheme.modules.task
    MoreDestinationTone.SHOPPING -> FhTheme.modules.shopping
    MoreDestinationTone.FOOD -> FhTheme.modules.food
    MoreDestinationTone.REWARD -> FhTheme.modules.reward
    MoreDestinationTone.GOALS -> FhTheme.modules.goals
    MoreDestinationTone.ATTENTION -> FhModulePalette(
        accent = MaterialTheme.colorScheme.error,
        onAccent = MaterialTheme.colorScheme.onError,
        container = MaterialTheme.colorScheme.errorContainer,
        onContainer = MaterialTheme.colorScheme.onErrorContainer,
    )
    MoreDestinationTone.ACCENT -> FhModulePalette(
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
        container = MaterialTheme.colorScheme.primaryContainer,
        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun MoreDestinationTile(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FhTheme.radius.card)
    val palette = moreDestinationPalette(item.route)

    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) palette.accent.copy(alpha = 0.16f) else palette.container,
        contentColor = palette.onContainer,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) {
            BorderStroke(2.dp, palette.accent)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FhTheme.spacing.sm),
            modifier = Modifier.padding(
                horizontal = FhTheme.spacing.xs,
                vertical = FhTheme.spacing.md,
            ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.accent,
                    contentColor = palette.onAccent,
                    modifier = Modifier.fillMaxSize(),
                ) {}
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = palette.onAccent,
                )
            }
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = palette.onContainer,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
