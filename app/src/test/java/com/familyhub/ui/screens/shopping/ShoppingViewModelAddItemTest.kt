package com.familyhub.ui.screens.shopping

import android.content.Context
import com.familyhub.R
import com.familyhub.data.datastore.AppSettingsDataStore
import com.familyhub.data.notification.ShoppingNotificationManager
import com.familyhub.data.repository.FamilyMemberRepository
import com.familyhub.data.repository.ShoppingRepository
import com.familyhub.domain.model.AppSettings
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.ShoppingActivity
import com.familyhub.domain.model.ShoppingCategory
import com.familyhub.domain.model.ShoppingItem
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.utils.IdGenerator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelAddItemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsFlow = MutableStateFlow(defaultSettings())
    private val listsFlow = MutableStateFlow(listOf(defaultList()))
    private val membersFlow = MutableStateFlow(listOf(defaultMember()))
    private val itemsFlow = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<ShoppingCategory>>(emptyList())
    private val activityFlow = MutableStateFlow<List<ShoppingActivity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addItem_emptyList_showsItemAndCounterImmediately() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.totalCount)
        assertEquals(0, viewModel.state.value.boughtCount)
        assertTrue(viewModel.state.value.items.isEmpty())

        viewModel.addItem(
            title = "Milk",
            categoryId = null,
            quantity = null,
            unit = null,
            note = null,
        )
        advanceUntilIdle()

        val addedItem = ShoppingItem(
            id = "item-1",
            listId = ShoppingRepository.DEFAULT_LIST_ID,
            title = "Milk",
            createdByUserId = "user-1",
            isBought = false,
            createdAt = 1L,
            categoryId = null,
        )
        itemsFlow.value = listOf(addedItem)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.totalCount)
        assertEquals(0, state.boughtCount)
        assertEquals(listOf(addedItem), state.items)
        assertEquals(1, state.groupedItems.single().items.size)
        assertEquals(null, state.groupedItems.single().category)
    }

    @Test
    fun addItem_baseDataReemit_doesNotWipeFreshItems() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val addedItem = ShoppingItem(
            id = "item-1",
            listId = ShoppingRepository.DEFAULT_LIST_ID,
            title = "Bread",
            createdByUserId = "user-1",
            isBought = false,
            createdAt = 1L,
            categoryId = null,
        )
        itemsFlow.value = listOf(addedItem)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.totalCount)

        membersFlow.value = listOf(
            defaultMember().copy(displayName = "Updated member"),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.totalCount)
        assertEquals(listOf(addedItem), viewModel.state.value.items)
        assertFalse(viewModel.state.value.groupedItems.isEmpty())
    }

    @Test
    fun groupedItems_uncategorisedItem_isRenderedInSection() {
        val state = ShoppingUiState(
            items = listOf(
                sampleItem(title = "Eggs", categoryId = null, isBought = false),
            ),
            categories = listOf(
                sampleCategory(id = "cat-produce", title = "Produce"),
            ),
            activeFilter = ShoppingFilter.All,
        )

        val sections = state.groupedItems
        assertEquals(1, sections.size)
        assertEquals(null, sections.single().category)
        assertEquals("Eggs", sections.single().items.single().title)
    }

    private fun createViewModel(): ShoppingViewModel {
        val shoppingRepository = mockk<ShoppingRepository>(relaxed = true) {
            every { observeLists() } returns listsFlow
            every { observeItems(ShoppingRepository.DEFAULT_LIST_ID) } returns itemsFlow
            every { observeCategories(ShoppingRepository.DEFAULT_LIST_ID) } returns categoriesFlow
            every { observeActivity(ShoppingRepository.DEFAULT_LIST_ID, any()) } returns activityFlow
            coEvery { ensureDefaultList() } returns defaultList()
            coEvery { upsertItem(any()) } returns Unit
            coEvery { loadTopItemTitles(any()) } returns emptyList()
        }
        val memberRepository = mockk<FamilyMemberRepository>(relaxed = true) {
            every { getAllMembers() } returns membersFlow
        }
        val appSettingsDataStore = mockk<AppSettingsDataStore>(relaxed = true)
        every { appSettingsDataStore.settingsFlow } returns settingsFlow
        coEvery { appSettingsDataStore.setActiveShoppingList(any()) } returns Unit
        val idGenerator = mockk<IdGenerator>(relaxed = true) {
            every { newId() } returns "item-1"
        }
        val context = mockk<Context>(relaxed = true) {
            every { getString(R.string.shopping_item_added_success) } returns "Added"
            every { getString(R.string.shopping_member_unknown) } returns "Unknown"
        }

        return ShoppingViewModel(
            shoppingRepository = shoppingRepository,
            memberRepository = memberRepository,
            appSettingsDataStore = appSettingsDataStore,
            idGenerator = idGenerator,
            shoppingNotificationManager = mockk<ShoppingNotificationManager>(relaxed = true),
            context = context,
        )
    }

    private fun defaultSettings() = AppSettings(
        currentUserId = "user-1",
        activeShoppingListId = ShoppingRepository.DEFAULT_LIST_ID,
    )

    private fun defaultList() = ShoppingList(
        id = ShoppingRepository.DEFAULT_LIST_ID,
        title = "Family",
        colorHex = "#315F93",
        iconName = "cart",
        createdAt = 1L,
    )

    private fun defaultMember() = FamilyMember(
        id = "user-1",
        displayName = "Parent",
        roleLabel = "Parent",
        avatarColor = "#315F93",
        role = MemberRole.PARENT,
    )

    private fun sampleCategory(id: String, title: String) = ShoppingCategory(
        id = id,
        listId = ShoppingRepository.DEFAULT_LIST_ID,
        title = title,
        colorHex = "#4CAF50",
        iconName = "fruits",
        sortOrder = 0,
    )

    private fun sampleItem(
        title: String,
        categoryId: String?,
        isBought: Boolean,
    ) = ShoppingItem(
        id = "item-$title",
        listId = ShoppingRepository.DEFAULT_LIST_ID,
        title = title,
        createdByUserId = "user-1",
        isBought = isBought,
        createdAt = 1L,
        categoryId = categoryId,
    )
}
