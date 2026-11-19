package com.familyhub.ui.screens.tasks

import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTabPolicyTest {

    @Test
    fun childRequestedAwaiting_resolvesToMy() {
        assertEquals(
            TaskTab.MY,
            TaskTabPolicy.resolveEffectiveTab(MemberRole.CHILD, TaskTab.AWAITING),
        )
    }

    @Test
    fun pendingRequestedAwaiting_resolvesToMy() {
        assertEquals(
            TaskTab.MY,
            TaskTabPolicy.resolveEffectiveTab(MemberRole.PENDING, TaskTab.AWAITING),
        )
    }

    @Test
    fun parentRequestedAwaiting_keepsAwaiting() {
        assertEquals(
            TaskTab.AWAITING,
            TaskTabPolicy.resolveEffectiveTab(MemberRole.PARENT, TaskTab.AWAITING),
        )
    }

    @Test
    fun adminRequestedAwaiting_keepsAwaiting() {
        assertEquals(
            TaskTab.AWAITING,
            TaskTabPolicy.resolveEffectiveTab(MemberRole.ADMIN, TaskTab.AWAITING),
        )
    }

    @Test
    fun childRequestedAll_keepsAll() {
        assertEquals(
            TaskTab.ALL,
            TaskTabPolicy.resolveEffectiveTab(MemberRole.CHILD, TaskTab.ALL),
        )
    }

    @Test
    fun availableTabs_adminAndParentIncludeAwaiting() {
        assertEquals(
            listOf(TaskTab.MY, TaskTab.ALL, TaskTab.AWAITING),
            TaskTabPolicy.availableTabs(MemberRole.ADMIN),
        )
        assertEquals(
            listOf(TaskTab.MY, TaskTab.ALL, TaskTab.AWAITING),
            TaskTabPolicy.availableTabs(MemberRole.PARENT),
        )
    }

    @Test
    fun availableTabs_childAndPendingExcludeAwaiting() {
        assertEquals(
            listOf(TaskTab.MY, TaskTab.ALL),
            TaskTabPolicy.availableTabs(MemberRole.CHILD),
        )
        assertEquals(
            listOf(TaskTab.MY, TaskTab.ALL),
            TaskTabPolicy.availableTabs(MemberRole.PENDING),
        )
    }
}
