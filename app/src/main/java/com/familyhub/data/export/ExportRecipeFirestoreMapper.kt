package com.familyhub.data.export

internal fun Map<String, Any?>.toExportRecipe(documentId: String): ExportRecipe {
    @Suppress("UNCHECKED_CAST")
    fun Any?.asMapList(): List<Map<String, Any?>> = when (this) {
        is List<*> -> this.mapNotNull { it as? Map<String, Any?> }
        else -> emptyList()
    }

    fun Any?.asString(default: String = ""): String = this as? String ?: default
    fun Any?.asNullableString(): String? = this as? String
    fun Any?.asBoolean(default: Boolean = false): Boolean = this as? Boolean ?: default
    fun Any?.asLong(default: Long = 0L): Long = (this as? Number)?.toLong() ?: default
    fun Any?.asInt(default: Int = 0): Int = (this as? Number)?.toInt() ?: default

    @Suppress("UNCHECKED_CAST")
    fun Any?.asStringList(): List<String> = when (this) {
        is List<*> -> this.mapNotNull { it as? String }
        else -> emptyList()
    }

    return ExportRecipe(
        id = this["id"].asString(documentId),
        title = this["title"].asString(),
        description = this["description"].asNullableString(),
        servings = this["servings"].asInt(),
        prepTimeMinutes = this["prepTimeMinutes"].asInt(),
        cookTimeMinutes = this["cookTimeMinutes"].asInt(),
        difficulty = this["difficulty"].asString("MEDIUM"),
        category = this["category"].asString("MAIN_DISH"),
        isFavorite = this["isFavorite"].asBoolean(this["favorite"].asBoolean()),
        imageUrl = this["imageUrl"].asNullableString(),
        notes = this["notes"].asNullableString(),
        tags = this["tags"].asStringList(),
        createdByUserId = this["createdByUserId"].asString(),
        createdAt = this["createdAt"].asLong(),
        updatedAt = this["updatedAt"].asLong(),
        ingredients = this["ingredients"].asMapList().map { item ->
            ExportRecipeIngredient(
                id = item["id"].asString(),
                name = item["name"].asString(),
                quantity = item["quantity"].asString()
            )
        },
        steps = this["steps"].asMapList().map { item ->
            ExportRecipeStep(
                id = item["id"].asString(),
                description = item["description"].asString(),
                order = item["order"].asInt()
            )
        }
    )
}