package com.familyhub.ui.screens.shopping

object ShoppingSearchFilterReducer {
    fun apply(state: ShoppingUiState): ShoppingUiState =
        state.copy(
            searchQuery = "",
            activeFilter = ShoppingFilter.All,
        )
}
