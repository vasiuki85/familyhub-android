package com.familyhub.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Funnel events deliberately contain only coarse, non-identifying values. */
@Singleton
class ActivationAnalytics @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val analytics = FirebaseAnalytics.getInstance(context)

    fun signupStarted() = log(EVENT_SIGNUP_STARTED)
    fun signupCompleted() = log(EVENT_SIGNUP_COMPLETED)
    fun signinCompleted() = log(EVENT_SIGNIN_COMPLETED)
    fun onboardingChoice(choice: Choice) = log(EVENT_ONBOARDING_CHOICE, PARAM_CHOICE to choice.value)
    fun familyConnected(mode: FamilyMode) = log(EVENT_FAMILY_CONNECTED, PARAM_MODE to mode.value)
    fun approvalChecked() = log(EVENT_APPROVAL_CHECKED)
    fun taskLoopCompleted() = log(EVENT_TASK_LOOP_COMPLETED)

    private fun log(name: String, parameter: Pair<String, String>? = null) {
        analytics.logEvent(name, Bundle().apply {
            parameter?.let { putString(it.first, it.second) }
        })
    }

    enum class Choice(val value: String) { CREATE("create"), JOIN("join"), LOCAL("local") }
    enum class FamilyMode(val value: String) { CREATE("create"), JOIN("join") }

    private companion object {
        const val EVENT_SIGNUP_STARTED = "activation_signup_started"
        const val EVENT_SIGNUP_COMPLETED = "activation_signup_completed"
        const val EVENT_SIGNIN_COMPLETED = "activation_signin_completed"
        const val EVENT_ONBOARDING_CHOICE = "activation_onboarding_choice"
        const val EVENT_FAMILY_CONNECTED = "activation_family_connected"
        const val EVENT_APPROVAL_CHECKED = "activation_approval_checked"
        const val EVENT_TASK_LOOP_COMPLETED = "activation_task_loop_completed"
        const val PARAM_CHOICE = "choice"
        const val PARAM_MODE = "mode"
    }
}
