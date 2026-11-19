package com.familyhub.data.menu

import com.familyhub.data.local.entity.MenuEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.local.entity.WeeklyMenuEntity
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import com.familyhub.domain.model.MealType
import com.familyhub.domain.model.MenuEntry
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.MenuSyncIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuReplacePlannerTest {

  private val weekStart = 1_700_000_000_000L
  private val canonicalParentId = MenuSyncIds.weeklyMenuId(weekStart)

  @Test
  fun weeklyReplace_classifiesChangedUnchangedAndRemoved() {
    val unchangedEntry = weeklyEntryEntity(
      day = 0,
      meal = "BREAKFAST",
      customDish = "Oatmeal",
      updatedAt = 100L,
    )
    val removedEntry = weeklyEntryEntity(
      day = 1,
      meal = "LUNCH",
      customDish = "Soup",
      updatedAt = 200L,
    )
    val current = WeeklyMenuWithEntries(
      menu = weeklyParent(updatedAt = 50L),
      entries = listOf(unchangedEntry, removedEntry),
    )
    val desired = weeklyMenu(
      entries = listOf(
        menuEntry(day = 0, meal = MealType.BREAKFAST, customDish = "Oatmeal"),
        menuEntry(day = 2, meal = MealType.DINNER, customDish = "Pasta"),
      ),
    )

    val plan = MenuReplacePlanner.planWeeklyReplace(current, desired)

    assertEquals(1, plan.unchangedEntries.size)
    assertEquals(unchangedEntry.updatedAt, plan.unchangedEntries.single().updatedAt)
    assertEquals(1, plan.changedEntries.size)
    assertEquals(2, plan.changedEntries.single().dayOfWeek)
    assertEquals(1, plan.removedEntries.size)
    assertEquals(removedEntry.id, plan.removedEntries.single().id)
    assertEquals(0, plan.changedSources.size)
    assertFalse(plan.parentPayloadChanged)
  }

  @Test
  fun weeklyReplace_changedSourceTrackedForUpdatedSlot() {
    val existing = weeklyEntryEntity(day = 0, meal = "BREAKFAST", customDish = "Old", updatedAt = 50L)
    val current = WeeklyMenuWithEntries(
      menu = weeklyParent(updatedAt = 10L),
      entries = listOf(existing),
    )
    val desired = weeklyMenu(
      entries = listOf(menuEntry(day = 0, meal = MealType.BREAKFAST, customDish = "New")),
    )

    val plan = MenuReplacePlanner.planWeeklyReplace(current, desired)

    assertEquals(1, plan.changedEntries.size)
    assertEquals(1, plan.changedSources.size)
    assertEquals(existing.id, plan.changedSources.single().id)
  }

  @Test
  fun weeklyReplace_removedChildIncludedInMaxTimestamp() {
    val removed = weeklyEntryEntity(day = 1, meal = "LUNCH", updatedAt = 900L)
    val current = WeeklyMenuWithEntries(
      menu = weeklyParent(updatedAt = 100L),
      entries = listOf(removed),
    )
    val plan = MenuReplacePlanner.planWeeklyReplace(current, weeklyMenu(entries = emptyList()))

    assertEquals(
      900L,
      MenuReplacePlanner.maxMutationUpdatedAt(
        parentUpdatedAt = 100L,
        cohortUpdatedAts = (plan.changedSources + plan.removedEntries).map { it.updatedAt },
      ),
    )
  }

  @Test
  fun weeklyReplace_writesCanonicalIds() {
    val legacyParentId = "legacy-week"
    val current = WeeklyMenuWithEntries(
      menu = weeklyParent(id = legacyParentId, updatedAt = 10L),
      entries = emptyList(),
    )
    val desired = weeklyMenu(id = legacyParentId, entries = listOf(menuEntry()))

    val plan = MenuReplacePlanner.planWeeklyReplace(current, desired)

    assertEquals(canonicalParentId, plan.canonicalParentId)
    assertEquals(legacyParentId, plan.legacyParentId)
    assertEquals(canonicalParentId, plan.parentEntity.id)
    assertEquals(
      MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
      plan.changedEntries.single().id,
    )
  }

  @Test
  fun weeklyReplace_preservesWeeklyTaskRefs() {
    val desired = weeklyMenu(
      entries = listOf(
        menuEntry(
          cookingTaskId = "cook-1",
          prepTaskId = "prep-1",
        ),
      ),
    )
    val plan = MenuReplacePlanner.planWeeklyReplace(null, desired)

    assertEquals("cook-1", plan.changedEntries.single().cookingTaskId)
    assertEquals("prep-1", plan.changedEntries.single().prepTaskId)
  }

  @Test
  fun templateReplace_clearsStoredTaskRefsViaChangedEntry() {
    val templateId = "tpl-1"
    val current = MenuTemplateWithEntries(
      template = MenuTemplateEntity(templateId, "Week", createdAt = 1L, updatedAt = 5L),
      entries = listOf(
        templateEntryEntity(
          templateId = templateId,
          day = 0,
          meal = "BREAKFAST",
          cookingTaskId = "old-cook",
          prepTaskId = "old-prep",
          updatedAt = 30L,
        ),
      ),
    )
    val desired = menuTemplate(
      id = templateId,
      entries = listOf(
        menuEntry(
          cookingTaskId = "new-cook",
          prepTaskId = "new-prep",
        ),
      ),
    )

    val plan = MenuReplacePlanner.planTemplateReplace(current, desired)

    assertEquals(1, plan.changedEntries.size)
    assertEquals(0, plan.unchangedEntries.size)
    assertNull(plan.changedEntries.single().cookingTaskId)
    assertNull(plan.changedEntries.single().prepTaskId)
    assertTrue(plan.hasMutations(current))
  }

  @Test
  fun templateReplace_changedEntryUsesCanonicalId() {
    val templateId = "tpl-2"
    val desired = menuTemplate(
      id = templateId,
      entries = listOf(menuEntry(customDish = "Salad")),
    )
    val plan = MenuReplacePlanner.planTemplateReplace(null, desired)

    assertEquals(
      MenuSyncIds.menuTemplateEntryId(templateId, 0, "BREAKFAST"),
      plan.changedEntries.single().id,
    )
    assertNull(plan.changedEntries.single().cookingTaskId)
    assertNull(plan.changedEntries.single().prepTaskId)
  }

  @Test
  fun templateReplace_noOpWhenStoredRefsAlreadyNull() {
    val templateId = "tpl-3"
    val entry = templateEntryEntity(templateId = templateId, updatedAt = 30L)
    val current = MenuTemplateWithEntries(
      template = MenuTemplateEntity(templateId, "Week", createdAt = 1L, updatedAt = 5L),
      entries = listOf(entry),
    )
    val desired = menuTemplate(
      id = templateId,
      entries = listOf(menuEntry(customDish = "Toast")),
    )

    val plan = MenuReplacePlanner.planTemplateReplace(current, desired)

    assertFalse(plan.hasMutations(current))
  }

  @Test
  fun weeklyReplace_noOpWhenNothingChanged() {
    val entry = weeklyEntryEntity(updatedAt = 77L)
    val current = WeeklyMenuWithEntries(
      menu = weeklyParent(id = canonicalParentId, updatedAt = 55L),
      entries = listOf(entry),
    )
    val desired = weeklyMenu(
      id = canonicalParentId,
      entries = listOf(
        menuEntry(
          id = entry.id,
          customDish = entry.customDish ?: "Toast",
          updatedAt = entry.updatedAt,
        ),
      ),
    )

    val plan = MenuReplacePlanner.planWeeklyReplace(current, desired)

    assertFalse(plan.hasMutations(current))
  }

  private fun weeklyParent(
    id: String = canonicalParentId,
    updatedAt: Long = 0L,
  ) = WeeklyMenuEntity(
    id = id,
    startDateMillis = weekStart,
    isAutoGenerated = false,
    createdAt = 1L,
    updatedAt = updatedAt,
  )

  private fun weeklyEntryEntity(
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
    cookingTaskId = null,
    prepTaskId = null,
    updatedAt = updatedAt,
  )

  private fun templateEntryEntity(
    templateId: String,
    day: Int = 0,
    meal: String = "BREAKFAST",
    cookingTaskId: String? = null,
    prepTaskId: String? = null,
    updatedAt: Long = 0L,
  ) = MenuTemplateEntryEntity(
    id = MenuSyncIds.menuTemplateEntryId(templateId, day, meal),
    templateId = templateId,
    dayOfWeek = day,
    mealType = meal,
    recipeId = null,
    customDish = "Toast",
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
    id: String,
    name: String = "Week",
    entries: List<MenuEntry> = listOf(menuEntry()),
  ) = MenuTemplate(
    id = id,
    name = name,
    createdAt = 1L,
    updatedAt = 0L,
    entries = entries,
  )

  private fun menuEntry(
    id: String = MenuSyncIds.menuEntryId(weekStart, 0, "BREAKFAST"),
    day: Int = 0,
    meal: MealType = MealType.BREAKFAST,
    customDish: String = "Toast",
    cookingTaskId: String? = null,
    prepTaskId: String? = null,
    updatedAt: Long = 0L,
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
