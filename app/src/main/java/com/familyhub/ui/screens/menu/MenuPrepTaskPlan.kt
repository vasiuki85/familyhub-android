package com.familyhub.ui.screens.menu

import com.familyhub.domain.model.WeeklyMenu

/**
 * Pure plan for menu prep-task side effects.
 *
 * Task deletes are deferred until after [WeeklyMenuRepository.upsertMenu] succeeds.
 * Prep-task creates/updates still run before menu commit — a known cross-repository limitation.
 */
internal data class MenuPrepTaskPlan(
    val menu: WeeklyMenu,
    val taskIdsToDeleteAfterCommit: Set<String>,
)
