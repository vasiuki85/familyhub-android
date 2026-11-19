package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.entity.toDomain
import com.familyhub.domain.model.DailyAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DailyAssignmentRepository @Inject constructor(
    private val dailyAssignmentDao: DailyAssignmentDao,
    private val menuMutationCoordinator: MenuMutationCoordinator,
) {
    fun observeAssignments(weekStartMillis: Long): Flow<List<DailyAssignment>> {
        return dailyAssignmentDao.observeByWeek(weekStartMillis).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun upsertAssignment(actorUserId: String, assignment: DailyAssignment) {
        menuMutationCoordinator.upsertDailyAssignment(actorUserId, assignment)
    }

    suspend fun deleteAssignmentsForWeek(actorUserId: String, weekStartMillis: Long) {
        menuMutationCoordinator.deleteDailyAssignmentsForWeek(actorUserId, weekStartMillis)
    }
}
