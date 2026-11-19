package com.familyhub.ui.screens.settings.encrypted

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyhub.R
import com.familyhub.data.restore.RestoreCollectionCounts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupRestoreSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sectionShowsCreateAndRestoreActions() {
        composeRule.setContent {
            MaterialTheme {
                EncryptedBackupRestoreSectionCard(
                    state = EncryptedBackupRestoreUiState(
                        phase = EncryptedBackupRestorePhase.Idle
                    ),
                    onCreateClicked = {},
                    onRestoreClicked = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_section_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_create_button)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_restore_button)
        ).assertIsDisplayed()
    }

    @Test
    fun createPasswordMismatch_showsSingleDialogWithInlineError() {
        composeRule.setContent {
            MaterialTheme {
                EncryptedBackupRestoreDialogHost(
                    phase = EncryptedBackupRestorePhase.CreatePassword(
                        error = EncryptedBackupRestoreError.PASSWORD_MISMATCH
                    ),
                    onConfirmCreate = { _, _ -> },
                    onCancelCreate = {},
                    onConfirmInspect = {},
                    onCancelInspect = {},
                    onContinuePreview = {},
                    onCancelPreview = {},
                    onConfirmRestore = { _, _, _ -> },
                    onCancelConfirmRestore = {},
                    onAddProfile = {},
                    onAcknowledgeSuccess = {},
                    onAcknowledgeError = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_password_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_error_password_mismatch)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.encrypted_backup_error_title)
        ).assertCountEquals(0)
    }

    @Test
    fun confirmRestoreWrongPassword_keepsConfirmDialogVisible() {
        val preview = samplePreview()
        composeRule.setContent {
            MaterialTheme {
                EncryptedBackupRestoreDialogHost(
                    phase = EncryptedBackupRestorePhase.ConfirmRestore(
                        preview = preview,
                        requiresUidConfirmation = false,
                        error = EncryptedBackupRestoreError.WRONG_PASSWORD
                    ),
                    onConfirmCreate = { _, _ -> },
                    onCancelCreate = {},
                    onConfirmInspect = {},
                    onCancelInspect = {},
                    onContinuePreview = {},
                    onCancelPreview = {},
                    onConfirmRestore = { _, _, _ -> },
                    onCancelConfirmRestore = {},
                    onAddProfile = {},
                    onAcknowledgeSuccess = {},
                    onAcknowledgeError = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_restore_confirm_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_error_wrong_password)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.encrypted_backup_preview_title)
        ).assertCountEquals(0)
    }

    @Test
    fun fingerprintChanged_showsInspectDialogNotStalePreview() {
        composeRule.setContent {
            MaterialTheme {
                EncryptedBackupRestoreDialogHost(
                    phase = EncryptedBackupRestorePhase.AwaitingInspectPassword(
                        stagingPath = "/cache/familyhub_restore_x.fhb",
                        error = EncryptedBackupRestoreError.FILE_CHANGED
                    ),
                    onConfirmCreate = { _, _ -> },
                    onCancelCreate = {},
                    onConfirmInspect = {},
                    onCancelInspect = {},
                    onContinuePreview = {},
                    onCancelPreview = {},
                    onConfirmRestore = { _, _, _ -> },
                    onCancelConfirmRestore = {},
                    onAddProfile = {},
                    onAcknowledgeSuccess = {},
                    onAcknowledgeError = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_inspect_password_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_error_file_changed)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.encrypted_backup_preview_title)
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.encrypted_backup_restore_confirm_title)
        ).assertCountEquals(0)
    }

    @Test
    fun needsLocalProfileRecoveryCard_visibleWhenPersisted() {
        composeRule.setContent {
            MaterialTheme {
                EncryptedBackupRestoreSectionContent(
                    state = EncryptedBackupRestoreUiState(
                        needsLocalProfileRecovery = true,
                        addLocalProfileError = EncryptedBackupRestoreError.GENERIC
                    ),
                    onCreateClicked = {},
                    onRestoreClicked = {},
                    onAddProfile = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_needs_local_profile_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.encrypted_backup_error_generic)
        ).assertIsDisplayed()
    }

    private fun samplePreview() = EncryptedBackupRestorePhase.Preview(
        stagingPath = "/cache/familyhub_restore_x.fhb",
        fingerprintSha256 = "abc",
        createdAt = 1L,
        sourceFamilyId = null,
        counts = RestoreCollectionCounts(
            members = 1,
            tasks = 0,
            shoppingLists = 0,
            shoppingItems = 0,
            events = 0,
            recipes = 0,
            familyGoals = 0,
            rewards = 0,
            penalties = 0,
            weeklyMenus = 0,
            menuTemplates = 0
        ),
        inspectAuthUid = "uid-a",
        containsAuthenticatedUser = true
    )
}
