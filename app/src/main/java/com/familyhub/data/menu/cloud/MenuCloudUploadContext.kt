package com.familyhub.data.menu.cloud

/**
 * Immutable menu upload decision formed inside the sync mutex.
 *
 * Invariant: [capability.canUploadMenu] == false implies [operations] is empty.
 */
data class MenuCloudUploadContext(
    val capability: MenuCloudWriteCapabilityResult,
    val operations: List<MenuCloudWriteOperation>,
) {
    init {
        if (!capability.canUploadMenu) {
            require(operations.isEmpty()) {
                "Menu upload operations must be empty when canUploadMenu=false"
            }
        }
    }

    val canUploadMenu: Boolean get() = capability.canUploadMenu
}
