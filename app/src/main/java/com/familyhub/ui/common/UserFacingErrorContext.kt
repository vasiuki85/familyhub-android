package com.familyhub.ui.common

import androidx.annotation.StringRes
import com.familyhub.R

/**
 * Operation-specific fallback when [UserFacingErrorMapper] cannot classify a throwable.
 */
enum class UserFacingErrorContext(@StringRes val fallbackRes: Int) {
    General(R.string.error_operation_failed),
    LoadData(R.string.error_load_failed),
    TaskCreate(R.string.error_task_create_failed),
    TaskUpdate(R.string.error_task_update_failed),
    TaskDelete(R.string.error_task_delete_failed),
    TaskReorder(R.string.error_task_order_failed),
    ShoppingAdd(R.string.error_shopping_add_failed),
    ShoppingUpdate(R.string.error_shopping_update_failed),
    ShoppingDelete(R.string.error_shopping_delete_failed),
    ShoppingListCreate(R.string.error_shopping_list_create_failed),
    ShoppingListUpdate(R.string.error_shopping_list_update_failed),
    ShoppingListDelete(R.string.error_shopping_list_delete_failed),
    ShoppingCategoryCreate(R.string.error_shopping_category_create_failed),
    ShoppingCategoryUpdate(R.string.error_shopping_category_update_failed),
    ShoppingCategoryDelete(R.string.error_shopping_category_delete_failed),
    EventCreate(R.string.error_event_create_failed),
    EventUpdate(R.string.error_event_update_failed),
    EventDelete(R.string.error_event_delete_failed),
    UserSwitch(R.string.error_user_switch_failed),
    Export(R.string.error_export_failed),
    Backup(R.string.error_backup_failed),
    Sync(R.string.settings_sync_safe_error),
    Calendar(R.string.error_calendar_sync_failed),
    Menu(R.string.error_menu_operation_failed),
    MenuShare(R.string.menu_share_error),
    Recipe(R.string.error_recipe_operation_failed),
    Rewards(R.string.error_rewards_operation_failed),
    RulesLoad(R.string.error_rules_load_failed),
    RulesCreate(R.string.error_rules_create_failed),
    RulesUpdate(R.string.error_rules_update_failed),
    RulesDelete(R.string.error_rules_delete_failed),
    AppreciationsLoad(R.string.error_appreciations_load_failed),
    AppreciationsCreate(R.string.error_appreciations_create_failed),
    AppreciationsDelete(R.string.error_appreciations_delete_failed),
    Goals(R.string.error_goals_operation_failed),
    Contacts(R.string.error_contacts_operation_failed),
    Profile(R.string.error_profile_save_failed),
    Awesomeness(R.string.error_awesomeness_operation_failed),
}
