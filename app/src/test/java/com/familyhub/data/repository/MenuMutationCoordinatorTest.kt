package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.DefaultMenuMutationClock
import com.familyhub.domain.utils.MenuMutationStaleException
import com.familyhub.domain.utils.MenuSyncIds
import com.familyhub.domain.utils.MenuTimestampOverflowException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MenuMutationCoordinatorTest {

  private val database: FamilyHubDatabase = mockk(relaxed = true)
  private val weeklyMenuDao: WeeklyMenuDao = mockk(relaxed = true)
  private val menuTemplateDao: MenuTemplateDao = mockk(relaxed = true)
  private val dailyAssignmentDao: DailyAssignmentDao = mockk(relaxed = true)
  private val familyMemberDao: FamilyMemberDao = mockk(relaxed = true)
  private val cloudDeletionLedger: CloudDeletionLedgerPort = mockk(relaxed = true)
  private val clock = DefaultMenuMutationClock(wallClock = { 5_000L })

  private lateinit var coordinator: MenuMutationCoordinator

  private val actorUserId = "actor-admin"

  private val weekStart = 1_700_000_000_000L
  private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)
  private val templateId = "tpl-1"

  @Before
  fun setUp() {
    mockkStatic("androidx.room.RoomDatabaseKt")
    coEvery { database.withTransaction<Unit>(any()) } coAnswers {
      secondArg<suspend () -> Unit>().invoke()
    }
    coEvery { familyMemberDao.getById(actorUserId) } returns FamilyMemberEntity(
      id = actorUserId,
      displayName = "Admin",
      roleLabel = "Admin",
      avatarColor = "#000000",
      role = MemberRole.ADMIN,
    )
    coordinator = MenuMutationCoordinator(
      database,
      weeklyMenuDao,
      menuTemplateDao,
      dailyAssignmentDao,
      familyMemberDao,
      cloudDeletionLedger,
      clock,
    )
    coEvery { weeklyMenuDao.upsertMenu(any()) } returns Unit
    coEvery { weeklyMenuDao.upsertEntries(any()) } returns Unit
    coEvery { weeklyMenuDao.deleteEntriesByIds(any()) } returns Unit
    coEvery { weeklyMenuDao.deleteMenu(any()) } returns Unit
    coEvery { menuTemplateDao.insertTemplate(any()) } returns Unit
    coEvery { menuTemplateDao.insertEntries(any()) } returns Unit
    coEvery { menuTemplateDao.deleteEntriesByIds(any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkStatic("androidx.room.RoomDatabaseKt")
  }

  @Test
  fun replaceWeeklyMenu_stampGreaterThanChangedAndRemovedChildren() = runTest {
    val unchanged = entryEntity(customDish = "Keep", updatedAt = 100L)
    val removed = entryEntity(day = 1, meal = "LUNCH", customDish = "Gone", updatedAt = 400L)
    val current = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 200L),
      entries = listOf(unchanged, removed),
    )
    val after = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 5_000L),
      entries = listOf(
        unchanged,
        entryEntity(day = 2, meal = "DINNER", customDish = "New", updatedAt = 5_000L),
      ),
    )
    stubWeeklyReads(current, verify = current, result = after)

    val capturedEntries = mutableListOf<List<MenuEntryEntity>>()
    coEvery { weeklyMenuDao.upsertEntries(capture(capturedEntries)) } returns Unit

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(
          menuEntry(
            customDish = "Keep",
            cookingTaskId = unchanged.cookingTaskId,
            prepTaskId = unchanged.prepTaskId,
          ),
          menuEntry(day = 2, meal = MealType.DINNER, customDish = "New"),
        ),
      ),
    )

    val parentSlot = slot<WeeklyMenuEntity>()
    coVerify { weeklyMenuDao.upsertMenu(capture(parentSlot)) }
    assertEquals(5_000L, parentSlot.captured.updatedAt)

    val writtenChanged = capturedEntries.flatten().find { it.customDish == "New" }!!
    assertEquals(5_000L, writtenChanged.updatedAt)
    assertTrue(capturedEntries.flatten().any { it.customDish == "Keep" && it.updatedAt == 100L })
    coVerify { weeklyMenuDao.deleteEntriesByIds(listOf(removed.id)) }
  }

  @Test
  fun replaceWeeklyMenu_unchangedChildKeepsTimestamp() = runTest {
    val unchanged = entryEntity(customDish = "Keep", updatedAt = 321L)
    val current = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 50L),
      entries = listOf(unchanged),
    )
    val after = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 5_000L),
      entries = listOf(
        unchanged,
        entryEntity(day = 1, meal = "LUNCH", customDish = "Added", updatedAt = 5_000L),
      ),
    )
    stubWeeklyReads(current, verify = current, result = after)

    val capturedEntries = mutableListOf<List<MenuEntryEntity>>()
    coEvery { weeklyMenuDao.upsertEntries(capture(capturedEntries)) } returns Unit

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(
          menuEntry(
            customDish = "Keep",
            cookingTaskId = unchanged.cookingTaskId,
            prepTaskId = unchanged.prepTaskId,
          ),
          menuEntry(day = 1, meal = MealType.LUNCH, customDish = "Added"),
        ),
      ),
    )

    assertTrue(capturedEntries.flatten().any { it.customDish == "Keep" && it.updatedAt == 321L })
  }

  @Test
  fun replaceWeeklyMenu_exactNoOpSkipsTransactionAndDaoWrites() = runTest {
    val entry = entryEntity(customDish = "Keep", updatedAt = 100L)
    val current = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 50L),
      entries = listOf(entry),
    )
    coEvery { weeklyMenuDao.getByStartDate(weekStart) } returns current

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(
          menuEntry(
            id = entry.id,
            customDish = entry.customDish ?: "Toast",
            updatedAt = entry.updatedAt,
            cookingTaskId = entry.cookingTaskId,
            prepTaskId = entry.prepTaskId,
          ),
        ),
      ),
    )

    coVerify(exactly = 0) { database.withTransaction<Unit>(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.upsertMenu(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.upsertEntries(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.deleteEntriesByIds(any()) }
  }

  @Test
  fun replaceWeeklyMenu_longMaxValueThrowsBeforeDaoMutation() = runTest {
    val current = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = Long.MAX_VALUE),
      entries = emptyList(),
    )
    coEvery { weeklyMenuDao.getByStartDate(weekStart) } returns current

    val error = runCatching {
      coordinator.replaceWeeklyMenu(actorUserId,
        weeklyMenu(entries = listOf(menuEntry(customDish = "New"))),
      )
    }.exceptionOrNull()

    assertTrue(error is MenuTimestampOverflowException)
    coVerify(exactly = 0) { weeklyMenuDao.upsertMenu(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.upsertEntries(any()) }
    coVerify(exactly = 0) { database.withTransaction<Unit>(any()) }
  }

  @Test
  fun replaceWeeklyMenu_writesCanonicalIds() = runTest {
    val legacyParentId = "legacy-parent"
    val current = WeeklyMenuWithEntries(
      menu = parentEntity(id = legacyParentId, updatedAt = 1L),
      entries = emptyList(),
    )
    val after = WeeklyMenuWithEntries(
      menu = parentEntity(updatedAt = 5_000L),
      entries = listOf(entryEntity(updatedAt = 5_000L)),
    )
    stubWeeklyReads(current, verify = current, result = after)

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        id = legacyParentId,
        entries = listOf(menuEntry()),
      ),
    )

    val parentSlot = slot<WeeklyMenuEntity>()
    coVerify { weeklyMenuDao.upsertMenu(capture(parentSlot)) }
    assertEquals(canonicalParentId, parentSlot.captured.id)
    coVerify { weeklyMenuDao.deleteMenu(legacyParentId) }
  }

  @Test
  fun replaceWeeklyMenu_staleChildInsertedThrowsWithZeroDaoMutations() = runTest {
    val current = weeklySnapshot(
      entries = listOf(entryEntity(customDish = "Only")),
    )
    val fresh = weeklySnapshot(
      entries = listOf(
        entryEntity(customDish = "Only"),
        entryEntity(day = 1, meal = "LUNCH", customDish = "Inserted", updatedAt = 10L),
      ),
    )
    expectWeeklyStale(current, fresh)
  }

  @Test
  fun replaceWeeklyMenu_staleChildDeletedThrowsWithZeroDaoMutations() = runTest {
    val deleted = entryEntity(day = 1, meal = "LUNCH", customDish = "Gone", updatedAt = 10L)
    val current = weeklySnapshot(entries = listOf(entryEntity(customDish = "Keep"), deleted))
    val fresh = weeklySnapshot(entries = listOf(entryEntity(customDish = "Keep")))
    expectWeeklyStale(current, fresh)
  }

  @Test
  fun replaceWeeklyMenu_staleChildPayloadDriftSameUpdatedAtThrowsWithZeroDaoMutations() = runTest {
    val current = weeklySnapshot(
      entries = listOf(entryEntity(customDish = "Before", updatedAt = 77L)),
    )
    val fresh = weeklySnapshot(
      entries = listOf(entryEntity(customDish = "After", updatedAt = 77L)),
    )
    expectWeeklyStale(current, fresh)
  }

  @Test
  fun replaceWeeklyMenu_staleChildUpdatedAtChangedThrowsWithZeroDaoMutations() = runTest {
    val current = weeklySnapshot(
      entries = listOf(entryEntity(customDish = "Same", updatedAt = 50L)),
    )
    val fresh = weeklySnapshot(
      entries = listOf(entryEntity(customDish = "Same", updatedAt = 99L)),
    )
    expectWeeklyStale(current, fresh)
  }

  @Test
  fun replaceWeeklyMenu_staleParentPayloadDriftSameUpdatedAtThrowsWithZeroDaoMutations() = runTest {
    val current = weeklySnapshot(
      parent = parentEntity(updatedAt = 200L, isAutoGenerated = false),
      entries = emptyList(),
    )
    val fresh = weeklySnapshot(
      parent = parentEntity(updatedAt = 200L, isAutoGenerated = true),
      entries = emptyList(),
    )
    expectWeeklyStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_staleChildInsertedThrowsWithZeroDaoMutations() = runTest {
    val current = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "Only")),
    )
    val fresh = templateSnapshot(
      entries = listOf(
        templateEntryEntity(customDish = "Only"),
        templateEntryEntity(day = 1, meal = "LUNCH", customDish = "Inserted", updatedAt = 10L),
      ),
    )
    expectTemplateStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_staleChildDeletedThrowsWithZeroDaoMutations() = runTest {
    val deleted = templateEntryEntity(day = 1, meal = "LUNCH", customDish = "Gone", updatedAt = 10L)
    val current = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "Keep"), deleted),
    )
    val fresh = templateSnapshot(entries = listOf(templateEntryEntity(customDish = "Keep")))
    expectTemplateStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_staleChildPayloadDriftSameUpdatedAtThrowsWithZeroDaoMutations() = runTest {
    val current = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "Before", updatedAt = 77L)),
    )
    val fresh = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "After", updatedAt = 77L)),
    )
    expectTemplateStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_staleChildUpdatedAtChangedThrowsWithZeroDaoMutations() = runTest {
    val current = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "Same", updatedAt = 50L)),
    )
    val fresh = templateSnapshot(
      entries = listOf(templateEntryEntity(customDish = "Same", updatedAt = 99L)),
    )
    expectTemplateStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_staleParentPayloadDriftSameUpdatedAtThrowsWithZeroDaoMutations() = runTest {
    val current = templateSnapshot(
      parent = templateParent(updatedAt = 200L, name = "Before"),
      entries = emptyList(),
    )
    val fresh = templateSnapshot(
      parent = templateParent(updatedAt = 200L, name = "After"),
      entries = emptyList(),
    )
    expectTemplateStale(current, fresh)
  }

  @Test
  fun replaceMenuTemplate_clearsExistingTaskRefs() = runTest {
    val existing = templateEntryEntity(
      cookingTaskId = "old-cook",
      prepTaskId = "old-prep",
      updatedAt = 50L,
    )
    val current = templateSnapshot(
      parent = templateParent(updatedAt = 10L),
      entries = listOf(existing),
    )
    val after = templateSnapshot(
      parent = templateParent(updatedAt = 5_000L),
      entries = listOf(
        existing.copy(cookingTaskId = null, prepTaskId = null, updatedAt = 5_000L),
      ),
    )
    coEvery { menuTemplateDao.getById(templateId) } returnsMany listOf(current, current, after)

    val capturedEntries = mutableListOf<List<MenuTemplateEntryEntity>>()
    coEvery { menuTemplateDao.insertEntries(capture(capturedEntries)) } returns Unit

    coordinator.replaceMenuTemplate(actorUserId,
      menuTemplate(
        entries = listOf(
          menuEntry(
            cookingTaskId = "new-cook",
            prepTaskId = "new-prep",
          ),
        ),
      ),
    )

    val written = capturedEntries.flatten().single()
    assertNull(written.cookingTaskId)
    assertNull(written.prepTaskId)
    assertEquals(5_000L, written.updatedAt)
  }

  private suspend fun expectWeeklyStale(
    current: WeeklyMenuWithEntries,
    fresh: WeeklyMenuWithEntries,
  ) {
    coEvery { weeklyMenuDao.getByStartDate(weekStart) } returnsMany listOf(current, fresh)

    val error = runCatching {
      coordinator.replaceWeeklyMenu(actorUserId,
        weeklyMenu(entries = listOf(menuEntry(customDish = "Replacement"))),
      )
    }.exceptionOrNull()

    assertTrue(error is MenuMutationStaleException)
    coVerify(exactly = 0) { weeklyMenuDao.upsertMenu(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.upsertEntries(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.deleteEntriesByIds(any()) }
    coVerify(exactly = 0) { weeklyMenuDao.deleteMenu(any()) }
  }

  private suspend fun expectTemplateStale(
    current: MenuTemplateWithEntries,
    fresh: MenuTemplateWithEntries,
  ) {
    coEvery { menuTemplateDao.getById(templateId) } returnsMany listOf(current, fresh)

    val error = runCatching {
      coordinator.replaceMenuTemplate(actorUserId,
        menuTemplate(entries = listOf(menuEntry(customDish = "Replacement"))),
      )
    }.exceptionOrNull()

    assertTrue(error is MenuMutationStaleException)
    coVerify(exactly = 0) { menuTemplateDao.insertTemplate(any()) }
    coVerify(exactly = 0) { menuTemplateDao.insertEntries(any()) }
    coVerify(exactly = 0) { menuTemplateDao.deleteEntriesByIds(any()) }
  }

  private fun stubWeeklyReads(
    initial: WeeklyMenuWithEntries,
    verify: WeeklyMenuWithEntries,
    result: WeeklyMenuWithEntries,
  ) {
    coEvery { weeklyMenuDao.getByStartDate(weekStart) } returnsMany listOf(initial, verify, result)
  }

  private fun weeklySnapshot(
    parent: WeeklyMenuEntity = parentEntity(updatedAt = 10L),
    entries: List<MenuEntryEntity>,
  ) = WeeklyMenuWithEntries(menu = parent, entries = entries)

  private fun templateSnapshot(
    parent: MenuTemplateEntity = templateParent(updatedAt = 10L),
    entries: List<MenuTemplateEntryEntity>,
  ) = MenuTemplateWithEntries(template = parent, entries = entries)

  private fun parentEntity(
    id: String = canonicalParentId,
    updatedAt: Long = 0L,
    isAutoGenerated: Boolean = false,
  ) = WeeklyMenuEntity(
    id = id,
    startDateMillis = weekStart,
    isAutoGenerated = isAutoGenerated,
    createdAt = 1L,
    updatedAt = updatedAt,
  )

  private fun templateParent(
    updatedAt: Long = 0L,
    name: String = "Week",
  ) = MenuTemplateEntity(
    id = templateId,
    name = name,
    createdAt = 1L,
    updatedAt = updatedAt,
  )

  private fun entryEntity(
    day: Int = 0,
    meal: String = "BREAKFAST",
    customDish: String = "Toast",
    updatedAt: Long = 0L,
  ) = MenuEntryEntity(
    id = MenuSyncIds.menuEntryId(weekStart, day, meal),
    menuId = canonicalParentId,
    dayOfWeek = day,
    mealType = meal,
    recipeId = null,
    customDish = customDish,
    servings = 1,
    assignedMemberId = null,
    cookingTaskId = "cook-$day",
    prepTaskId = "prep-$day",
    updatedAt = updatedAt,
  )

  private fun templateEntryEntity(
    day: Int = 0,
    meal: String = "BREAKFAST",
    customDish: String = "Toast",
    updatedAt: Long = 0L,
    cookingTaskId: String? = null,
    prepTaskId: String? = null,
  ) = MenuTemplateEntryEntity(
    id = MenuSyncIds.menuTemplateEntryId(templateId, day, meal),
    templateId = templateId,
    dayOfWeek = day,
    mealType = meal,
    recipeId = null,
    customDish = customDish,
    servings = 1,
    assignedMemberId = null,
    cookingTaskId = cookingTaskId,
    prepTaskId = prepTaskId,
    updatedAt = updatedAt,
  )

  private fun weeklyMenu(
    id: String = canonicalParentId,
    entries: List<MenuEntry> = listOf(menuEntry()),
  ) = WeeklyMenu(
    id = id,
    startDateMillis = weekStart,
    isAutoGenerated = false,
    createdAt = 1L,
    updatedAt = 0L,
    entries = entries,
  )

  private fun menuTemplate(
    entries: List<MenuEntry> = listOf(menuEntry()),
  ) = MenuTemplate(
    id = templateId,
    name = "Week",
    createdAt = 1L,
    updatedAt = 0L,
    entries = entries,
  )

  private fun menuEntry(
    id: String = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
    day: Int = 0,
    meal: MealType = MealType.BREAKFAST,
    customDish: String = "Toast",
    updatedAt: Long = 0L,
    cookingTaskId: String? = null,
    prepTaskId: String? = null,
  ) = MenuEntry(
    id = id,
    menuId = canonicalParentId,
    dayOfWeek = day,
    mealType = meal,
    recipeId = null,
    customDish = customDish,
    servings = 1,
    assignedMemberId = null,
    cookingTaskId = cookingTaskId,
    prepTaskId = prepTaskId,
    updatedAt = updatedAt,
  )
}
