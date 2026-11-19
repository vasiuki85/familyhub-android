package com.familyhub.domain.utils

import com.familyhub.domain.model.MealType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Fixed-length deterministic document IDs for menu slot entities.
 *
 * Format: `{prefix}{32 hex}` = 34 ASCII chars.
 * Payload: entityType(1) + schemaVersion(1) + length-prefixed / fixed-width components.
 * Hash: [UUID.nameUUIDFromBytes] over the binary payload (UTF-8 explicit).
 *
 * No delimiter-based string payloads — boundaries are length-prefixed only.
 */
object MenuSyncIds {
    const val SCHEMA_VERSION: Byte = 0x01
    const val CANONICAL_ID_LENGTH = 34
    const val MAX_FIRESTORE_ID_UTF8_BYTES = 1500

    private const val PREFIX_WEEKLY_MENU = "wm"
    private const val PREFIX_MENU_ENTRY = "me"
    private const val PREFIX_TEMPLATE_ENTRY = "te"
    private const val PREFIX_DAILY_ASSIGNMENT = "da"

    private const val ENTITY_WEEKLY_MENU: Byte = 0x01
    private const val ENTITY_MENU_ENTRY: Byte = 0x02
    private const val ENTITY_TEMPLATE_ENTRY: Byte = 0x03
    private const val ENTITY_DAILY_ASSIGNMENT: Byte = 0x04

    fun weeklyMenuId(startDateMillis: Long): String =
        canonical(PREFIX_WEEKLY_MENU, ENTITY_WEEKLY_MENU) {
            writeInt64Be(startDateMillis)
        }

    fun menuEntryId(startDateMillis: Long, dayOfWeek: Int, mealType: String): String {
        requireDayOfWeek(dayOfWeek)
        val meal = requireMealType(mealType)
        return canonical(PREFIX_MENU_ENTRY, ENTITY_MENU_ENTRY) {
            writeInt64Be(startDateMillis)
            writeUint8(dayOfWeek)
            writeUtf8Lp(meal)
        }
    }

    fun menuTemplateEntryId(templateId: String, dayOfWeek: Int, mealType: String): String {
        requireValidFirestoreDocumentId(templateId)
        requireDayOfWeek(dayOfWeek)
        val meal = requireMealType(mealType)
        return canonical(PREFIX_TEMPLATE_ENTRY, ENTITY_TEMPLATE_ENTRY) {
            writeUtf8Lp(templateId)
            writeUint8(dayOfWeek)
            writeUtf8Lp(meal)
        }
    }

    fun dailyAssignmentId(weekStartMillis: Long, dayOfWeek: Int): String {
        requireDayOfWeek(dayOfWeek)
        return canonical(PREFIX_DAILY_ASSIGNMENT, ENTITY_DAILY_ASSIGNMENT) {
            writeInt64Be(weekStartMillis)
            writeUint8(dayOfWeek)
        }
    }

    fun requireDayOfWeek(dayOfWeek: Int) {
        require(dayOfWeek in 0..6) {
            "dayOfWeek must be in 0..6, got $dayOfWeek"
        }
    }

    fun requireMealType(mealType: String): String {
        require(mealType.isNotBlank()) { "mealType must not be blank" }
        return try {
            MealType.valueOf(mealType).name
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid mealType: $mealType")
        }
    }

    fun requireValidFirestoreDocumentId(id: String) {
        require(id.isNotEmpty()) { "document id must not be empty" }
        require(id != "." && id != "..") { "document id must not be '.' or '..'" }
        require(!id.contains('/')) { "document id must not contain '/'" }
        require(!(id.length >= 4 && id.startsWith("__") && id.endsWith("__"))) {
            "document id must not be a Firestore reserved id"
        }
        require(hasValidUtf16Surrogates(id)) {
            "document id contains invalid UTF-16 surrogates"
        }
        val utf8Bytes = id.toByteArray(StandardCharsets.UTF_8).size
        require(utf8Bytes <= MAX_FIRESTORE_ID_UTF8_BYTES) {
            "document id UTF-8 length $utf8Bytes exceeds $MAX_FIRESTORE_ID_UTF8_BYTES"
        }
    }

    private inline fun canonical(
        prefix: String,
        entityType: Byte,
        body: PayloadBuilder.() -> Unit
    ): String {
        val payload = PayloadBuilder().apply {
            writeByte(entityType)
            writeByte(SCHEMA_VERSION)
            body()
        }.toByteArray()
        val uuid = UUID.nameUUIDFromBytes(payload)
        val hex = uuid.toString().replace("-", "")
        val id = prefix + hex
        check(id.length == CANONICAL_ID_LENGTH) {
            "canonical id length ${id.length} != $CANONICAL_ID_LENGTH"
        }
        return id
    }

    private fun hasValidUtf16Surrogates(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                Character.isHighSurrogate(ch) -> {
                    if (i + 1 >= value.length || !Character.isLowSurrogate(value[i + 1])) {
                        return false
                    }
                    i += 2
                }
                Character.isLowSurrogate(ch) -> return false
                else -> i++
            }
        }
        return true
    }

    class PayloadBuilder {
        private val out = ByteArrayOutputStream()

        fun writeByte(value: Byte) {
            out.write(value.toInt() and 0xFF)
        }

        fun writeUint8(value: Int) {
            require(value in 0..255) { "UINT8 out of range: $value" }
            out.write(value)
        }

        fun writeInt64Be(value: Long) {
            for (shift in 56 downTo 0 step 8) {
                out.write(((value ushr shift) and 0xFFL).toInt())
            }
        }

        fun writeInt32Be(value: Int) {
            require(value >= 0) { "UTF-8 length must be non-negative" }
            for (shift in 24 downTo 0 step 8) {
                out.write((value ushr shift) and 0xFF)
            }
        }

        fun writeUtf8Lp(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeInt32Be(bytes.size)
            out.write(bytes)
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
