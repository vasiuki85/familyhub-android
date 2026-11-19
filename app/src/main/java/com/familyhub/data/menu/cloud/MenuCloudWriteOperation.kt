package com.familyhub.data.menu.cloud

sealed interface MenuCloudWriteOperation {
    val collection: String
    val documentId: String

    data class Delete(
        override val collection: String,
        override val documentId: String,
    ) : MenuCloudWriteOperation

    data class Set(
        override val collection: String,
        override val documentId: String,
        val fields: Map<String, Any?>,
    ) : MenuCloudWriteOperation
}
