package com.familyhub.data.repository

import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedMemberBootstrapPolicyTest {

    @Test
    fun emptyUnboundSnapshot_assignsAdminRole() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-1",
            membersContainUid = false,
            memberCount = 0,
            accountId = null,
            localDataFamilyId = null,
            suppressAuthMemberBootstrap = false,
            restoreInProgress = false
        )
        assertTrue(action is AuthenticatedMemberBootstrapPolicy.Action.CreateMember)
        assertEquals(MemberRole.ADMIN, (action as AuthenticatedMemberBootstrapPolicy.Action.CreateMember).role)
    }

    @Test
    fun emptyCloudBoundWithAccountId_assignsPendingRole() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-1",
            membersContainUid = false,
            memberCount = 0,
            accountId = "family-1",
            localDataFamilyId = null,
            suppressAuthMemberBootstrap = false,
            restoreInProgress = false
        )
        assertEquals(
            MemberRole.PENDING,
            (action as AuthenticatedMemberBootstrapPolicy.Action.CreateMember).role
        )
    }

    @Test
    fun emptyPausedBoundWithLocalDataFamilyId_assignsPendingRole() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-1",
            membersContainUid = false,
            memberCount = 0,
            accountId = null,
            localDataFamilyId = "local-family-1",
            suppressAuthMemberBootstrap = false,
            restoreInProgress = false
        )
        assertEquals(
            MemberRole.PENDING,
            (action as AuthenticatedMemberBootstrapPolicy.Action.CreateMember).role
        )
    }

    @Test
    fun emptyRestoredOwnerWithLocalFamilyId_assignsPendingRole() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-owner",
            membersContainUid = false,
            memberCount = 0,
            accountId = null,
            localDataFamilyId = "restored-family",
            suppressAuthMemberBootstrap = false,
            restoreInProgress = false
        )
        assertEquals(
            MemberRole.PENDING,
            (action as AuthenticatedMemberBootstrapPolicy.Action.CreateMember).role
        )
    }

    @Test
    fun existingFamily_assignsPendingRole() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-1",
            membersContainUid = false,
            memberCount = 2,
            accountId = null,
            localDataFamilyId = null,
            suppressAuthMemberBootstrap = false,
            restoreInProgress = false
        )
        assertEquals(
            MemberRole.PENDING,
            (action as AuthenticatedMemberBootstrapPolicy.Action.CreateMember).role
        )
    }

    @Test
    fun suppressionOrRestoreInProgress_noOp() {
        assertTrue(
            AuthenticatedMemberBootstrapPolicy.resolve(
                authenticatedUid = "uid-1",
                membersContainUid = false,
                memberCount = 0,
                accountId = null,
                localDataFamilyId = null,
                suppressAuthMemberBootstrap = true,
                restoreInProgress = false
            ) is AuthenticatedMemberBootstrapPolicy.Action.NoOp
        )
        assertTrue(
            AuthenticatedMemberBootstrapPolicy.resolve(
                authenticatedUid = "uid-1",
                membersContainUid = false,
                memberCount = 0,
                accountId = null,
                localDataFamilyId = null,
                suppressAuthMemberBootstrap = false,
                restoreInProgress = true
            ) is AuthenticatedMemberBootstrapPolicy.Action.NoOp
        )
    }

    @Test
    fun uidPresent_clearsStaleSuppression() {
        val action = AuthenticatedMemberBootstrapPolicy.resolve(
            authenticatedUid = "uid-1",
            membersContainUid = true,
            memberCount = 1,
            accountId = "family-1",
            localDataFamilyId = "local-1",
            suppressAuthMemberBootstrap = true,
            restoreInProgress = false
        )
        assertTrue(action is AuthenticatedMemberBootstrapPolicy.Action.ClearStaleSuppression)
    }
}
