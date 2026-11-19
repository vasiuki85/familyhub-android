package com.familyhub.domain.model

data class ShoppingItem(
    val id: String,               // UUID
    val listId: String,           // какому списку принадлежит
    val title: String,            // "Молоко 2.5%"
    val createdByUserId: String,  // кто добавил
    val isBought: Boolean,        // куплено / не куплено
    val createdAt: Long,          // millis UTC
    val categoryId: String? = null,  // категория
    val quantity: Double? = null,    // количество, может быть null
    val quantityUnit: ShoppingItemUnit? = null, // единица измерения
    val note: String? = null,         // дополнительное примечание
    val boughtAt: Long? = null        // когда отметили купленным
)

enum class ShoppingItemUnit(val id: String) {
    PIECES("pcs"),
    PACK("pack"),
    KILOGRAM("kg"),
    GRAM("g"),
    LITER("l"),
    MILLILITER("ml"),
    BOTTLE("bottle"),
    BOX("box"),
    NONE("none");

    companion object {
        fun fromId(id: String?): ShoppingItemUnit? = values().firstOrNull { it.id == id }
    }
}

