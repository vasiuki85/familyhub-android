package com.familyhub.data.menu.cloud

/**
 * Pure cloud menu merge/projection engine.
 *
 * Deterministic, side-effect free, and independent of Room, DataStore, Firebase, and wall clock.
 */
object MenuCloudMergeEngine {

    fun merge(input: MenuCloudMergeInput): MenuCloudMergeResult {
        val remotePresent = input.remoteSnapshot != null
        val capability = MenuCloudWriteCapability.evaluate(
            remoteFamilyPresent = remotePresent,
            currentUserId = input.currentUserId,
            remoteSelfMember = input.remoteSelfMember,
            localSelfMember = input.localSelfMember,
        )

        MenuCloudSnapshotValidator.validateStrict(input.localSnapshot, "local")
        input.remoteSnapshot?.let { MenuCloudSnapshotValidator.validateStrict(it, "remote") }

        val rawRemoteBase = input.remoteSnapshot?.let { MenuCloudRawRemoteBase.fromSnapshot(it) }
        val localProcessed = MenuCloudSnapshotProcessor.process(input.localSnapshot)
        val remoteProcessed = input.remoteSnapshot?.let { MenuCloudSnapshotProcessor.process(it) }

        val remoteClassifications = remoteProcessed?.classifications.orEmpty()

        val (localProjection, cloudTarget) = when {
            capability.canUploadMenu && remoteProcessed == null -> {
                val projected = MenuCloudSnapshotProcessor.applyTombstones(
                    snapshot = localProcessed.snapshot,
                    tombstones = input.tombstones,
                    documentIndex = localProcessed.documentIndex,
                )
                projected to projected
            }
            capability.canUploadMenu && remoteProcessed != null -> {
                val merged = MenuCloudSnapshotProcessor.mergeWriters(localProcessed, remoteProcessed)
                val tombstoneIndex = MenuCloudDocumentIndex.merge(
                    localProcessed.documentIndex,
                    remoteProcessed.documentIndex,
                    merged.documentIndex,
                )
                val projected = MenuCloudSnapshotProcessor.applyTombstones(
                    snapshot = merged.snapshot,
                    tombstones = input.tombstones,
                    documentIndex = tombstoneIndex,
                )
                projected to projected
            }
            remoteProcessed != null -> {
                val projected = remoteProcessed.snapshot
                projected to projected
            }
            else -> {
                val projected = localProcessed.snapshot
                projected to MenuCloudSnapshot.empty()
            }
        }

        val pinnedLocalProjection = MenuCloudImmutableParentFields.pinCreatedAt(localProjection, rawRemoteBase)
        val pinnedCloudTarget = MenuCloudImmutableParentFields.pinCreatedAt(cloudTarget, rawRemoteBase)

        return MenuCloudMergeResult(
            localProjection = pinnedLocalProjection,
            cloudTarget = pinnedCloudTarget,
            rawRemoteBase = rawRemoteBase,
            remoteIdClassifications = remoteClassifications,
            capability = capability,
        )
    }
}
