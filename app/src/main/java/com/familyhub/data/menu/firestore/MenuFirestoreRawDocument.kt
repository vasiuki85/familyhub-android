package com.familyhub.data.menu.firestore

data class MenuFirestoreRawDocument(
    val collection: String,
    val documentId: String,
    val fields: Map<String, Any?>,
)
