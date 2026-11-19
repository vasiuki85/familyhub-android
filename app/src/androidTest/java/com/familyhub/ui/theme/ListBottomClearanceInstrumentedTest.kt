package com.familyhub.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.ui.screens.menu.WeeklyMenuLayoutPolicy
import com.familyhub.ui.screens.recipes.RecipeBookLayoutPolicy
import com.familyhub.ui.screens.stats.MemberStatisticsLayoutTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListBottomClearanceInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun weeklyMenu_lastItem_hasBreathingRoomWithinContentArea() {
        assertLastItemClearsContentBottom(
            bottomPadding = WeeklyMenuLayoutPolicy.listBottomPadding(),
            minimumGapBelowLastItem = FhSpacing.lg,
            listTag = "weekly-menu-list",
            itemPrefix = "weekly-menu-item",
            itemCount = 12,
        )
    }

    @Test
    fun recipeBook_lastItem_scrollsAboveFabWithinContentArea() {
        assertLastItemClearsFabOverlay(
            bottomPadding = RecipeBookLayoutPolicy.listBottomPadding(hasFloatingActionButton = true),
            listTag = "recipe-book-list",
            itemPrefix = "recipe-book-item",
            itemCount = 12,
        )
    }

    @Test
    fun memberStatistics_lastItem_hasBreathingRoomWithinContentArea() {
        assertLastItemClearsContentBottom(
            bottomPadding = MemberStatisticsLayoutTokens.listBottomPadding(),
            minimumGapBelowLastItem = FhSpacing.lg,
            listTag = "member-stats-list",
            itemPrefix = "member-stats-item",
            itemCount = 8,
        )
    }

    private fun assertLastItemClearsContentBottom(
        bottomPadding: androidx.compose.ui.unit.Dp,
        minimumGapBelowLastItem: androidx.compose.ui.unit.Dp,
        listTag: String,
        itemPrefix: String,
        itemCount: Int,
    ) {
        val lastItemTag = "$itemPrefix-${itemCount - 1}"

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(CONTENT_AREA_TAG),
                ) {
                    ScrollableListHarness(
                        listTag = listTag,
                        itemPrefix = itemPrefix,
                        itemCount = itemCount,
                        bottomPadding = bottomPadding,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(listTag).performScrollToIndex(itemCount - 1)
        composeRule.waitForIdle()

        val contentBottom = composeRule.onNodeWithTag(CONTENT_AREA_TAG).getBoundsInRoot().bottom
        val lastItemBottom = composeRule.onNodeWithTag(lastItemTag).getBoundsInRoot().bottom
        val gapBelowLastItem = contentBottom - lastItemBottom

        assertTrue(
            "Expected at least ${minimumGapBelowLastItem.value}dp below last item, got ${gapBelowLastItem.value}dp",
            gapBelowLastItem >= minimumGapBelowLastItem - 1.dp,
        )
        composeRule.onNodeWithTag(lastItemTag).assertIsDisplayed()
    }

    private fun assertLastItemClearsFabOverlay(
        bottomPadding: androidx.compose.ui.unit.Dp,
        listTag: String,
        itemPrefix: String,
        itemCount: Int,
    ) {
        val lastItemTag = "$itemPrefix-${itemCount - 1}"

        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(CONTENT_AREA_TAG),
                ) {
                    ScrollableListHarness(
                        listTag = listTag,
                        itemPrefix = itemPrefix,
                        itemCount = itemCount,
                        bottomPadding = bottomPadding,
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(FAB_MARGIN)
                            .size(FAB_SIZE)
                            .testTag(FAB_OVERLAY_TAG),
                        color = MaterialTheme.colorScheme.primary,
                    ) {}
                }
            }
        }

        composeRule.onNodeWithTag(listTag).performScrollToIndex(itemCount - 1)
        composeRule.waitForIdle()

        val fabTop = composeRule.onNodeWithTag(FAB_OVERLAY_TAG).getBoundsInRoot().top
        val lastItemBottom = composeRule.onNodeWithTag(lastItemTag).getBoundsInRoot().bottom

        assertTrue(
            "Expected $lastItemTag bottom=$lastItemBottom to stay above FAB top=$fabTop",
            lastItemBottom < fabTop,
        )
        composeRule.onNodeWithTag(lastItemTag).assertIsDisplayed()
    }

    @Composable
    private fun ScrollableListHarness(
        listTag: String,
        itemPrefix: String,
        itemCount: Int,
        bottomPadding: androidx.compose.ui.unit.Dp,
    ) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(listTag),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            items(itemCount) { index ->
                Text(
                    text = "Row $index",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("$itemPrefix-$index"),
                )
            }
        }
    }

    private companion object {
        const val CONTENT_AREA_TAG = "content-area"
        const val FAB_OVERLAY_TAG = "fab-overlay"
        val FAB_SIZE = 56.dp
        val FAB_MARGIN = 16.dp
    }
}
