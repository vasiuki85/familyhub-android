package com.familyhub.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class EditProfileContentPolicyTest {

    @Test
    fun missingRouteMemberId_isUnavailableWithoutShowingEditor() {
        assertEquals(
            EditProfileContentMode.UNAVAILABLE,
            EditProfileContentPolicy.resolve(
                memberId = null,
                loadStatus = EditProfileLoadStatus.LOADING,
                hasMember = false,
            )
        )
        assertEquals(
            EditProfileContentMode.UNAVAILABLE,
            EditProfileContentPolicy.resolve(
                memberId = " ",
                loadStatus = EditProfileLoadStatus.READY,
                hasMember = true,
            )
        )
    }

    @Test
    fun loadingMember_showsLoadingState() {
        assertEquals(
            EditProfileContentMode.LOADING,
            EditProfileContentPolicy.resolve(
                memberId = "member-1",
                loadStatus = EditProfileLoadStatus.LOADING,
                hasMember = false,
            )
        )
    }

    @Test
    fun readyMember_showsEditor() {
        assertEquals(
            EditProfileContentMode.EDITOR,
            EditProfileContentPolicy.resolve(
                memberId = "member-1",
                loadStatus = EditProfileLoadStatus.READY,
                hasMember = true,
            )
        )
    }

    @Test
    fun inconsistentReadyWithoutMember_failsClosedToUnavailable() {
        assertEquals(
            EditProfileContentMode.UNAVAILABLE,
            EditProfileContentPolicy.resolve(
                memberId = "member-1",
                loadStatus = EditProfileLoadStatus.READY,
                hasMember = false,
            )
        )
    }

    @Test
    fun repositoryMissingMember_showsUnavailableState() {
        assertEquals(
            EditProfileContentMode.UNAVAILABLE,
            EditProfileContentPolicy.resolve(
                memberId = "member-1",
                loadStatus = EditProfileLoadStatus.UNAVAILABLE,
                hasMember = false,
            )
        )
    }

    @Test
    fun loadFailure_showsRetryableState() {
        assertEquals(
            EditProfileContentMode.LOAD_FAILED,
            EditProfileContentPolicy.resolve(
                memberId = "member-1",
                loadStatus = EditProfileLoadStatus.FAILED,
                hasMember = false,
            )
        )
    }
}
