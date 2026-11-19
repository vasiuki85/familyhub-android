package com.familyhub.ui.screens.profile

enum class EditProfileLoadStatus {
    LOADING,
    READY,
    UNAVAILABLE,
    FAILED,
}

enum class EditProfileContentMode {
    LOADING,
    EDITOR,
    UNAVAILABLE,
    LOAD_FAILED,
}

object EditProfileContentPolicy {
    fun resolve(
        memberId: String?,
        loadStatus: EditProfileLoadStatus,
        hasMember: Boolean,
    ): EditProfileContentMode {
        if (memberId.isNullOrBlank()) return EditProfileContentMode.UNAVAILABLE

        return when (loadStatus) {
            EditProfileLoadStatus.LOADING -> EditProfileContentMode.LOADING
            EditProfileLoadStatus.READY -> {
                if (hasMember) {
                    EditProfileContentMode.EDITOR
                } else {
                    EditProfileContentMode.UNAVAILABLE
                }
            }
            EditProfileLoadStatus.UNAVAILABLE -> EditProfileContentMode.UNAVAILABLE
            EditProfileLoadStatus.FAILED -> EditProfileContentMode.LOAD_FAILED
        }
    }
}
