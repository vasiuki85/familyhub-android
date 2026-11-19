package com.familyhub.ui.screens.shopping

import com.familyhub.R
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingContentUiStateMapperTest {

    @Test
    fun defaultStateWhileLoading_hasNoEmptyStateCtaOrFab() {
        val model = ShoppingContentUiStateMapper.mapFromState(ShoppingUiState())

        assertFalse(model.showsEmptyState)
        assertNull(model.primaryAction)
        assertEquals(ShoppingFabAction.NONE, model.fabAction)
        assertFalse(model.showSearchAndFilters)
        assertFalse(model.showQuickSuggestions)
    }

    @Test
    fun loadingWithStaleEmptyLists_doesNotReturnCreateList() {
        val model = mapState(isLoading = true, lists = emptyList())

        assertNull(model.emptyKind)
        assertNotEquals(ShoppingContentAction.CREATE_LIST, model.primaryAction)
        assertEquals(ShoppingFabAction.NONE, model.fabAction)
    }

    @Test
    fun loadingWithPopulatedListsAndItems_doesNotReturnAddItem() {
        val model = mapState(isLoading = true)

        assertNull(model.emptyKind)
        assertNotEquals(ShoppingFabAction.ADD_ITEM, model.fabAction)
        assertEquals(ShoppingFabAction.NONE, model.fabAction)
    }

    @Test
    fun afterLoadingEmptyLists_returnsNoLists() {
        val model = mapState(isLoading = false, lists = emptyList())

        assertEquals(ShoppingContentEmptyKind.NO_LISTS, model.emptyKind)
        assertEquals(ShoppingContentAction.CREATE_LIST, model.primaryAction)
    }

    @Test
    fun noLists_returnsNoListsWithCreateListAction() {
        val model = mapState(lists = emptyList())

        assertEquals(ShoppingContentEmptyKind.NO_LISTS, model.emptyKind)
        assertEquals(ShoppingContentAction.CREATE_LIST, model.primaryAction)
        assertEquals(ShoppingFabAction.CREATE_LIST, model.fabAction)
        assertEquals(R.string.shopping_empty_no_lists_title, model.titleResId)
    }

    @Test
    fun noLists_hasPriorityOverStaleSearchAndFilter() {
        val model = mapState(
            lists = emptyList(),
            searchQuery = "milk",
            activeFilter = ShoppingFilter.NotBought,
        )

        assertEquals(ShoppingContentEmptyKind.NO_LISTS, model.emptyKind)
        assertFalse(model.showSearchAndFilters)
        assertFalse(model.showQuickSuggestions)
    }

    @Test
    fun emptyList_returnsEmptyListWithAddItemAction() {
        val model = mapState(items = emptyList())

        assertEquals(ShoppingContentEmptyKind.EMPTY_LIST, model.emptyKind)
        assertEquals(ShoppingContentAction.ADD_ITEM, model.primaryAction)
        assertEquals(ShoppingFabAction.NONE, model.fabAction)
        assertEquals(R.string.shopping_empty_list_title, model.titleResId)
    }

    @Test
    fun searchWithNoResults_returnsNoResultsWithResetFilters() {
        val model = mapState(
            items = listOf(sampleItem(title = "Milk")),
            searchQuery = "bread",
        )

        assertEquals(ShoppingContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(ShoppingContentAction.RESET_FILTERS, model.primaryAction)
        assertEquals(R.string.shopping_empty_no_results_title, model.titleResId)
    }

    @Test
    fun notBoughtFilterWithNoResults_returnsNoResults() {
        val model = mapState(
            items = listOf(sampleItem(title = "Milk", isBought = true)),
            activeFilter = ShoppingFilter.NotBought,
        )

        assertEquals(ShoppingContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(ShoppingContentAction.RESET_FILTERS, model.primaryAction)
    }

    @Test
    fun categoryFilterWithNoResults_returnsNoResults() {
        val model = mapState(
            items = listOf(sampleItem(title = "Milk", categoryId = "cat-a")),
            categories = listOf(sampleCategory("cat-a"), sampleCategory("cat-b")),
            activeFilter = ShoppingFilter.Category("cat-b"),
        )

        assertEquals(ShoppingContentEmptyKind.NO_RESULTS, model.emptyKind)
        assertEquals(ShoppingContentAction.RESET_FILTERS, model.primaryAction)
    }

    @Test
    fun filteredItemsNonEmpty_hasNoEmptyState() {
        val model = mapState(
            items = listOf(sampleItem(title = "Milk")),
            searchQuery = "milk",
        )

        assertFalse(model.showsEmptyState)
        assertNull(model.emptyKind)
        assertNull(model.primaryAction)
    }

    @Test
    fun noLists_neverUsesAddItemForPrimaryOrFab() {
        val model = mapState(lists = emptyList())

        assertNotEquals(ShoppingContentAction.ADD_ITEM, model.primaryAction)
        assertNotEquals(ShoppingFabAction.ADD_ITEM, model.fabAction)
    }

    private fun mapState(
        isLoading: Boolean = false,
        lists: List<ShoppingList> = listOf(sampleList()),
        items: List<ShoppingItem> = listOf(sampleItem()),
        categories: List<ShoppingCategory> = emptyList(),
        searchQuery: String = "",
        activeFilter: ShoppingFilter = ShoppingFilter.All,
    ): ShoppingContentUiModel {
        val state = ShoppingUiState(
            isLoading = isLoading,
            lists = lists,
            items = items,
            categories = categories,
            searchQuery = searchQuery,
            activeFilter = activeFilter,
        )
        return ShoppingContentUiStateMapper.mapFromState(state)
    }

    private fun sampleList() = ShoppingList(
        id = LIST_ID,
        title = "Groceries",
        colorHex = "#4CAF50",
        iconName = "cart",
        createdAt = 0L,
    )

    private fun sampleCategory(id: String = CATEGORY_ID) = ShoppingCategory(
        id = id,
        listId = LIST_ID,
        title = "Category $id",
        colorHex = "#4CAF50",
        iconName = "fruits",
        sortOrder = 0,
    )

    private fun sampleItem(
        title: String = "Milk",
        isBought: Boolean = false,
        categoryId: String? = null,
    ) = ShoppingItem(
        id = "item-$title",
        listId = LIST_ID,
        title = title,
        createdByUserId = "user-1",
        isBought = isBought,
        createdAt = 0L,
        categoryId = categoryId,
    )

    private companion object {
        const val LIST_ID = "list-1"
        const val CATEGORY_ID = "cat-a"
    }
}
