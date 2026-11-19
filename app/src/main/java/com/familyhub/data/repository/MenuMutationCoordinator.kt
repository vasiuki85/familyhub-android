package com.familyhub.data.repository

import androidx.room.withTransaction
import com.familyhub.data.datastore.DeletedCloudDocumentCollections
import com.familyhub.data.local.db.FamilyHubDatabase
import com.familyhub.data.local.db.dao.DailyAssignmentDao
import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.db.dao.MenuTemplateDao
import com.familyhub.data.local.db.dao.WeeklyMenuDao
import com.familyhub.data.local.entity.DailyAssignmentEntity
import com.familyhub.data.local.entity.toDomain
import com.familyhub.data.local.entity.toEntity
import com.familyhub.data.menu.DailyAssignmentPayload
import com.familyhub.data.menu.MenuMutationSnapshotGuard
import com.familyhub.data.menu.MenuReplacePlanner
import com.familyhub.data.sync.CloudDeletionLedgerPort
import com.familyhub.domain.model.DailyAssignment
import com.familyhub.domain.model.MenuTemplate
import com.familyhub.domain.model.WeeklyMenu
import com.familyhub.domain.utils.MenuMutationClock
import com.familyhub.domain.utils.MenuSyncIds
import com.familyhub.domain.utils.MenuTimestampOverflowException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes local menu replace operations and applies them in a single Room transaction.
 *
 * Import/restore bypass this coordinator and write directly through DAO clear/upsert paths.
 */
@Singleton
class MenuMutationCoordinator @Inject constructor(
    private val database: FamilyHubDatabase,
    private val weeklyMenuDao: WeeklyMenuDao,
    private val menuTemplateDao: MenuTemplateDao,
    private val dailyAssignmentDao: DailyAssignmentDao,
    private val familyMemberDao: FamilyMemberDao,
    private val cloudDeletionLedger: CloudDeletionLedgerPort,
    private val clock: MenuMutationClock,
) {
    private val mutex = Mutex()

    suspend fun replaceWeeklyMenu(actorUserId: String, desired: WeeklyMenu): WeeklyMenu = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        val current = weeklyMenuDao.getByStartDate(desired.startDateMillis)
        val plan = MenuReplacePlanner.planWeeklyReplace(current, desired)
        if (!plan.hasMutations(current)) {
            clearWeeklyMenuTombstones(current)
            return@withLock current?.toDomain() ?: desired.copy(id = plan.canonicalParentId)
        }

        val maxUpdatedAt = MenuReplacePlanner.maxMutationUpdatedAt(
            parentUpdatedAt = current?.menu?.updatedAt ?: 0L,
            cohortUpdatedAts = (plan.changedSources + plan.removedEntries).map { it.updatedAt },
        )
        val stamp = clock.nextUpdatedAt(maxUpdatedAt)
        val stampedParent = plan.parentEntity.copy(
            updatedAt = if (requiresWeeklyParentStamp(plan)) stamp else plan.parentEntity.updatedAt,
        )
        val stampedChanged = plan.changedEntries.map { it.copy(updatedAt = stamp) }

        val tombstoneBatch = buildList {
            plan.removedEntries.forEach { add(DeletedCloudDocumentCollections.MENU_ENTRIES to it.id) }
        }
        cloudDeletionLedger.recordDeletions(tombstoneBatch)

        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = weeklyMenuDao.getByStartDate(desired.startDateMillis)
            MenuMutationSnapshotGuard.requireWeeklyAggregateUnchanged(current, fresh)
            weeklyMenuDao.upsertMenu(stampedParent)
            if (stampedChanged.isNotEmpty()) {
                weeklyMenuDao.upsertEntries(stampedChanged)
            }
            if (plan.unchangedEntries.isNotEmpty()) {
                weeklyMenuDao.upsertEntries(plan.unchangedEntries)
            }
            if (plan.removedEntries.isNotEmpty()) {
                weeklyMenuDao.deleteEntriesByIds(plan.removedEntries.map { it.id })
            }
            plan.legacyParentId?.let { weeklyMenuDao.deleteMenu(it) }
        }

        val result = weeklyMenuDao.getByStartDate(desired.startDateMillis)!!
        clearWeeklyMenuTombstones(result)
        result.toDomain()
    }

    suspend fun deleteWeeklyMenu(actorUserId: String, menuId: String) = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        val current = weeklyMenuDao.getById(menuId) ?: return@withLock
        val tombstoneBatch = buildList {
            add(DeletedCloudDocumentCollections.WEEKLY_MENUS to current.menu.id)
            current.entries.forEach { add(DeletedCloudDocumentCollections.MENU_ENTRIES to it.id) }
        }
        cloudDeletionLedger.recordDeletions(tombstoneBatch)

        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = weeklyMenuDao.getById(menuId)
            MenuMutationSnapshotGuard.requireWeeklyAggregateUnchanged(current, fresh)
            weeklyMenuDao.deleteEntriesForMenu(menuId)
            weeklyMenuDao.deleteMenu(menuId)
        }
    }

    suspend fun replaceMenuTemplate(actorUserId: String, desired: MenuTemplate): MenuTemplate = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        val current = menuTemplateDao.getById(desired.id)
        val plan = MenuReplacePlanner.planTemplateReplace(current, desired)
        if (!plan.hasMutations(current)) {
            clearMenuTemplateTombstones(current)
            return@withLock current?.toDomain() ?: desired
        }

        val maxUpdatedAt = MenuReplacePlanner.maxMutationUpdatedAt(
            parentUpdatedAt = current?.template?.updatedAt ?: 0L,
            cohortUpdatedAts = (plan.changedSources + plan.removedEntries).map { it.updatedAt },
        )
        val stamp = clock.nextUpdatedAt(maxUpdatedAt)
        val stampedParent = plan.templateEntity.copy(
            updatedAt = if (requiresTemplateParentStamp(plan)) stamp else plan.templateEntity.updatedAt,
        )
        val stampedChanged = plan.changedEntries.map { it.copy(updatedAt = stamp) }

        cloudDeletionLedger.recordDeletions(
            plan.removedEntries.map { DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES to it.id }
        )

        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = menuTemplateDao.getById(desired.id)
            MenuMutationSnapshotGuard.requireTemplateAggregateUnchanged(current, fresh)
            menuTemplateDao.insertTemplate(stampedParent)
            if (stampedChanged.isNotEmpty()) {
                menuTemplateDao.insertEntries(stampedChanged)
            }
            if (plan.unchangedEntries.isNotEmpty()) {
                menuTemplateDao.insertEntries(plan.unchangedEntries)
            }
            if (plan.removedEntries.isNotEmpty()) {
                menuTemplateDao.deleteEntriesByIds(plan.removedEntries.map { it.id })
            }
        }

        val result = menuTemplateDao.getById(desired.id)!!
        clearMenuTemplateTombstones(result)
        result.toDomain()
    }

    suspend fun deleteMenuTemplate(actorUserId: String, templateId: String) = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        val current = menuTemplateDao.getById(templateId) ?: return@withLock
        val tombstoneBatch = buildList {
            add(DeletedCloudDocumentCollections.MENU_TEMPLATES to current.template.id)
            current.entries.forEach { add(DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES to it.id) }
        }
        cloudDeletionLedger.recordDeletions(tombstoneBatch)

        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = menuTemplateDao.getById(templateId)
            MenuMutationSnapshotGuard.requireTemplateAggregateUnchanged(current, fresh)
            menuTemplateDao.deleteEntries(templateId)
            menuTemplateDao.deleteTemplate(templateId)
        }
    }

    suspend fun upsertDailyAssignment(
        actorUserId: String,
        desired: DailyAssignment,
    ): DailyAssignment = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        MenuSyncIds.requireDayOfWeek(desired.dayOfWeek)
        val canonical = canonicalDailyAssignment(desired)
        val weekStartMillis = canonical.weekStartMillis
        val current = dailyAssignmentDao.getByWeek(weekStartMillis)
        val existing = current.find { it.dayOfWeek == canonical.dayOfWeek }

        if (existing != null && DailyAssignmentPayload.samePersisted(existing, canonical)) {
            clearDailyAssignmentTombstones(listOf(existing))
            return@withLock existing.toDomain()
        }

        rejectPersistedTimestampOverflow(existing)

        val cohortMaxUpdatedAt = current.maxOfOrNull { it.updatedAt } ?: 0L
        val stamp = clock.nextUpdatedAt(cohortMaxUpdatedAt)
        val entityToWrite = canonical.toEntity().copy(updatedAt = stamp)

        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = dailyAssignmentDao.getByWeek(weekStartMillis)
            MenuMutationSnapshotGuard.requireDailyAssignmentsUnchanged(current, fresh)
            dailyAssignmentDao.upsertAssignment(entityToWrite)
        }

        val result = dailyAssignmentDao.getByWeek(weekStartMillis)
            .first { it.id == entityToWrite.id }
        clearDailyAssignmentTombstones(listOf(result))
        result.toDomain()
    }

    suspend fun deleteDailyAssignmentsForWeek(actorUserId: String, weekStartMillis: Long) = mutex.withLock {
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
        val current = dailyAssignmentDao.getByWeek(weekStartMillis)
        if (current.isEmpty()) return@withLock
        cloudDeletionLedger.recordDeletions(
            current.map { DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS to it.id }
        )
        database.withTransaction {
            MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorUserId)
            val fresh = dailyAssignmentDao.getByWeek(weekStartMillis)
            MenuMutationSnapshotGuard.requireDailyAssignmentsUnchanged(current, fresh)
            dailyAssignmentDao.deleteByWeek(weekStartMillis)
        }
    }

    private fun canonicalDailyAssignment(assignment: DailyAssignment): DailyAssignment {
        val id = MenuSyncIds.dailyAssignmentId(assignment.weekStartMillis, assignment.dayOfWeek)
        return assignment.copy(id = id)
    }

    private fun rejectPersistedTimestampOverflow(entity: DailyAssignmentEntity?) {
        if (entity?.updatedAt == Long.MAX_VALUE) {
            throw MenuTimestampOverflowException()
        }
    }

    private suspend fun clearWeeklyMenuTombstones(aggregate: com.familyhub.data.local.entity.WeeklyMenuWithEntries?) {
        if (aggregate == null) return
        val entries = buildList {
            add(DeletedCloudDocumentCollections.WEEKLY_MENUS to aggregate.menu.id)
            aggregate.entries.forEach { add(DeletedCloudDocumentCollections.MENU_ENTRIES to it.id) }
        }
        cloudDeletionLedger.clearDeletions(entries)
    }

    private suspend fun clearMenuTemplateTombstones(aggregate: com.familyhub.data.local.entity.MenuTemplateWithEntries?) {
        if (aggregate == null) return
        val entries = buildList {
            add(DeletedCloudDocumentCollections.MENU_TEMPLATES to aggregate.template.id)
            aggregate.entries.forEach { add(DeletedCloudDocumentCollections.MENU_TEMPLATE_ENTRIES to it.id) }
        }
        cloudDeletionLedger.clearDeletions(entries)
    }

    private suspend fun clearDailyAssignmentTombstones(assignments: List<DailyAssignmentEntity>) {
        if (assignments.isEmpty()) return
        cloudDeletionLedger.clearDeletions(
            assignments.map { DeletedCloudDocumentCollections.DAILY_ASSIGNMENTS to it.id }
        )
    }

    private fun requiresWeeklyParentStamp(plan: com.familyhub.data.menu.WeeklyMenuReplacePlan): Boolean =
        plan.parentPayloadChanged ||
            plan.changedEntries.isNotEmpty() ||
            plan.removedEntries.isNotEmpty() ||
            plan.legacyParentId != null

    private fun requiresTemplateParentStamp(plan: com.familyhub.data.menu.MenuTemplateReplacePlan): Boolean =
        plan.parentPayloadChanged ||
            plan.changedEntries.isNotEmpty() ||
            plan.removedEntries.isNotEmpty()
}
