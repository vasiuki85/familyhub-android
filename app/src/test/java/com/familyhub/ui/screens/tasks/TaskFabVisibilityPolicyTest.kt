package com.familyhub.ui.screens.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFabVisibilityPolicyTest {
    @Test
    fun createTaskEmptyState_hidesDuplicateFab() {
        assertFalse(
            TaskFabVisibilityPolicy.shouldShow(
                fabAction = TaskFabAction.CREATE_TASK,
                primaryAction = TaskContentAction.CREATE_TASK,
            )
        )
    }

    @Test
    fun resetFiltersEmptyState_keepsCreateFab() {
        assertTrue(
            TaskFabVisibilityPolicy.shouldShow(
                fabAction = TaskFabAction.CREATE_TASK,
                primaryAction = TaskContentAction.RESET_FILTERS,
            )
        )
    }

    @Test
    fun loadingState_hasNoFab() {
        assertFalse(
            TaskFabVisibilityPolicy.shouldShow(
                fabAction = TaskFabAction.NONE,
                primaryAction = null,
            )
        )
    }

    @Test
    fun familyEmptyIllustration_isLimitedToLoadedMyAndFamilyStates() {
        assertTrue(
            TaskContentUiModel(emptyKind = TaskContentEmptyKind.NO_MY_TASKS)
                .showsFamilyEmptyIllustration
        )
        assertTrue(
            TaskContentUiModel(emptyKind = TaskContentEmptyKind.NO_FAMILY_TASKS)
                .showsFamilyEmptyIllustration
        )
        assertFalse(
            TaskContentUiModel(emptyKind = TaskContentEmptyKind.NO_RESULTS)
                .showsFamilyEmptyIllustration
        )
        assertFalse(
            TaskContentUiModel(emptyKind = TaskContentEmptyKind.NO_AWAITING)
                .showsFamilyEmptyIllustration
        )
    }
}
