package com.familyhub.ui.screens.shopping

import androidx.annotation.StringRes
import com.familyhub.R

enum class ShoppingContentEmptyKind {
    NO_LISTS,
    EMPTY_LIST,
    NO_RESULTS,
}

enum class ShoppingContentAction {
    CREATE_LIST,
    ADD_ITEM,
    RESET_FILTERS,
}

enum class ShoppingFabAction {
    NONE,
    CREATE_LIST,
    ADD_ITEM,
}

data class ShoppingContentUiModel(
    val emptyKind: ShoppingContentEmptyKind? = null,
    @StringRes val titleResId: Int? = null,
    @StringRes val messageResId: Int? = null,
    @StringRes val actionLabelResId: Int? = null,
    val primaryAction: ShoppingContentAction? = null,
    @StringRes val actionContentDescriptionResId: Int? = null,
    val showSearchAndFilters: Boolean = true,
    val showQuickSuggestions: Boolean = true,
    val fabAction: ShoppingFabAction = ShoppingFabAction.ADD_ITEM,
) {
    val showsEmptyState: Boolean
        get() = emptyKind != null

    companion object {
        fun content(): ShoppingContentUiModel = ShoppingContentUiModel()

        fun loading(): ShoppingContentUiModel = ShoppingContentUiModel(
            showSearchAndFilters = false,
            showQuickSuggestions = false,
            fabAction = ShoppingFabAction.NONE,
        )
    }
}

object ShoppingContentUiStateMapper {
    fun mapFromState(state: ShoppingUiState): ShoppingContentUiModel = map(
        isLoading = state.isLoading,
        hasActiveLists = state.lists.isNotEmpty(),
        items = state.items,
        filteredItems = state.filteredItems,
        searchQuery = state.searchQuery,
        activeFilter = state.activeFilter,
    )

    fun map(
        isLoading: Boolean,
        hasActiveLists: Boolean,
        items: List<com.familyhub.domain.model.ShoppingItem>,
        filteredItems: List<com.familyhub.domain.model.ShoppingItem>,
        searchQuery: String,
        activeFilter: ShoppingFilter,
    ): ShoppingContentUiModel {
        if (isLoading) {
            return ShoppingContentUiModel.loading()
        }
        if (!hasActiveLists) {
            return ShoppingContentUiModel(
                emptyKind = ShoppingContentEmptyKind.NO_LISTS,
                titleResId = R.string.shopping_empty_no_lists_title,
                messageResId = R.string.shopping_empty_no_lists_message,
                actionLabelResId = R.string.shopping_empty_no_lists_action,
                primaryAction = ShoppingContentAction.CREATE_LIST,
                actionContentDescriptionResId = R.string.shopping_empty_no_lists_action_cd,
                showSearchAndFilters = false,
                showQuickSuggestions = false,
                fabAction = ShoppingFabAction.CREATE_LIST,
            )
        }
        if (items.isEmpty()) {
            return ShoppingContentUiModel(
                emptyKind = ShoppingContentEmptyKind.EMPTY_LIST,
                titleResId = R.string.shopping_empty_list_title,
                messageResId = R.string.shopping_empty_list_message,
                actionLabelResId = R.string.shopping_add_item,
                primaryAction = ShoppingContentAction.ADD_ITEM,
                actionContentDescriptionResId = R.string.shopping_empty_list_action_cd,
                fabAction = ShoppingFabAction.NONE,
            )
        }
        if (filteredItems.isEmpty() && isSearchOrFilterActive(searchQuery, activeFilter)) {
            return ShoppingContentUiModel(
                emptyKind = ShoppingContentEmptyKind.NO_RESULTS,
                titleResId = R.string.shopping_empty_no_results_title,
                messageResId = R.string.shopping_empty_no_results_message,
                actionLabelResId = R.string.shopping_empty_no_results_action,
                primaryAction = ShoppingContentAction.RESET_FILTERS,
                actionContentDescriptionResId = R.string.shopping_empty_no_results_action_cd,
                fabAction = ShoppingFabAction.ADD_ITEM,
            )
        }
        return ShoppingContentUiModel.content()
    }

    internal fun isSearchOrFilterActive(
        searchQuery: String,
        activeFilter: ShoppingFilter,
    ): Boolean = searchQuery.isNotBlank() || activeFilter !is ShoppingFilter.All
}
