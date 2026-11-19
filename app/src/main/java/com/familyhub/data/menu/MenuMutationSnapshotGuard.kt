package com.familyhub.data.menu

import com.familyhub.data.local.entity.MenuTemplateEntryEntity
import com.familyhub.data.local.entity.MenuTemplateWithEntries
import com.familyhub.data.local.entity.WeeklyMenuWithEntries
import com.familyhub.domain.utils.MenuMutationStaleException

/**
 * Transaction-time aggregate equality for menu replace operations.
 *
 * Compares the full parent entity and the full child set keyed by id so inserts,
 * deletes, payload drift with the same [updatedAt], and timestamp drift are all detected.
 */
object MenuMutationSnapshotGuard {

    fun requireWeeklyAggregateUnchanged(
        expected: WeeklyMenuWithEntries?,
        fresh: WeeklyMenuWithEntries?,
    ) {
        if (expected == null && fresh == null) return
        if (expected == null || fresh == null) {
            throw MenuMutationStaleException()
        }
        if (expected.menu != fresh.menu) {
            throw MenuMutationStaleException()
        }
        if (childrenById(expected) != childrenById(fresh)) {
            throw MenuMutationStaleException()
        }
    }

    fun requireTemplateAggregateUnchanged(
        expected: MenuTemplateWithEntries?,
        fresh: MenuTemplateWithEntries?,
    ) {
        if (expected == null && fresh == null) return
        if (expected == null || fresh == null) {
            throw MenuMutationStaleException()
        }
        if (expected.template != fresh.template) {
            throw MenuMutationStaleException()
        }
        if (templateChildrenById(expected) != templateChildrenById(fresh)) {
            throw MenuMutationStaleException()
        }
    }

    private fun childrenById(aggregate: WeeklyMenuWithEntries): Map<String, com.familyhub.data.local.entity.MenuEntryEntity> =
        aggregate.entries.associateBy { it.id }

    private fun templateChildrenById(
        aggregate: MenuTemplateWithEntries,
    ): Map<String, MenuTemplateEntryEntity> = aggregate.entries.associateBy { it.id }

    fun requireDailyAssignmentsUnchanged(
        expected: List<com.familyhub.data.local.entity.DailyAssignmentEntity>,
        fresh: List<com.familyhub.data.local.entity.DailyAssignmentEntity>,
    ) {
        if (expected.associateBy { it.id } != fresh.associateBy { it.id }) {
            throw MenuMutationStaleException()
        }
    }
}
