package com.familyhub.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.familyhub.R

/**
 * Common screen chrome: top app bar, optional refresh bar and error banner, snackbar, and FAB.
 *
 * The refresh bar and error banner are pinned between the app bar and the content area.
 * The [content] lambda receives [PaddingValues] with top = 0.dp (the column already
 * consumed the top inset); callers must apply start/end/bottom padding themselves.
 * System bars are consumed by the app-level scaffold, so this nested scaffold deliberately
 * uses zero window insets to avoid duplicate empty bands above and below every screen.
 *
 * When [titleContent] is non-null it fully replaces the built-in title/subtitle [Column];
 * [title] and [subtitle] are then ignored. Callers supplying [titleContent] are responsible
 * for their own text styling and accessibility.
 *
 * When [onRefresh] is non-null the content is wrapped in [PullToRefreshBox] and driven by
 * [isRefreshing]. The linear progress indicator is suppressed in that mode so only one
 * refresh affordance is visible (the pull indicator owns the loading state; the top-bar
 * refresh action remains available for accessibility).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FhScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    backgroundDecorationAlpha: Float = 0.04f,
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    collapsingTopBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = if (collapsingTopBar) {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    val layoutDirection = LocalLayoutDirection.current
    val showLinearRefreshIndicator = isRefreshing && onRefresh == null
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (isLightTheme) {
            Image(
                painter = painterResource(R.drawable.fh_background_warm_v2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = backgroundDecorationAlpha.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = navigationIcon,
                    title = {
                        if (titleContent != null) {
                            titleContent()
                        } else {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            snackbarHost = {
                if (snackbarHostState != null) {
                    SnackbarHost(hostState = snackbarHostState)
                }
            },
            floatingActionButton = floatingActionButton
        ) { paddingValues ->
            val contentPadding = PaddingValues(
                start = paddingValues.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                if (showLinearRefreshIndicator) {
                    val loadingDescription = stringResource(R.string.common_loading)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = loadingDescription }
                    )
                }
                if (errorMessage != null) {
                    ErrorMessage(
                        message = errorMessage,
                        onDismiss = onErrorDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (onRefresh != null) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        content(contentPadding)
                    }
                } else {
                    content(contentPadding)
                }
            }
        }
    }
}
