package com.familyhub.data.sync

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

internal enum class SyncOutboxOperation {
    UPSERT,
    DELETE,
}

private data class DirectSyncTriggerSpec(
    val table: String,
    val collectionKey: String,
    val newEntityIdSql: String,
    val oldEntityIdSql: String = newEntityIdSql.replace("NEW.", "OLD."),
)

private val DIRECT_SYNC_TRIGGER_SPECS = listOf(
    DirectSyncTriggerSpec("family_members", "members", "NEW.`id`"),
    DirectSyncTriggerSpec("tasks", "tasks", "NEW.`id`"),
    DirectSyncTriggerSpec("shopping_lists", "shoppingLists", "NEW.`id`"),
    DirectSyncTriggerSpec("shopping_categories", "shoppingCategories", "NEW.`id`"),
    DirectSyncTriggerSpec("shopping_items", "shoppingItems", "NEW.`id`"),
    DirectSyncTriggerSpec("shopping_activity", "shoppingActivity", "NEW.`id`"),
    DirectSyncTriggerSpec("family_events", "events", "NEW.`id`"),
    DirectSyncTriggerSpec("task_completions", "taskCompletions", "NEW.`id`"),
    DirectSyncTriggerSpec(
        "user_achievements",
        "achievements",
        "NEW.`userId` || '_' || NEW.`type`",
        "OLD.`userId` || '_' || OLD.`type`",
    ),
    DirectSyncTriggerSpec("rewards", "rewards", "NEW.`id`"),
    DirectSyncTriggerSpec("reward_requests", "rewardRequests", "NEW.`id`"),
    DirectSyncTriggerSpec("user_wallets", "wallets", "NEW.`userId`"),
    DirectSyncTriggerSpec("task_rotations", "taskRotations", "NEW.`taskId`"),
    DirectSyncTriggerSpec(
        "user_scores",
        "scores",
        "NEW.`userId` || '_' || NEW.`weekStartDate`",
        "OLD.`userId` || '_' || OLD.`weekStartDate`",
    ),
    DirectSyncTriggerSpec("house_rules", "houseRules", "NEW.`id`"),
    DirectSyncTriggerSpec("task_templates", "taskTemplates", "NEW.`id`"),
    DirectSyncTriggerSpec("task_categories", "taskCategories", "NEW.`id`"),
    DirectSyncTriggerSpec("appreciations", "appreciations", "NEW.`id`"),
    DirectSyncTriggerSpec("family_goals", "familyGoals", "NEW.`id`"),
    DirectSyncTriggerSpec("goal_stages", "goalStages", "NEW.`id`"),
    DirectSyncTriggerSpec("goal_steps", "goalSteps", "NEW.`id`"),
    DirectSyncTriggerSpec("emergency_contacts", "emergencyContacts", "NEW.`id`"),
    DirectSyncTriggerSpec("penalties", "penalties", "NEW.`id`"),
    DirectSyncTriggerSpec("recipes", "recipes", "NEW.`id`"),
    DirectSyncTriggerSpec("weekly_menus", "weeklyMenus", "NEW.`id`"),
    DirectSyncTriggerSpec("menu_entries", "menuEntries", "NEW.`id`"),
    DirectSyncTriggerSpec("menu_templates", "menuTemplates", "NEW.`id`"),
    DirectSyncTriggerSpec("menu_template_entries", "menuTemplateEntries", "NEW.`id`"),
    DirectSyncTriggerSpec("daily_assignments", "dailyAssignments", "NEW.`id`"),
)

private const val TRACKING_ENABLED_SQL =
    "COALESCE((SELECT `ignoreDepth` FROM `sync_tracking_control` WHERE `id` = 1), 1) = 0"

/**
 * Installs the durable per-entity journal and triggers. The two tables are also Room entities;
 * CREATE IF NOT EXISTS keeps this function usable from both migration and fresh-database callbacks.
 */
internal fun installSyncOutboxInfrastructure(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_outbox` (
            `sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `collectionKey` TEXT NOT NULL,
            `entityId` TEXT NOT NULL,
            `operation` TEXT NOT NULL
        )
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_sync_outbox_collectionKey_entityId`
        ON `sync_outbox` (`collectionKey`, `entityId`)
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_tracking_control` (
            `id` INTEGER NOT NULL,
            `ignoreDepth` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    database.execSQL(
        "INSERT OR IGNORE INTO `sync_tracking_control` (`id`, `ignoreDepth`) VALUES (1, 0)"
    )

    DIRECT_SYNC_TRIGGER_SPECS.forEach { spec ->
        installDirectTriggers(database, spec)
    }
    installEmbeddedRecipeTriggers(database, "recipe_ingredients")
    installEmbeddedRecipeTriggers(database, "recipe_steps")
}

private fun installDirectTriggers(
    database: SupportSQLiteDatabase,
    spec: DirectSyncTriggerSpec,
) {
    val safeTable = spec.table.replace(Regex("[^A-Za-z0-9_]"), "_")
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_insert`
        AFTER INSERT ON `${spec.table}`
        WHEN $TRACKING_ENABLED_SQL
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('${spec.collectionKey}', ${spec.newEntityIdSql}, '${SyncOutboxOperation.UPSERT.name}');
        END
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_update`
        AFTER UPDATE ON `${spec.table}`
        WHEN $TRACKING_ENABLED_SQL
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('${spec.collectionKey}', ${spec.newEntityIdSql}, '${SyncOutboxOperation.UPSERT.name}');
        END
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_delete`
        AFTER DELETE ON `${spec.table}`
        WHEN $TRACKING_ENABLED_SQL
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('${spec.collectionKey}', ${spec.oldEntityIdSql}, '${SyncOutboxOperation.DELETE.name}');
        END
        """.trimIndent()
    )
}

private fun installEmbeddedRecipeTriggers(
    database: SupportSQLiteDatabase,
    table: String,
) {
    val safeTable = table.replace(Regex("[^A-Za-z0-9_]"), "_")
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_insert`
        AFTER INSERT ON `$table`
        WHEN $TRACKING_ENABLED_SQL
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('recipes', NEW.`recipeId`, '${SyncOutboxOperation.UPSERT.name}');
        END
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_update`
        AFTER UPDATE ON `$table`
        WHEN $TRACKING_ENABLED_SQL
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('recipes', NEW.`recipeId`, '${SyncOutboxOperation.UPSERT.name}');
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            SELECT 'recipes', OLD.`recipeId`, '${SyncOutboxOperation.UPSERT.name}'
            WHERE OLD.`recipeId` <> NEW.`recipeId`;
        END
        """.trimIndent()
    )
    database.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `sync_outbox_${safeTable}_delete`
        AFTER DELETE ON `$table`
        WHEN $TRACKING_ENABLED_SQL
          AND EXISTS (SELECT 1 FROM `recipes` WHERE `id` = OLD.`recipeId`)
        BEGIN
            INSERT INTO `sync_outbox` (`collectionKey`, `entityId`, `operation`)
            VALUES ('recipes', OLD.`recipeId`, '${SyncOutboxOperation.UPSERT.name}');
        END
        """.trimIndent()
    )
}

internal val SYNC_OUTBOX_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        installSyncOutboxInfrastructure(db)
    }
}
