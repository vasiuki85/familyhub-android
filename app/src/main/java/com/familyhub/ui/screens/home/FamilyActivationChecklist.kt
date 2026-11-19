package com.familyhub.ui.screens.home

import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.domain.model.FamilyMember
import com.familyhub.domain.model.MemberRole

enum class FamilyActivationAction { SETTINGS, TASKS }

data class FamilyActivationChecklist(
    val visible: Boolean,
    val memberStepComplete: Boolean,
    val taskStepComplete: Boolean,
    val hasPendingMember: Boolean,
    val action: FamilyActivationAction,
    @StringRes val titleRes: Int = R.string.home_activation_title,
    @StringRes val messageRes: Int = R.string.home_activation_message,
)

object FamilyActivationChecklistMapper {
    fun map(
        isHydrated: Boolean,
        accountId: String?,
        currentUserId: String,
        currentUserRole: MemberRole,
        members: List<FamilyMember>,
        hasAnyTask: Boolean,
    ): FamilyActivationChecklist {
        val eligible = isHydrated && !accountId.isNullOrBlank() &&
            currentUserRole in setOf(MemberRole.ADMIN, MemberRole.PARENT)
        val memberStepComplete = members.any {
            it.id != currentUserId && it.role != MemberRole.PENDING
        }
        val hasPendingMember = members.any { it.id != currentUserId && it.role == MemberRole.PENDING }
        val visible = eligible && !(memberStepComplete && hasAnyTask)
        return FamilyActivationChecklist(
            visible = visible,
            memberStepComplete = memberStepComplete,
            taskStepComplete = hasAnyTask,
            hasPendingMember = hasPendingMember,
            action = if (hasPendingMember || !memberStepComplete) {
                FamilyActivationAction.SETTINGS
            } else {
                FamilyActivationAction.TASKS
            },
        )
    }
}
