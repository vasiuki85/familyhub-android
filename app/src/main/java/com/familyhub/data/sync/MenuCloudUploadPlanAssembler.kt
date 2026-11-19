package com.familyhub.data.sync

import com.familyhub.data.menu.cloud.MenuCloudWriteOperation

internal fun menuWriteOperationsToPlanned(
    operations: List<MenuCloudWriteOperation>,
): List<PlannedUploadOperation> {
    return operations.map { operation ->
        when (operation) {
            is MenuCloudWriteOperation.Delete ->
                PlannedUploadOperation.Delete(operation.collection, operation.documentId)
            is MenuCloudWriteOperation.Set ->
                PlannedUploadOperation.Set(
                    collection = operation.collection,
                    documentId = operation.documentId,
                    data = operation.fields,
                )
        }
    }
}

internal fun mergeUploadPlans(
    nonMenuOperations: List<PlannedUploadOperation>,
    menuOperations: List<PlannedUploadOperation>,
): List<PlannedUploadOperation> {
    val combined = nonMenuOperations + menuOperations
    assertNoCombinedUploadConflicts(combined)
    return combined
}

internal fun assertNoCombinedUploadConflicts(operations: List<PlannedUploadOperation>) {
    val seen = mutableSetOf<Pair<String, String>>()
    operations.forEach { operation ->
        val key = plannedUploadOperationKey(operation)
        check(key !in seen) {
            "Duplicate upload operation for ${key.first}/${key.second}"
        }
        seen += key
    }
}

internal fun plannedUploadOperationKey(operation: PlannedUploadOperation): Pair<String, String> {
    return when (operation) {
        is PlannedUploadOperation.Delete -> operation.collection to operation.documentId
        is PlannedUploadOperation.Set -> operation.collection to operation.documentId
    }
}
