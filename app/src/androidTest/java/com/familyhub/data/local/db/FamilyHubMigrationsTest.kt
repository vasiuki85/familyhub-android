package com.familyhub.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familyhub.di.MIGRATION_23_24
import com.familyhub.di.MIGRATION_24_25
import com.familyhub.di.MIGRATION_25_26
import com.familyhub.di.MIGRATION_26_27
import com.familyhub.di.MIGRATION_27_28
import com.familyhub.di.MIGRATION_28_29
import com.familyhub.di.MIGRATION_29_30
import com.familyhub.di.MIGRATION_30_31
import com.familyhub.di.MIGRATION_31_32
import com.familyhub.di.MIGRATION_32_33
import com.familyhub.di.MIGRATION_33_34
import com.familyhub.di.MIGRATION_34_35
import com.familyhub.di.MIGRATION_35_36
import com.familyhub.di.MIGRATION_36_37
import com.familyhub.di.MIGRATION_37_38
import com.familyhub.di.MIGRATION_38_39
import com.familyhub.domain.utils.MenuSyncIds
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private const val TEST_DB = "familyhub-migration-test.db"

/**
 * Migration tests backed by exported Room schema JSON under app/schemas.
 *
 * Unavailable historical schemas (never existed or unrestorable from git):
 * 24, 25, 31, 34, 35. Neighboring available schemas are used where noted.
 */
@RunWith(AndroidJUnit4::class)
class FamilyHubMigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyHubDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val allMigrationsFrom23 = arrayOf(
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
    )

    @Test
    fun currentSchema39_matchesExportedSchema() {
        val json = readAssetSchema(39)
        val root = JSONObject(json)
        assertThat(root.getJSONObject("database").getInt("version")).isEqualTo(39)
        assertThat(root.getJSONObject("database").getString("identityHash")).isNotEmpty()
    }

    @Test
    fun migrate23To26_preservesItemsAndBootstrapsDefaults() {
        helper.createDatabase(TEST_DB, 23).apply {
            execSQL(
                """
                INSERT INTO shopping_items (id, title, createdByUserId, isBought, createdAt)
                VALUES ('item1', 'Молоко', 'user1', 0, 1000)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26
        ).use { db ->
            db.query("SELECT listId FROM shopping_items WHERE id = 'item1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("default_list")
            }

            db.query("SELECT COUNT(*) FROM shopping_lists WHERE id = 'default_list'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
            }

            db.query("PRAGMA table_info(shopping_activity)").use { cursor ->
                val columnNames = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columnNames += cursor.getString(nameIndex)
                }
                assertThat(columnNames).containsAtLeast(
                    "id",
                    "listId",
                    "actionType",
                    "actorId",
                    "occurredAt"
                )
            }
        }
    }

    @Test
    fun migrate24To26_addsMissingColumns() {
        // Schema v24 never existed; start from exported v23.
        helper.createDatabase(TEST_DB, 23).apply {
            execSQL(
                """
                INSERT INTO shopping_items (id, title, createdByUserId, isBought, createdAt)
                VALUES ('legacy-item', 'Хлеб', 'user1', 0, 42)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26
        ).use { db ->
            val expectedColumns = setOf(
                "id",
                "title",
                "colorHex",
                "iconName",
                "createdAt",
                "isArchived",
                "archivedAt",
                "templateKey",
                "participantIds"
            )
            db.query("PRAGMA table_info(shopping_lists)").use { cursor ->
                val actual = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    actual += cursor.getString(nameIndex)
                }
                assertThat(actual).containsAtLeastElementsIn(expectedColumns)
            }

            db.query("PRAGMA table_info(shopping_activity)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }

            db.query("SELECT listId FROM shopping_items WHERE id = 'legacy-item'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("default_list")
            }
        }
    }

    @Test
    fun migrate25To26_createsGoalPlanTables() {
        // Schema v25 never existed; start from exported v23.
        helper.createDatabase(TEST_DB, 23).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26
        ).use { db ->
            db.query("PRAGMA table_info(goal_stages)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
            db.query("PRAGMA table_info(goal_steps)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
        }
    }

    @Test
    fun migrate26To33_addsLaterTablesAndColumns() {
        helper.createDatabase(TEST_DB, 26).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            33,
            true,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33
        ).use { db ->
            db.query("PRAGMA table_info(emergency_contacts)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
            db.query("PRAGMA table_info(daily_assignments)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
            db.query("PRAGMA table_info(penalties)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
        }
    }

    @Test
    fun migrate33To34_addsTaskIndexes() {
        // Schema v34 unrestorable; validate indexes after 33→36.
        helper.createDatabase(TEST_DB, 33).apply {
            execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, createdByUserId, assignedToUserId, dueDate,
                    repeatRule, status, createdAt, orderIndex, isPrivate, taskType, priority,
                    rewardPoints, categoryId, requiresApproval, attachments, comments
                ) VALUES (
                    't-index', 'Indexed', NULL, 'parent', 'child', NULL,
                    'NONE', 'TODO', 1, 0, 0, 'ROUTINE', 'MEDIUM',
                    NULL, NULL, 0, '[]', '[]'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            36,
            true,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36
        ).use { db ->
            db.query("PRAGMA index_list(tasks)").use { cursor ->
                val indexNames = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    indexNames += cursor.getString(nameIndex)
                }
                assertThat(indexNames).containsAtLeast(
                    "index_tasks_status",
                    "index_tasks_assignedToUserId",
                    "index_tasks_dueDate"
                )
            }
        }
    }

    @Test
    fun migrate31To32_addsBirthDateToFamilyMembers() {
        // Schema v31 unrestorable; start from exported v30.
        helper.createDatabase(TEST_DB, 30).apply {
            execSQL(
                """
                INSERT INTO family_members (id, displayName, roleLabel, avatarColor, role, age, photoUrl)
                VALUES ('user-1', 'Test', 'Test', '#2196F3', 'CHILD', 10, NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            32,
            true,
            MIGRATION_30_31,
            MIGRATION_31_32
        ).use { db ->
            db.query("PRAGMA table_info(family_members)").use { cursor ->
                val columnNames = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columnNames += cursor.getString(nameIndex)
                }
                assertThat(columnNames).contains("birthDate")
            }
            db.query("SELECT birthDate FROM family_members WHERE id = 'user-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.isNull(0)).isTrue()
            }
        }
    }

    @Test
    fun migrate29To30_addsPersonalFlagToFamilyGoals() {
        helper.createDatabase(TEST_DB, 29).apply {
            execSQL(
                """
                INSERT INTO family_goals (
                    id, title, description, createdByUserId, targetPoints, currentPoints,
                    status, deadlineAt, createdAt, updatedAt
                ) VALUES (
                    'goal-1', 'Goal', NULL, 'user-1', 10, 0,
                    'ACTIVE', NULL, 1000, 1000
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            30,
            true,
            MIGRATION_29_30
        ).use { db ->
            db.query("SELECT personal FROM family_goals WHERE id = 'goal-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate34To35_addsReversalAndUpdatedAtColumns() {
        // Schemas v34/v35 unrestorable; start from exported v33.
        helper.createDatabase(TEST_DB, 33).apply {
            execSQL(
                """
                INSERT INTO task_completions (
                    id, taskId, userId, taskType, completionTime, weekStartDate,
                    basePoints, bonusPoints, penaltyPoints, totalPoints
                ) VALUES ('c1', 't1', 'u1', 'ROUTINE', 100, 50, 10, 0, 0, 10)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO user_scores (userId, weekStartDate, score)
                VALUES ('u1', 50, 10)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO user_wallets (userId, balance) VALUES ('u1', 10)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            36,
            true,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36
        ).use { db ->
            db.query("SELECT reversedAt, updatedAt, generatedRepeatTaskId FROM task_completions WHERE id = 'c1'")
                .use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.isNull(0)).isTrue()
                    assertThat(cursor.getLong(1)).isEqualTo(0L)
                    assertThat(cursor.isNull(2)).isTrue()
                }
            db.query("SELECT updatedAt FROM user_scores WHERE userId = 'u1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(0L)
            }
            db.query("SELECT updatedAt FROM user_wallets WHERE userId = 'u1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(0L)
            }
        }
    }

    @Test
    fun migrate35To36_preservesExistingLedgerAndAddsCycleColumns() {
        // Schema v35 unrestorable; start from exported v33.
        helper.createDatabase(TEST_DB, 33).apply {
            execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, createdByUserId, assignedToUserId, dueDate,
                    repeatRule, status, createdAt, orderIndex, isPrivate, taskType, priority,
                    rewardPoints, categoryId, requiresApproval, attachments, comments
                ) VALUES (
                    't1', 'Task', NULL, 'parent', 'child', NULL,
                    'NONE', 'DONE', 100, 0, 0, 'ROUTINE', 'MEDIUM',
                    NULL, NULL, 0, '[]', '[]'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO task_completions (
                    id, taskId, userId, taskType, completionTime, weekStartDate,
                    basePoints, bonusPoints, penaltyPoints, totalPoints
                ) VALUES ('c1', 't1', 'child', 'ROUTINE', 100, 50, 10, 0, 0, 10)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            36,
            true,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36
        ).use { db ->
            db.query("SELECT completionCycle FROM tasks WHERE id = 't1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(0L)
            }
            db.query("SELECT completionCycle, totalPoints FROM task_completions WHERE id = 'c1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.isNull(0)).isTrue()
                assertThat(cursor.getInt(1)).isEqualTo(10)
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_task_completions_taskId_completionCycle'"
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }
        }
    }

    @Test
    fun fullChain_preservesRepresentativeData() {
        Assume.assumeTrue(
            "Requires exported schemas 23 and 39",
            assetSchemaExists(23) && assetSchemaExists(39)
        )

        helper.createDatabase(TEST_DB, 23).apply {
            execSQL(
                """
                INSERT INTO shopping_items (id, title, createdByUserId, isBought, createdAt)
                VALUES ('chain-item', 'Chain Milk', 'user1', 0, 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO family_members (id, displayName, roleLabel, avatarColor, role, age, photoUrl)
                VALUES ('chain-user', 'Chain', 'Child', '#2196F3', 'CHILD', 8, NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            39,
            true,
            *allMigrationsFrom23
        ).use { db ->
            db.query("SELECT listId, title FROM shopping_items WHERE id = 'chain-item'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("default_list")
                assertThat(cursor.getString(1)).isEqualTo("Chain Milk")
            }
            db.query("SELECT displayName, birthDate FROM family_members WHERE id = 'chain-user'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Chain")
                assertThat(cursor.isNull(1)).isTrue()
            }
            db.query("PRAGMA table_info(tasks)").use { cursor ->
                val columns = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
                assertThat(columns).contains("completionCycle")
            }
            db.query("PRAGMA table_info(weekly_menus)").use { cursor ->
                val columns = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
                assertThat(columns).contains("updatedAt")
            }
            // Verify outbox infrastructure exists after full chain
            db.query("PRAGMA table_info(sync_outbox)").use { cursor ->
                assertThat(cursor.count).isGreaterThan(0)
            }
            db.query("SELECT ignoreDepth FROM sync_tracking_control WHERE id = 1").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate36To37_duplicateWeekPreservesComplementarySlotsAndCanonicalIds() {
        Assume.assumeTrue("Requires exported schemas 36 and 37", assetSchemaExists(36) && assetSchemaExists(37))

        helper.createDatabase(TEST_DB, 36).apply {
            execSQL(
                """
                INSERT INTO weekly_menus (id, startDateMillis, isAutoGenerated, createdAt)
                VALUES ('old-a', 1000, 0, 10)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO weekly_menus (id, startDateMillis, isAutoGenerated, createdAt)
                VALUES ('old-b', 1000, 1, 20)
                """.trimIndent()
            )
            // Conflicting BREAKFAST slot: A's payload is stronger, but B's parent
            // createdAt=20 must win via inherited parent timestamp provenance.
            execSQL(
                """
                INSERT INTO menu_entries (
                    id, menuId, dayOfWeek, mealType, recipeId, customDish, servings,
                    assignedMemberId, cookingTaskId, prepTaskId
                ) VALUES ('e-a', 'old-a', 0, 'BREAKFAST', 'zzz', 'payload-favored', 9, NULL, 'cook-a', 'prep-a')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO menu_entries (
                    id, menuId, dayOfWeek, mealType, recipeId, customDish, servings,
                    assignedMemberId, cookingTaskId, prepTaskId
                ) VALUES ('e-b-conflict', 'old-b', 0, 'BREAKFAST', NULL, 'provenance-wins', 1, NULL, NULL, NULL)
                """.trimIndent()
            )
            // Complementary LUNCH slot from older parent must still be preserved.
            execSQL(
                """
                INSERT INTO menu_entries (
                    id, menuId, dayOfWeek, mealType, recipeId, customDish, servings,
                    assignedMemberId, cookingTaskId, prepTaskId
                ) VALUES ('e-b', 'old-b', 1, 'LUNCH', NULL, 'soup', 3, NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO menu_templates (id, name, createdAt)
                VALUES ('tpl-1', 'Weekday', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO menu_template_entries (
                    id, templateId, dayOfWeek, mealType, recipeId, customDish, servings,
                    assignedMemberId, cookingTaskId, prepTaskId
                ) VALUES ('te-1', 'tpl-1', 2, 'DINNER', NULL, 'pasta', 4, 'm1', 'should-null', 'also-null')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO daily_assignments (id, weekStartMillis, dayOfWeek, assignedMemberId, rewardPoints, isRated)
                VALUES ('da-old', 1000, 3, 'member-1', NULL, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO emergency_contacts (id, name, phoneNumber, description, createdAt)
                VALUES ('keep-contact', 'Keep', '+1000', NULL, 1)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 37, true, MIGRATION_36_37).use { db ->
            val canonicalMenuId = MenuSyncIds.weeklyMenuId(1000L)
            db.query("SELECT COUNT(*) FROM weekly_menus").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
            db.query("SELECT id, updatedAt FROM weekly_menus").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo(canonicalMenuId)
                assertThat(c.getLong(1)).isEqualTo(20L)
            }
            db.query("SELECT COUNT(*) FROM menu_entries").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(2)
            }
            db.query(
                "SELECT id, customDish, cookingTaskId, updatedAt FROM menu_entries WHERE dayOfWeek = 0"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo(MenuSyncIds.menuEntryId(1000L, 0, "BREAKFAST"))
                assertThat(c.getString(1)).isEqualTo("provenance-wins")
                assertThat(c.isNull(2)).isTrue()
                assertThat(c.getLong(3)).isEqualTo(20L)
            }
            db.query(
                "SELECT customDish FROM menu_entries WHERE dayOfWeek = 1"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("soup")
            }
            db.query(
                "SELECT cookingTaskId, prepTaskId, assignedMemberId FROM menu_template_entries"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.isNull(0)).isTrue()
                assertThat(c.isNull(1)).isTrue()
                assertThat(c.getString(2)).isEqualTo("m1")
            }
            db.query("SELECT id FROM daily_assignments").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo(MenuSyncIds.dailyAssignmentId(1000L, 3))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_weekly_menus_startDateMillis'"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
            db.query("SELECT name FROM emergency_contacts WHERE id = 'keep-contact'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Keep")
            }
            // Orphan check
            db.query(
                """
                SELECT COUNT(*) FROM menu_entries e
                LEFT JOIN weekly_menus m ON e.menuId = m.id
                WHERE m.id IS NULL
                """.trimIndent()
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate38To39_installsOutboxAndTriggersFire() {
        Assume.assumeTrue(
            "Requires exported schemas 38 and 39",
            assetSchemaExists(38) && assetSchemaExists(39)
        )

        helper.createDatabase(TEST_DB, 38).apply {
            // Insert a representative task (all columns as of v38, which added
            // dueDateHasTime, reminderMode, reminderMinutesOfDay to tasks)
            execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, createdByUserId, assignedToUserId, dueDate,
                    dueDateHasTime, reminderMode, reminderMinutesOfDay,
                    repeatRule, status, createdAt, orderIndex, isPrivate, taskType, priority,
                    rewardPoints, categoryId, requiresApproval, attachments, comments,
                    completionCycle
                ) VALUES (
                    'mig-task', 'Migration Task', NULL, 'adult', 'child', 1000,
                    0, 'NONE', NULL,
                    'NONE', 'TODO', 2000, 1, 0, 'ROUTINE', 'MEDIUM',
                    NULL, NULL, 0, '[]', '[]',
                    0
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39).use { db ->
            // --- Schema validation ---
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_outbox'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_tracking_control'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_sync_outbox_collectionKey_entityId'"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
            }

            // Control row must exist with ignoreDepth = 0 (tracking enabled)
            db.query("SELECT ignoreDepth FROM sync_tracking_control WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
            }

            // Verify original data survived
            db.query("SELECT title FROM tasks WHERE id = 'mig-task'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Migration Task")
            }

            // --- Trigger validation: post-migration writes must journal ---

            // 1. INSERT writes UPSERT with monotonic sequence
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, createdByUserId, assignedToUserId, dueDate,
                    dueDateHasTime, reminderMode, reminderMinutesOfDay,
                    repeatRule, status, createdAt, orderIndex, isPrivate, taskType, priority,
                    rewardPoints, categoryId, requiresApproval, attachments, comments,
                    completionCycle
                ) VALUES (
                    'trigger-insert', 'Inserted', NULL, 'adult', 'child', NULL,
                    0, 'NONE', NULL,
                    'NONE', 'TODO', 3000, 0, 0, 'ROUTINE', 'MEDIUM',
                    NULL, NULL, 0, '[]', '[]',
                    0
                )
                """.trimIndent()
            )
            db.query(
                "SELECT collectionKey, entityId, operation FROM sync_outbox WHERE entityId = 'trigger-insert'"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("tasks")
                assertThat(c.getString(1)).isEqualTo("trigger-insert")
                assertThat(c.getString(2)).isEqualTo("UPSERT")
            }

            // 2. UPDATE writes UPSERT (same entity) with higher sequence
            val seqAfterInsert = db.query("SELECT MAX(sequence) FROM sync_outbox").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                c.getLong(0)
            }
            db.execSQL(
                "UPDATE tasks SET title = 'Updated', status = 'DONE' WHERE id = 'trigger-insert'"
            )
            db.query(
                "SELECT MAX(sequence) FROM sync_outbox WHERE entityId = 'trigger-insert'"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getLong(0)).isGreaterThan(seqAfterInsert)
            }

            // 3. DELETE writes DELETE with yet higher sequence
            val seqAfterUpdate = db.query("SELECT MAX(sequence) FROM sync_outbox").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                c.getLong(0)
            }
            db.execSQL("DELETE FROM tasks WHERE id = 'trigger-insert'")
            db.query(
                "SELECT collectionKey, entityId, operation, sequence FROM sync_outbox WHERE entityId = 'trigger-insert' ORDER BY sequence DESC"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("tasks")
                assertThat(c.getString(1)).isEqualTo("trigger-insert")
                assertThat(c.getString(2)).isEqualTo("DELETE")
                assertThat(c.getLong(3)).isGreaterThan(seqAfterUpdate)
            }

            // Verify monotonic sequence across all rows
            db.query("SELECT sequence FROM sync_outbox ORDER BY sequence").use { c ->
                var prev = -1L
                while (c.moveToNext()) {
                    val cur = c.getLong(0)
                    assertThat(cur).isGreaterThan(prev)
                    prev = cur
                }
            }
        }
    }

    private fun assetSchemaExists(version: Int): Boolean =
        runCatching {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.familyhub.data.local.db.FamilyHubDatabase/$version.json")
                .close()
            true
        }.getOrDefault(false)

    private fun readAssetSchema(version: Int): String {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open("com.familyhub.data.local.db.FamilyHubDatabase/$version.json").use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
        }
    }
}
