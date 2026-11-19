package com.familyhub.ui.screens.tasks

object TaskFabVisibilityPolicy {
    fun shouldShow(
        fabAction: TaskFabAction,
        primaryAction: TaskContentAction?,
    ): Boolean = fabAction == TaskFabAction.CREATE_TASK &&
        primaryAction != TaskContentAction.CREATE_TASK
}
