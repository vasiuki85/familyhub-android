package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.TaskHistoryDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.domain.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskHistoryRepository @Inject constructor(
    private val dao: TaskHistoryDao
) {
    fun getHistoryForTask(taskId: String): Flow<List<TaskHistory>> {
        return dao.getHistoryForTask(taskId).map { it.map { entity -> entity.toDomain() } }
    }
    
    fun getRecentHistory(limit: Int = 50): Flow<List<TaskHistory>> {
        return dao.getRecentHistory(limit).map { it.map { entity -> entity.toDomain() } }
    }
    
    suspend fun insertHistory(history: TaskHistory) {
        dao.insert(history.toEntity())
    }
    
    suspend fun deleteHistoryForTask(taskId: String) {
        dao.deleteHistoryForTask(taskId)
    }
}

