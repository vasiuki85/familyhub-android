package com.familyhub.domain.model

data class ShoppingActivity(
    val id: String,
    val listId: String,
    val itemId: String?,
    val actorId: String,
    val actionType: ShoppingActivityType,
    val payload: Map<String, String>,
    val occurredAt: Long
)

enum class ShoppingActivityType(val id: String) {
    ITEM_ADDED("item_added"),
    ITEM_COMPLETED("item_completed"),
    ITEM_REOPENED("item_reopened"),
    ITEM_DELETED("item_deleted"),
    LIST_CREATED("list_created"),
    LIST_ARCHIVED("list_archived"),
    LIST_RESTORED("list_restored"),
    LIST_DUPLICATED("list_duplicated"),
    LIST_TEMPLATE_APPLIED("list_template_applied"),
    MEMBER_ADDED("member_added"),
    MEMBER_REMOVED("member_removed");

    companion object {
        fun fromId(id: String): ShoppingActivityType = values().firstOrNull { it.id == id }
            ?: ITEM_ADDED
    }
}
