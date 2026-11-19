package com.familyhub.domain.usecase

interface TaskHistoryTextProvider {
    fun taskCreated(title: String): String
    fun taskUpdated(changes: List<String>): String
    fun statusChanged(oldStatus: String, newStatus: String): String
    fun taskDeleted(title: String): String
    fun taskReassigned(): String
    fun privacyChanged(isPrivate: Boolean): String
    fun rotationChanged(previousName: String?, newName: String): String
}
