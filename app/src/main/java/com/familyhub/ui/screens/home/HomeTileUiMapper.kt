package com.familyhub.ui.screens.home

data class HomeTileCounts(
    val shoppingItemsRemaining: Int? = null,
    val todayMealsCount: Int? = null,
)

object HomeTileUiMapper {
    fun shoppingCountLoaded(counts: HomeTileCounts): Boolean = counts.shoppingItemsRemaining != null

    fun foodCountLoaded(counts: HomeTileCounts): Boolean = counts.todayMealsCount != null
}
