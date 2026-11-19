package com.familyhub.domain.utils

import com.familyhub.domain.model.Task

/**
 * Проверяет, видна ли задача текущему пользователю
 * Приватная задача видна только создателю и исполнителю
 */
fun Task.isVisibleToUser(userId: String): Boolean {
    return !isPrivate || createdByUserId == userId || assignedToUserId == userId
}

/**
 * Фильтрует задачи по видимости для текущего пользователя
 */
fun List<Task>.filterByVisibility(userId: String): List<Task> {
    return this.filter { it.isVisibleToUser(userId) }
}

