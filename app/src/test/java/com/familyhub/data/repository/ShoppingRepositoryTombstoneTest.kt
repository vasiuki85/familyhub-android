package com.familyhub.data.repository

import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.dao.ShoppingItemDao
import com.familyhub.data.local.entity.ShoppingCategoryEntity
import com.familyhub.data.local.entity.ShoppingItemEntity
import com.familyhub.data.local.entity.ShoppingListEntity
import com.familyhub.data.sync.CloudDeletionLedger
import com.familyhub.domain.model.ShoppingList
import com.familyhub.domain.utils.IdGenerator
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ShoppingRepositoryTombstoneTest {

    private val dao = mockk<ShoppingItemDao>(relaxed = true)
    private val ledger = mockk<CloudDeletionLedger>(relaxed = true)
    private val idGenerator = mockk<IdGenerator>(relaxed = true)
    private lateinit var repository: ShoppingRepository

    @Before
    fun setUp() {
        repository = ShoppingRepository(dao, idGenerator, ledger)
        coEvery { ledger.recordDeletions(any<Collection<Pair<String, String>>>()) } just Runs
        coEvery { dao.clear(any()) } just Runs
        coEvery { dao.deleteCategoriesByList(any()) } just Runs
        coEvery { dao.deleteList(any()) } just Runs
    }

    @Test
    fun deleteList_recordsCascadeTombstonesInSingleBatchBeforeDaoDeletes() = runTest {
        val list = ShoppingList(
            id = "list-1",
            title = "Groceries",
            colorHex = "#fff",
            iconName = "cart",
            createdAt = 1L
        )
        coEvery { dao.getAllOnce("list-1") } returns listOf(
            ShoppingItemEntity(
                id = "item-1",
                listId = "list-1",
                title = "Milk",
                createdByUserId = "user-1",
                isBought = false,
                createdAt = 1L,
                categoryId = "cat-1",
                quantity = null,
                quantityUnit = null,
                note = null,
                boughtAt = null
            )
        )
        coEvery { dao.getCategoriesOnce("list-1") } returns listOf(
            ShoppingCategoryEntity(
                id = "cat-1",
                listId = "list-1",
                title = "Dairy",
                colorHex = "#fff",
                iconName = "dairy",
                sortOrder = 0
            )
        )

        repository.deleteList(list)

        coVerifyOrder {
            ledger.recordDeletions(
                match<List<Pair<String, String>>> { entries ->
                    entries.toSet() == setOf(
                        DeletedCloudDocumentCollections.SHOPPING_LISTS to "list-1",
                        DeletedCloudDocumentCollections.SHOPPING_CATEGORIES to "cat-1",
                        DeletedCloudDocumentCollections.SHOPPING_ITEMS to "item-1"
                    )
                }
            )
            dao.clear("list-1")
            dao.deleteCategoriesByList("list-1")
            dao.deleteList(any<ShoppingListEntity>())
        }
        coVerify(exactly = 1) { ledger.recordDeletions(any<Collection<Pair<String, String>>>()) }
    }
}
