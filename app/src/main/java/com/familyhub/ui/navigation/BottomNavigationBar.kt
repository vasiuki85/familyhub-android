package com.familyhub.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(navController: NavController, selectedRoutes: List<String> = emptyList()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val activeItems = getActiveBottomNavItems(selectedRoutes)
    val barRoutes = activeItems.map { it.route }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    val moreSelected = currentRoute != null &&
        barRoutes.none { route -> isBottomNavRouteActive(currentRoute, route) }

    if (showMoreSheet) {
        MoreDestinationsSheet(
            navController = navController,
            barRoutes = barRoutes,
            currentRoute = currentRoute,
            onDismiss = { showMoreSheet = false },
        )
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        activeItems.forEach { item ->
            NavigationBarItem(
                selected = isBottomNavRouteActive(currentRoute, item.route),
                onClick = { navController.navigateToBottomNavDestination(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                colors = bottomNavItemColors(item.route),
            )
        }

        NavigationBarItem(
            selected = moreSelected,
            onClick = { showMoreSheet = true },
            icon = {
                Icon(
                    imageVector = BottomNavMore.icon,
                    contentDescription = stringResource(BottomNavMore.labelRes),
                )
            },
            label = {
                Text(
                    text = stringResource(BottomNavMore.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun bottomNavItemColors(route: String): NavigationBarItemColors {
    val module = navigationPaletteForRoute(route)
    return NavigationBarItemDefaults.colors(
        selectedIconColor = module.accent,
        selectedTextColor = module.accent,
        indicatorColor = module.container,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
