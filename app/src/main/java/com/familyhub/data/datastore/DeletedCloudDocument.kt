package com.familyhub.data.datastore

data class DeletedCloudDocument(
    val familyId: String,
    val collection: String,
    val documentId: String
)

object DeletedCloudDocumentCollections {
    const val TASKS = "tasks"
    const val TASK_ROTATIONS = "task_rotations"
    const val SHOPPING_LISTS = "shopping_lists"
    const val SHOPPING_CATEGORIES = "shopping_categories"
    const val SHOPPING_ITEMS = "shopping_items"
    const val EVENTS = "events"
    const val REWARDS = "rewards"
    const val HOUSE_RULES = "house_rules"
    const val TASK_TEMPLATES = "task_templates"
    const val TASK_CATEGORIES = "task_categories"
    const val APPRECIATIONS = "appreciations"
    const val FAMILY_GOALS = "family_goals"
    const val FAMILY_GOAL_STAGES = "family_goal_stages"
    const val FAMILY_GOAL_STEPS = "family_goal_steps"
    const val EMERGENCY_CONTACTS = "emergency_contacts"

    const val WEEKLY_MENUS = "weekly_menus"
    const val MENU_ENTRIES = "menu_entries"
    const val MENU_TEMPLATES = "menu_templates"
    const val MENU_TEMPLATE_ENTRIES = "menu_template_entries"
    const val DAILY_ASSIGNMENTS = "daily_assignments"

    /** Menu collections participating in cloud sync (tombstones, merge, upload). */
    val MENU_COLLECTIONS: Set<String> = setOf(
        WEEKLY_MENUS,
        MENU_ENTRIES,
        MENU_TEMPLATES,
        MENU_TEMPLATE_ENTRIES,
        DAILY_ASSIGNMENTS,
    )

    /**
     * Non-menu collections whose tombstones may be confirmed via the generic pre-merge
     * initial-remote check. Menu tombstones use post-upload SERVER verification instead.
     */
    val PRE_MERGE_CONFIRMABLE_COLLECTIONS: Set<String> = setOf(
        TASKS,
        TASK_ROTATIONS,
        SHOPPING_LISTS,
        SHOPPING_CATEGORIES,
        SHOPPING_ITEMS,
        EVENTS,
        REWARDS,
        HOUSE_RULES,
        TASK_TEMPLATES,
        TASK_CATEGORIES,
        APPRECIATIONS,
        FAMILY_GOALS,
        FAMILY_GOAL_STAGES,
        FAMILY_GOAL_STEPS,
        EMERGENCY_CONTACTS,
    )

    /** Collections whose tombstones participate in remote confirmation, merge, and upload. */
    val REMOTE_ACTIVE_COLLECTIONS: Set<String> = PRE_MERGE_CONFIRMABLE_COLLECTIONS + MENU_COLLECTIONS

    /** @deprecated Menu collections are remote-active since FH2-20B2B3B. */
    @Deprecated("Use MENU_COLLECTIONS", ReplaceWith("MENU_COLLECTIONS"))
    val MENU_STORAGE_ONLY_COLLECTIONS: Set<String> = MENU_COLLECTIONS

    /** All collections allowed in DataStore tombstone encoding. */
    val STORAGE_ALLOWLIST: Set<String> = REMOTE_ACTIVE_COLLECTIONS

    fun isRemoteActive(collection: String): Boolean = collection in REMOTE_ACTIVE_COLLECTIONS

    fun isPreMergeConfirmable(collection: String): Boolean = collection in PRE_MERGE_CONFIRMABLE_COLLECTIONS

    fun isMenuCollection(collection: String): Boolean = collection in MENU_COLLECTIONS

    fun isStorageAllowed(collection: String): Boolean = collection in STORAGE_ALLOWLIST

    fun requireStorageAllowed(collection: String) {
        require(collection in STORAGE_ALLOWLIST) { "Unknown deleted document collection: $collection" }
    }

    fun requireAllowed(collection: String) = requireStorageAllowed(collection)
}
