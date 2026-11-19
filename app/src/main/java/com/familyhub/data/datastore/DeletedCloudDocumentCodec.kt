package com.familyhub.data.datastore

private const val FIELD_SEPARATOR = '\u001f'

private fun requireValidDeletedDocumentField(name: String, value: String) {
    require(value.isNotBlank()) { "$name must not be blank" }
    require(FIELD_SEPARATOR !in value) { "$name must not contain field separator" }
}

fun encodeDeletedCloudDocument(document: DeletedCloudDocument): String {
    DeletedCloudDocumentCollections.requireAllowed(document.collection)
    requireValidDeletedDocumentField("familyId", document.familyId)
    requireValidDeletedDocumentField("documentId", document.documentId)
    return listOf(document.familyId, document.collection, document.documentId).joinToString(FIELD_SEPARATOR.toString())
}

fun decodeDeletedCloudDocument(entry: String): DeletedCloudDocument? {
    if (entry.isBlank()) return null
    val parts = entry.split(FIELD_SEPARATOR)
    if (parts.size != 3) return null
    val familyId = parts[0]
    val collection = parts[1]
    val documentId = parts[2]
    if (familyId.isBlank() || documentId.isBlank()) return null
    if (collection !in DeletedCloudDocumentCollections.STORAGE_ALLOWLIST) return null
    return DeletedCloudDocument(
        familyId = familyId,
        collection = collection,
        documentId = documentId
    )
}

fun decodeDeletedCloudDocuments(entries: Set<String>): Set<DeletedCloudDocument> {
    return entries.mapNotNull(::decodeDeletedCloudDocument).toSet()
}

fun encodeDeletedCloudDocuments(documents: Set<DeletedCloudDocument>): Set<String> {
    return documents.map(::encodeDeletedCloudDocument).toSet()
}

fun groupDeletedDocumentIdsByCollection(documents: Set<DeletedCloudDocument>): Map<String, Set<String>> {
    return documents.groupBy({ it.collection }, { it.documentId }).mapValues { (_, ids) -> ids.toSet() }
}

fun filterRemoteActiveDeletedDocuments(documents: Set<DeletedCloudDocument>): Set<DeletedCloudDocument> {
    return documents.filter { DeletedCloudDocumentCollections.isRemoteActive(it.collection) }.toSet()
}

fun filterPreMergeConfirmableDeletedDocuments(documents: Set<DeletedCloudDocument>): Set<DeletedCloudDocument> {
    return documents.filter { DeletedCloudDocumentCollections.isPreMergeConfirmable(it.collection) }.toSet()
}

fun filterMenuDeletedDocuments(documents: Set<DeletedCloudDocument>): Set<DeletedCloudDocument> {
    return documents.filter { DeletedCloudDocumentCollections.isMenuCollection(it.collection) }.toSet()
}

fun remoteActiveDeletedDocumentIdsByCollection(
    documents: Set<DeletedCloudDocument>
): Map<String, Set<String>> {
    return groupDeletedDocumentIdsByCollection(filterRemoteActiveDeletedDocuments(documents))
}

fun retainDeletedDocumentEntriesAfterClearingFamily(entries: Set<String>, familyId: String): Set<String> {
    return entries.filter { entry ->
        val document = decodeDeletedCloudDocument(entry)
        document == null || document.familyId != familyId
    }.toSet()
}
