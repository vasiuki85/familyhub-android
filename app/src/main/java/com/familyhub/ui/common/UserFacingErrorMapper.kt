package com.familyhub.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.familyhub.R
import com.familyhub.data.sync.SyncFamilyInvariantException
import com.familyhub.ui.screens.auth.FirebaseAuthErrorMapper
import com.familyhub.ui.screens.settings.SettingsSyncErrorMapper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Maps throwables to reviewed user-facing strings. Technical details are logged only.
 */
object UserFacingErrorMapper {
    @StringRes
    fun messageRes(
        throwable: Throwable,
        context: UserFacingErrorContext = UserFacingErrorContext.General,
    ): Int {
        if (throwable is CancellationException) throw throwable

        return when (throwable) {
            is SyncFamilyInvariantException -> SettingsSyncErrorMapper.messageRes(throwable)
            is FirebaseAuthException, is FirebaseNetworkException ->
                FirebaseAuthErrorMapper.messageRes(throwable)
            is FirebaseFirestoreException -> firestoreMessageRes(throwable)
            is UnknownHostException, is SocketTimeoutException, is IOException ->
                R.string.error_network_unavailable
            else -> context.fallbackRes
        }
    }

    fun message(
        androidContext: Context,
        throwable: Throwable,
        context: UserFacingErrorContext = UserFacingErrorContext.General,
        tag: String? = null,
    ): String {
        log(throwable, tag)
        return androidContext.getString(messageRes(throwable, context))
    }

    private fun log(throwable: Throwable, tag: String?) {
        if (tag != null) {
            Timber.tag(tag).e(throwable, "User-facing error")
        } else {
            Timber.e(throwable, "User-facing error")
        }
    }

    @StringRes
    private fun firestoreMessageRes(exception: FirebaseFirestoreException): Int = when (exception.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        -> R.string.error_permission_denied

        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> R.string.error_sync_quota
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.error_sync_timeout
        FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network_unavailable
        else -> R.string.settings_sync_safe_error
    }
}
