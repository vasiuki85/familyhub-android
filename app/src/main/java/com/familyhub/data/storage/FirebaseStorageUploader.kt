package com.familyhub.data.storage

import android.net.Uri
import com.familyhub.data.datastore.SyncSettingsDataStore
import com.familyhub.data.sync.SyncExportSanitizer
import com.familyhub.domain.utils.BundledFamilyAvatar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class FirebaseStorageUploader @Inject constructor(
    private val storage: FirebaseStorage,
    private val syncSettingsDataStore: SyncSettingsDataStore,
    private val auth: FirebaseAuth
) {
    /**
     * Uploads a local [content://] avatar when cloud sync is enabled.
     * Returns an existing remote URL unchanged; returns null when the photo should stay local-only.
     */
    suspend fun uploadMemberAvatarIfNeeded(photoUrl: String?): String? {
        if (photoUrl.isNullOrBlank()) return null
        if (BundledFamilyAvatar.isToken(photoUrl)) return photoUrl
        if (SyncExportSanitizer.isRemoteUrl(photoUrl)) return photoUrl
        if (!photoUrl.startsWith("content://")) return null

        val settings = syncSettingsDataStore.settingsFlow.first()
        if (!settings.isEnabled) {
            Timber.d("FirebaseStorageUploader: sync disabled, keeping local avatar URI")
            return null
        }
        val familyId = settings.accountId?.takeIf { it.isNotBlank() } ?: return null
        val uid = auth.currentUser?.uid ?: return null

        val objectPath = "families/$familyId/members/$uid/avatar.jpg"
        val ref = storage.reference.child(objectPath)
        ref.putFile(Uri.parse(photoUrl)).await()
        return ref.downloadUrl.await().toString()
    }
}
