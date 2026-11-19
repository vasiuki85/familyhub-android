package com.familyhub.ui.screens.food

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyhub.R
import com.familyhub.ui.components.FhPillTabRow
import com.familyhub.ui.navigation.FoodSectionTab
import com.familyhub.ui.theme.FhTheme

@Composable
fun FoodSectionTabs(
    selectedTab: FoodSectionTab,
    onTabSelected: (FoodSectionTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = FoodSectionTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val tabLabels = tabs.map { tab -> stringResource(tab.labelRes) }

    FhPillTabRow(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        tabLabels = tabLabels,
        palette = FhTheme.modules.food,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    )
}

private val FoodSectionTab.labelRes: Int
    get() = when (this) {
        FoodSectionTab.WEEKLY_MENU -> R.string.food_hub_weekly_menu_title
        FoodSectionTab.RECIPES -> R.string.nav_label_recipes
    }
