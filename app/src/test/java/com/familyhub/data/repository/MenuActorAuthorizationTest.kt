package com.familyhub.data.repository

import com.familyhub.data.local.db.dao.FamilyMemberDao
import com.familyhub.data.local.entity.FamilyMemberEntity
import com.familyhub.domain.model.MemberRole
import com.familyhub.domain.utils.MenuMutationForbiddenException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class MenuActorAuthorizationTest {

    private val familyMemberDao = mockk<FamilyMemberDao>()
    private val actorId = "actor-1"

    @Test
    fun adminAndParent_canManageMenu() = runTest {
        coEvery { familyMemberDao.getById(actorId) } returns member(MemberRole.ADMIN)
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorId)

        coEvery { familyMemberDao.getById(actorId) } returns member(MemberRole.PARENT)
        MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorId)
    }

    @Test
    fun childPendingAndMissing_areDenied() = runTest {
        coEvery { familyMemberDao.getById(actorId) } returns member(MemberRole.CHILD)
        assertThrows(MenuMutationForbiddenException::class.java) {
            runBlockingAuthorization { MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorId) }
        }

        coEvery { familyMemberDao.getById(actorId) } returns member(MemberRole.PENDING)
        assertThrows(MenuMutationForbiddenException::class.java) {
            runBlockingAuthorization { MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorId) }
        }

        coEvery { familyMemberDao.getById(actorId) } returns null
        assertThrows(MenuMutationForbiddenException::class.java) {
            runBlockingAuthorization { MenuActorAuthorization.requireCanManageMenu(familyMemberDao, actorId) }
        }
    }

    private fun runBlockingAuthorization(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }

    private fun member(role: MemberRole) = FamilyMemberEntity(
        id = actorId,
        displayName = "User",
        roleLabel = role.name,
        avatarColor = "#000000",
        role = role,
    )
}
