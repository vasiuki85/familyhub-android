package com.familyhub.ui.screens.contacts

import com.familyhub.domain.model.MemberRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsUiStateTest {

    @Test
    fun adminAndParent_canManageContacts() {
        assertTrue(ContactsUiState(currentUserRole = MemberRole.ADMIN).canManageContacts)
        assertTrue(ContactsUiState(currentUserRole = MemberRole.PARENT).canManageContacts)
    }

    @Test
    fun childAndPending_areReadOnly() {
        assertFalse(ContactsUiState(currentUserRole = MemberRole.CHILD).canManageContacts)
        assertFalse(ContactsUiState(currentUserRole = MemberRole.PENDING).canManageContacts)
    }
}
