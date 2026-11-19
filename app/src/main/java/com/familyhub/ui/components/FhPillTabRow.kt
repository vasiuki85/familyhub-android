package com.familyhub.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyhub.ui.theme.FhModulePalette
import com.familyhub.ui.theme.FhTheme

/**
 * Segmented tab row where each tab paints its own pill background behind the label.
 *
 * Unlike a full-height indicator overlay, the selected background is part of the tab surface,
 * so the label stays readable. Scrolls horizontally with a trailing fade and keeps the selected
 * tab in view.
 */
@Composable
fun FhPillTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabLabels: List<String>,
    palette: FhModulePalette,
    modifier: Modifier = Modifier,
    edgePadding: androidx.compose.ui.unit.Dp = 16.dp,
    fadeColor: Color = MaterialTheme.colorScheme.background,
) {
    FhScrollableHorizontalRow(
        selectedIndex = selectedTabIndex,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        fadeColor = fadeColor,
    ) {
        itemsIndexed(tabLabels) { index, label ->
            val selected = index == selectedTabIndex
            Surface(
                onClick = { onTabSelected(index) },
                color = if (selected) palette.container else Color.Transparent,
                shape = RoundedCornerShape(FhTheme.radius.chip),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        palette.onContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
