package com.familyhub.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.domain.model.DailyAssignment
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.DefaultMenuMutationClock
import com.familyhub.domain.utils.MenuMutationForbiddenException
import com.familyhub.domain.utils.MenuMutationStaleException
import com.familyhub.domain.utils.MenuSyncIds
import com.familyhub.domain.utils.MenuTimestampOverflowException
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuMutationCoordinatorInstrumentedTest {

  private lateinit var database: FamilyHubDatabase
  private lateinit var weeklyMenuDao: WeeklyMenuDao
  private lateinit var menuTemplateDao: MenuTemplateDao
  private lateinit var dailyAssignmentDao: DailyAssignmentDao
  private lateinit var coordinator: MenuMutationCoordinator
  private val deletionLedger = RecordingDeletionLedger()

  private val actorUserId = "actor-admin"

  private val weekStart = 1_700_000_000_000L
  private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, FamilyHubDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    weeklyMenuDao = database.weeklyMenuDao()
    menuTemplateDao = database.menuTemplateDao()
    dailyAssignmentDao = database.dailyAssignmentDao()
    runBlocking {
      database.familyMemberDao().upsert(
        FamilyMemberEntity(
          id = actorUserId,
          displayName = "Admin",
          roleLabel = "Admin",
          avatarColor = "#000000",
          role = MemberRole.ADMIN,
        )
      )
    }
    coordinator = MenuMutationCoordinator(
      database = database,
      weeklyMenuDao = weeklyMenuDao,
      menuTemplateDao = menuTemplateDao,
      dailyAssignmentDao = dailyAssignmentDao,
      familyMemberDao = database.familyMemberDao(),
      cloudDeletionLedger = deletionLedger,
      clock = DefaultMenuMutationClock(wallClock = { 9_000L }),
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun replaceWeeklyMenu_secondReplaceReadsFirstResultWithStrictMonotonicTimestamps() = runBlocking {
    val first = coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(menuEntry(customDish = "First")),
      ),
    )
    assertThat(first.updatedAt).isEqualTo(9_000L)
    assertThat(first.entries.single().updatedAt).isEqualTo(9_000L)

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(
          menuEntry(customDish = "First"),
          menuEntry(day = 1, meal = MealType.LUNCH, customDish = "Second"),
        ),
      ),
    )

    val stored = weeklyMenuDao.getByStartDate(weekStart)!!
    assertThat(stored.entries).hasSize(2)
    assertThat(stored.entries.map { it.customDish }).containsExactly("First", "Second")
    assertThat(stored.menu.updatedAt).isEqualTo(9_001L)
    assertThat(stored.entries.single { it.customDish == "Second" }.updatedAt).isEqualTo(9_001L)
    assertThat(stored.entries.single { it.customDish == "First" }.updatedAt).isEqualTo(9_000L)
  }

  @Test
  fun replaceWeeklyMenu_unchangedChildPreservesTimestamp() = runBlocking {
    val unchangedUpdatedAt = 250L
    weeklyMenuDao.upsertMenu(parentEntity(updatedAt = 100L))
    weeklyMenuDao.upsertEntries(
      listOf(
        entryEntity(customDish = "Keep", updatedAt = unchangedUpdatedAt),
        entryEntity(day = 1, meal = "LUNCH", customDish = "Remove", updatedAt = 300L),
      ),
    )

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        entries = listOf(
          menuEntry(customDish = "Keep"),
          menuEntry(day = 2, meal = MealType.DINNER, customDish = "New"),
        ),
      ),
    )

    val stored = weeklyMenuDao.getByStartDate(weekStart)!!
    val kept = stored.entries.single { it.customDish == "Keep" }
    assertThat(kept.updatedAt).isEqualTo(unchangedUpdatedAt)
    assertThat(stored.entries.single { it.customDish == "New" }.updatedAt).isEqualTo(9_000L)
    assertThat(stored.entries.none { it.customDish == "Remove" }).isTrue()
  }

  @Test
  fun replaceWeeklyMenu_concurrentReplacesAreSerialized() = runBlocking {
    weeklyMenuDao.upsertMenu(parentEntity(updatedAt = 1L))
    weeklyMenuDao.upsertEntries(listOf(entryEntity(customDish = "Seed", updatedAt = 1L)))

    val ready = CountDownLatch(2)
    val start = CountDownLatch(1)
    val io = Dispatchers.IO
    val first = async(io) {
      ready.countDown()
      ready.await()
      start.await()
      coordinator.replaceWeeklyMenu(actorUserId,
        weeklyMenu(entries = listOf(menuEntry(customDish = "First"))),
      )
    }
    val second = async(io) {
      ready.countDown()
      ready.await()
      start.await()
      coordinator.replaceWeeklyMenu(actorUserId,
        weeklyMenu(entries = listOf(menuEntry(customDish = "Second"))),
      )
    }
    ready.await()
    start.countDown()
    val firstResult = first.await()
    val secondResult = second.await()

    val stamps = listOf(firstResult.updatedAt, secondResult.updatedAt).sorted()
    assertThat(stamps).containsExactly(9_000L, 9_001L).inOrder()

    val stored = weeklyMenuDao.getByStartDate(weekStart)!!
    assertThat(stored.entries.single().customDish).isAnyOf("First", "Second")
    assertThat(stored.menu.updatedAt).isEqualTo(9_001L)
    assertThat(stored.entries.single().updatedAt).isEqualTo(9_001L)
  }

  @Test
  fun replaceWeeklyMenu_canonicalIdsPersistedInRoom() = runBlocking {
    val legacyParentId = "legacy-week"
    weeklyMenuDao.upsertMenu(parentEntity(id = legacyParentId, updatedAt = 1L))

    coordinator.replaceWeeklyMenu(actorUserId,
      weeklyMenu(
        id = legacyParentId,
        entries = listOf(menuEntry(customDish = "Toast")),
      ),
    )

    val stored = weeklyMenuDao.getByStartDate(weekStart)!!
    assertThat(stored.menu.id).isEqualTo(canonicalParentId)
    assertThat(weeklyMenuDao.getByStartDate(weekStart)?.menu?.id).isEqualTo(canonicalParentId)
    assertThat(stored.entries.single().id)
      .isEqualTo(MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"))
    assertThat(stored.entries.single().cookingTaskId).isEqualTo("cook-0")
    assertThat(stored.entries.single().prepTaskId).isEqualTo("prep-0")
  }

  @Test
  fun replaceMenuTemplate_nullsTaskRefs() = runBlocking {
    val templateId = "tpl-1"
    menuTemplateDao.insertTemplate(
      MenuTemplateEntity(templateId, "Week", createdAt = 1L, updatedAt = 1L),
    )
    menuTemplateDao.insertEntries(
      listOf(
        MenuTemplateEntryEntity(
          id = MenuSyncIds.menuTemplateEntryId(templateId, 0, "BREAKFAST"),
          templateId = templateId,
          dayOfWeek = 0,
          mealType = "BREAKFAST",
          recipeId = null,
          customDish = "Toast",
          servings = 1,
          assignedMemberId = null,
          cookingTaskId = "old-cook",
          prepTaskId = "old-prep",
          updatedAt = 50L,
        ),
      ),
    )

    coordinator.replaceMenuTemplate(actorUserId,
      MenuTemplate(
        id = templateId,
        name = "Week",
        createdAt = 1L,
        updatedAt = 0L,
        entries = listOf(
          MenuEntry(
            id = MenuSyncIds.menuTemplateEntryId(templateId, 0, "BREAKFAST"),
            menuId = templateId,
            dayOfWeek = 0,
            mealType = MealType.BREAKFAST,
            recipeId = null,
            customDish = "Toast",
            servings = 1,
            assignedMemberId = null,
            cookingTaskId = "new-cook",
            prepTaskId = "new-prep",
            updatedAt = 0L,
          ),
        ),
      ),
    )

    val stored = menuTemplateDao.getById(templateId)!!
    assertThat(stored.entries.single().cookingTaskId).isNull()
    assertThat(stored.entries.single().prepTaskId).isNull()
    assertThat(stored.entries.single().updatedAt).isEqualTo(9_000L)
    assertThat(stored.template.updatedAt).isEqualTo(9_000L)
  }

  @Test
  fun replaceWeeklyMenu_longMaxValueThrowsWithoutPersistingMutation() = runBlocking {
    weeklyMenuDao.upsertMenu(parentEntity(updatedAt = Long.MAX_VALUE))

    assertThrows(MenuTimestampOverflowException::class.java) {
      runBlocking {
        coordinator.replaceWeeklyMenu(actorUserId,
          weeklyMenu(entries = listOf(menuEntry(customDish = "Blocked"))),
        )
      }
    }

    val stored = weeklyMenuDao.getByStartDate(weekStart)!!
    assertThat(stored.menu.updatedAt).isEqualTo(Long.MAX_VALUE)
    assertThat(stored.entries).isEmpty()
  }

  @Test
  fun replaceWeeklyMenu_daoFailureRollsBackEntireTransaction() = runBlocking {
    weeklyMenuDao.upsertMenu(parentEntity(updatedAt = 10L))
    weeklyMenuDao.upsertEntries(
      listOf(
        entryEntity(customDish = "Keep", updatedAt = 20L),
        entryEntity(day = 1, meal = "LUNCH", customDish = "Also keep", updatedAt = 30L),
      ),
    )
    val sqlite = database.openHelper.writableDatabase
    installAbortOnSecondMenuEntryUpsert(sqlite)
    try {
      assertThrows(SQLiteException::class.java) {
        runBlocking {
          coordinator.replaceWeeklyMenu(actorUserId,
            weeklyMenu(
              entries = listOf(
                menuEntry(customDish = "Keep"),
                menuEntry(day = 1, meal = MealType.LUNCH, customDish = "Changed"),
                menuEntry(day = 2, meal = MealType.DINNER, customDish = "New"),
              ),
            ),
          )
        }
      }

      val stored = weeklyMenuDao.getByStartDate(weekStart)!!
      assertThat(stored.menu.updatedAt).isEqualTo(10L)
      assertThat(stored.entries).hasSize(2)
      assertThat(stored.entries.single { it.dayOfWeek == 1 }.customDish).isEqualTo("Also keep")
    } finally {
      sqlite.execSQL("DROP TRIGGER IF EXISTS abort_second_menu_entry_upsert")
    }
  }

  @Test
  fun upsertDailyAssignment_concurrentDay0AndDay1_persistsBothRows() = runBlocking {
    val day0 = dailyAssignment(day = 0, memberId = "member-a")
    val day1 = dailyAssignment(day = 1, memberId = "member-b")
    val first = async(Dispatchers.IO) { coordinator.upsertDailyAssignment(actorUserId, day0) }
    val second = async(Dispatchers.IO) { coordinator.upsertDailyAssignment(actorUserId, day1) }
    first.await()
    second.await()

    val stored = dailyAssignmentDao.getByWeek(weekStart)
    assertThat(stored.map { it.dayOfWeek }.toSet()).containsExactly(0, 1)
    assertThat(stored.single { it.dayOfWeek == 0 }.assignedMemberId).isEqualTo("member-a")
    assertThat(stored.single { it.dayOfWeek == 1 }.assignedMemberId).isEqualTo("member-b")
  }

  @Test
  fun upsertDailyAssignment_samePayloadDifferentInputUpdatedAt_preservesPersistedTimestamp() = runBlocking {
    dailyAssignmentDao.upsertAssignment(
      dailyAssignmentEntity(day = 2, memberId = "member-a", updatedAt = 5_000L),
    )

    val result = coordinator.upsertDailyAssignment(
      actorUserId,
      dailyAssignment(day = 2, updatedAt = 0L, memberId = "member-a"),
    )

    assertThat(result.updatedAt).isEqualTo(5_000L)
    assertThat(dailyAssignmentDao.getByWeek(weekStart).single().updatedAt).isEqualTo(5_000L)
  }

  @Test
  fun upsertDailyAssignment_changedPayload_increasesTimestamp() = runBlocking {
    dailyAssignmentDao.upsertAssignment(
      dailyAssignmentEntity(day = 2, memberId = "old", updatedAt = 5_000L),
    )

    val result = coordinator.upsertDailyAssignment(
      actorUserId,
      dailyAssignment(day = 2, memberId = "new"),
    )

    assertThat(result.updatedAt).isGreaterThan(5_000L)
    assertThat(dailyAssignmentDao.getByWeek(weekStart).single().assignedMemberId).isEqualTo("new")
  }

  @Test
  fun upsertDailyAssignment_childRole_writesNothing() = runBlocking {
    database.familyMemberDao().upsert(
      FamilyMemberEntity(
        id = actorUserId,
        displayName = "Child",
        roleLabel = "Child",
        avatarColor = "#000000",
        role = MemberRole.CHILD,
      ),
    )

    val error = runCatching {
      coordinator.upsertDailyAssignment(actorUserId, dailyAssignment(day = 0))
    }.exceptionOrNull()

    assertThat(error).isInstanceOf(MenuMutationForbiddenException::class.java)
    assertThat(dailyAssignmentDao.getByWeek(weekStart)).isEmpty()
  }

  @Test
  fun upsertDailyAssignment_staleCohort_leavesRoomUntouched() = runBlocking {
    dailyAssignmentDao.upsertAssignment(
      dailyAssignmentEntity(day = 0, memberId = null, updatedAt = 100L),
    )
    val gateDao = StaleCohortGateDailyAssignmentDao(dailyAssignmentDao)
    val staleLedger = RecordingDeletionLedger()
    val gatedCoordinator = MenuMutationCoordinator(
      database = database,
      weeklyMenuDao = weeklyMenuDao,
      menuTemplateDao = menuTemplateDao,
      dailyAssignmentDao = gateDao,
      familyMemberDao = database.familyMemberDao(),
      cloudDeletionLedger = staleLedger,
      clock = DefaultMenuMutationClock(wallClock = { 9_000L }),
    )
    val injectedDay1 = dailyAssignmentEntity(day = 1, memberId = "inject", updatedAt = 50L)

    val upsertDeferred = async(Dispatchers.IO) {
      runCatching {
        gatedCoordinator.upsertDailyAssignment(
          actorUserId,
          dailyAssignment(day = 0, memberId = "new"),
        )
      }
    }

    gateDao.awaitFirstRead()
    dailyAssignmentDao.upsertAssignment(injectedDay1)
    gateDao.releaseCoordinator()

    val error = upsertDeferred.await().exceptionOrNull()
    assertThat(error).isInstanceOf(MenuMutationStaleException::class.java)
    assertThat(gateDao.upsertInvocations.get()).isEqualTo(0)
    assertThat(staleLedger.cleared).isEmpty()

    val rows = dailyAssignmentDao.getByWeek(weekStart)
    assertThat(rows).hasSize(2)
    val day0 = rows.single { it.dayOfWeek == 0 }
    assertThat(day0.assignedMemberId).isNull()
    assertThat(day0.updatedAt).isEqualTo(100L)
    val day1 = rows.single { it.dayOfWeek == 1 }
    assertThat(day1.assignedMemberId).isEqualTo("inject")
    assertThat(day1.updatedAt).isEqualTo(50L)
  }

  @Test
  fun deleteDailyAssignmentsForWeek_recordsSingleLedgerBatchAndDeletesAllRows() {
    runBlocking {
      dailyAssignmentDao.upsertAssignment(dailyAssignmentEntity(day = 0, updatedAt = 10L))
      dailyAssignmentDao.upsertAssignment(dailyAssignmentEntity(day = 1, updatedAt = 20L))

      coordinator.deleteDailyAssignmentsForWeek(actorUserId, weekStart)

      assertThat(dailyAssignmentDao.getByWeek(weekStart)).isEmpty()
      assertThat(deletionLedger.recordBatches).hasSize(1)
      assertThat(deletionLedger.recordBatches.single()).hasSize(2)
      assertThat(deletionLedger.recordBatches.single().map { it.first }.toSet())
        .containsExactly(DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS)
    }
  }

  private fun installAbortOnSecondMenuEntryUpsert(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TRIGGER abort_second_menu_entry_upsert
      AFTER INSERT ON menu_entries
      WHEN NEW.customDish = 'Changed'
      BEGIN
          SELECT RAISE(ABORT, 'test abort second menu entry upsert');
      END
      """.trimIndent(),
    )
  }

  private fun parentEntity(
    id: String = canonicalParentId,
    updatedAt: Long = 0L,
  ) = WeeklyMenuEntity(
    id = id,
    startDateMillis = weekStart,
    isAutoGenerated = false,
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

  private fun menuEntry(
    day: Int = 0,
    meal: MealType = MealType.BREAKFAST,
    customDish: String = "Toast",
    cookingTaskId: String? = "cook-$day",
    prepTaskId: String? = "prep-$day",
  ) = MenuEntry(
    id = MenuSyncIds.menuEntryId(weekStart, day, meal.name),
    menuId = canonicalParentId,
    dayOfWeek = day,
    mealType = meal,
    recipeId = null,
    customDish = customDish,
    servings = 1,
    assignedMemberId = null,
    cookingTaskId = cookingTaskId,
    prepTaskId = prepTaskId,
    updatedAt = 0L,
  )

  private fun dailyAssignment(
    day: Int,
    memberId: String? = null,
    updatedAt: Long = 0L,
  ) = DailyAssignment(
    id = MenuSyncIds.dailyAssignmentId(weekStart, day),
    weekStartMillis = weekStart,
    dayOfWeek = day,
    assignedMemberId = memberId,
    updatedAt = updatedAt,
  )

  private fun dailyAssignmentEntity(
    day: Int,
    memberId: String? = null,
    updatedAt: Long = 0L,
  ) = DailyAssignmentEntity(
    id = MenuSyncIds.dailyAssignmentId(weekStart, day),
    weekStartMillis = weekStart,
    dayOfWeek = day,
    assignedMemberId = memberId,
    updatedAt = updatedAt,
  )

  private class RecordingDeletionLedger : CloudDeletionLedgerPort {
    val recorded = mutableListOf<Pair<String, String>>()
    val recordBatches = mutableListOf<List<Pair<String, String>>>()
    val cleared = mutableListOf<Pair<String, String>>()

    override suspend fun recordDeletions(entries: Collection<Pair<String, String>>) {
      recordBatches.add(entries.toList())
      recorded.addAll(entries)
    }

    override suspend fun clearDeletions(entries: Collection<Pair<String, String>>) {
      cleared.addAll(entries)
    }
  }

  /**
   * Pauses [MenuMutationCoordinator] after the first transactional cohort read so the test can
   * commit a concurrent mutation through the real DAO before the second read inside Room.
   */
  private class StaleCohortGateDailyAssignmentDao(
    private val delegate: DailyAssignmentDao,
  ) : DailyAssignmentDao {
    private val getByWeekInvocations = AtomicInteger(0)
    private val firstReadDone = CountDownLatch(1)
    private val releaseGate = CompletableDeferred<Unit>()
    val upsertInvocations = AtomicInteger(0)

    suspend fun awaitFirstRead() {
      firstReadDone.await()
    }

    fun releaseCoordinator() {
      releaseGate.complete(Unit)
    }

    override suspend fun getByWeek(weekStart: Long): List<DailyAssignmentEntity> {
      val snapshot = delegate.getByWeek(weekStart)
      if (getByWeekInvocations.incrementAndGet() == 1) {
        firstReadDone.countDown()
        releaseGate.await()
      }
      return snapshot
    }

    override suspend fun upsertAssignment(assignment: DailyAssignmentEntity) {
      upsertInvocations.incrementAndGet()
      delegate.upsertAssignment(assignment)
    }

    override fun observeByWeek(weekStart: Long) = delegate.observeByWeek(weekStart)

    override suspend fun deleteByIds(ids: List<String>) = delegate.deleteByIds(ids)

    override suspend fun getAllSnapshot(): List<DailyAssignmentEntity> = delegate.getAllSnapshot()

    override suspend fun clearAll() = delegate.clearAll()

    override suspend fun deleteByWeek(weekStart: Long) = delegate.deleteByWeek(weekStart)
  }
}
