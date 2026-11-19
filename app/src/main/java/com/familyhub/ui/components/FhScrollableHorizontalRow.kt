package com.familyhub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrollable [LazyRow] with a trailing fade hint and optional auto-scroll to
 * [selectedIndex] when selection changes or when the row first appears.
 */
@Composable
fun FhScrollableHorizontalRow(
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    fadeColor: Color = MaterialTheme.colorScheme.background,
    fadeWidth: Dp = 28.dp,
    content: LazyListScope.() -> Unit,
) {
    LaunchedEffect(selectedIndex, listState.layoutInfo.totalItemsCount) {
        if (selectedIndex != null && selectedIndex >= 0 &&
            listState.layoutInfo.totalItemsCount > selectedIndex
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fhHorizontalScrollFadeOverlay(
                visible = listState.canScrollForward,
                fadeColor = fadeColor,
                fadeWidth = fadeWidth,
            ),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
