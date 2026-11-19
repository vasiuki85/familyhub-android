package com.familyhub.data.datastore

private const val FIELD_SEPARATOR = '\u001f'

private fun requireValidFamilyScopedField(name: String, value: String) {
    require(value.isNotBlank()) { "$name must not be blank" }
    require(FIELD_SEPARATOR !in value) { "$name must not contain field separator" }
}

fun encodeFamilyScopedDeletedId(id: FamilyScopedDeletedId): String {
    requireValidFamilyScopedField("familyId", id.familyId)
    requireValidFamilyScopedField("documentId", id.documentId)
    return listOf(id.familyId, id.documentId).joinToString(FIELD_SEPARATOR.toString())
}

fun decodeFamilyScopedDeletedId(entry: String): FamilyScopedDeletedId? {
    if (entry.isBlank()) return null
    val parts = entry.split(FIELD_SEPARATOR)
    if (parts.size != 2) return null
    val familyId = parts[0]
    val documentId = parts[1]
    if (familyId.isBlank() || documentId.isBlank()) return null
    return FamilyScopedDeletedId(familyId = familyId, documentId = documentId)
}

fun isLegacyRawFamilyScopedEntry(entry: String): Boolean {
    return entry.isNotBlank() && FIELD_SEPARATOR !in entry
}

fun isMalformedFamilyScopedEntry(entry: String): Boolean {
    return !isLegacyRawFamilyScopedEntry(entry) && decodeFamilyScopedDeletedId(entry) == null
}

/**
 * Idempotent normalization of member/recipe tombstone entries.
 *
 * - Valid scoped entries are preserved.
 * - Legacy raw IDs bind only to [storedAccountId] when present; otherwise dropped.
 * - Malformed scoped entries are preserved but never returned by [readDeletedDocumentIdsForFamily].
 */
fun normalizeFamilyScopedDeletedEntries(
    entries: Set<String>,
    storedAccountId: String?
): Set<String> {
    return entries.mapNotNull { entry ->
        when {
            decodeFamilyScopedDeletedId(entry) != null -> entry
            isLegacyRawFamilyScopedEntry(entry) -> {
                if (storedAccountId.isNullOrBlank()) {
                    null
                } else {
                    encodeFamilyScopedDeletedId(
                        FamilyScopedDeletedId(
                            familyId = storedAccountId,
                            documentId = entry
                        )
                    )
                }
            }
            isMalformedFamilyScopedEntry(entry) -> entry
            else -> null
        }
    }.toSet()
}

/** @see normalizeFamilyScopedDeletedEntries */
fun migrateFamilyScopedDeletedEntries(
    entries: Set<String>,
    previousAccountId: String?
): Set<String> = normalizeFamilyScopedDeletedEntries(entries, previousAccountId)

fun familyScopedDeletedEntriesChanged(before: Set<String>, after: Set<String>): Boolean = before != after

fun readNormalizedDeletedDocumentIdsForFamily(
    entries: Set<String>,
    storedAccountId: String?,
    familyId: String
): Pair<Set<String>, Set<String>> {
    val normalized = normalizeFamilyScopedDeletedEntries(entries, storedAccountId)
    val documentIds = readDeletedDocumentIdsForFamily(normalized, familyId)
    return normalized to documentIds
}

fun addDeletedDocumentIdForFamily(
    entries: Set<String>,
    storedAccountId: String?,
    familyId: String,
    documentId: String
): Set<String> {
    val normalized = normalizeFamilyScopedDeletedEntries(entries, storedAccountId)
    return normalized + encodeFamilyScopedDeletedEntry(familyId, documentId)
}

fun removeDeletedDocumentIdForFamily(
    entries: Set<String>,
    storedAccountId: String?,
    familyId: String,
    documentId: String
): Set<String> {
    val normalized = normalizeFamilyScopedDeletedEntries(entries, storedAccountId)
    return normalized.filterNot { entry ->
        val decoded = decodeFamilyScopedDeletedId(entry)
        decoded?.familyId == familyId && decoded.documentId == documentId
    }.toSet()
}

fun clearDeletedDocumentIdsForFamily(
    entries: Set<String>,
    storedAccountId: String?,
    familyId: String
): Set<String> {
    val normalized = normalizeFamilyScopedDeletedEntries(entries, storedAccountId)
    return retainFamilyScopedEntriesAfterClearingFamily(normalized, familyId)
}

fun readDeletedDocumentIdsForFamily(entries: Set<String>, familyId: String): Set<String> {
    return entries.mapNotNull { entry ->
        val decoded = decodeFamilyScopedDeletedId(entry)
        if (decoded?.familyId == familyId) decoded.documentId else null
    }.toSet()
}

fun retainFamilyScopedEntriesAfterClearingFamily(entries: Set<String>, familyId: String): Set<String> {
    return entries.filter { entry ->
        val decoded = decodeFamilyScopedDeletedId(entry)
        decoded == null || decoded.familyId != familyId
    }.toSet()
}

fun encodeFamilyScopedDeletedEntry(familyId: String, documentId: String): String {
    return encodeFamilyScopedDeletedId(FamilyScopedDeletedId(familyId, documentId))
}
