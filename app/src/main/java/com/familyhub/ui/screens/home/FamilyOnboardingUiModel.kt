package com.familyhub.ui.screens.home

import androidx.annotation.StringRes
import com.familyhub.R

enum class FamilyOnboardingAction {
    CREATE_FAMILY,
    JOIN_FAMILY,
    CONTINUE_LOCALLY,
}

data class FamilyOnboardingUiModel(
    val visible: Boolean = false,
    @StringRes val titleResId: Int? = null,
    @StringRes val messageResId: Int? = null,
    @StringRes val contentDescriptionResId: Int? = null,
    val compact: Boolean = false,
) {
    companion object {
        fun hidden(): FamilyOnboardingUiModel = FamilyOnboardingUiModel(visible = false)
    }
}

object FamilyOnboardingUiMapper {
    fun map(
        isFirebaseAuthenticated: Boolean,
        accountId: String?,
        familyOnboardingDismissed: Boolean,
        isHydrated: Boolean,
    ): FamilyOnboardingUiModel {
        if (!isHydrated) {
            return FamilyOnboardingUiModel.hidden()
        }
        if (!isFirebaseAuthenticated) {
            return FamilyOnboardingUiModel.hidden()
        }
        if (accountId != null) {
            return FamilyOnboardingUiModel.hidden()
        }
        if (familyOnboardingDismissed) {
            return FamilyOnboardingUiModel(
                visible = true,
                titleResId = R.string.home_family_onboarding_compact_title,
                messageResId = R.string.home_family_onboarding_compact_message,
                contentDescriptionResId = R.string.home_family_onboarding_compact_cd,
                compact = true,
            )
        }
        return FamilyOnboardingUiModel(
            visible = true,
            titleResId = R.string.home_family_onboarding_title,
            messageResId = R.string.home_family_onboarding_message,
            contentDescriptionResId = R.string.home_family_onboarding_cd,
        )
    }
}
