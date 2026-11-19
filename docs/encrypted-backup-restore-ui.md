# Encrypted backup/restore UI — FH2-19B3

Settings UI for encrypted `.fhb` create/restore. Core crypto/restore remains B1/B2.

## Architecture

`EncryptedBackupRestoreViewModel` owns an explicit phase machine (`Hydrating` → `Idle` → create/inspect/preview/restore → `Success`/`FatalError`). State never stores password, `ExportData`, or ciphertext. Share intents are one-shot UI events.

## Staging

SAF `OpenDocument` copies into `cache/restore_staging/familyhub_restore_*.fhb` with a 64 MiB streaming bound (`Staging` phase blocks UI). Only the staged app-private file is passed to `EncryptedRestoreCoordinator`. Hydration from `SavedStateHandle` cannot overwrite a newer user operation (generation guard).

## Auth UID policy

`beginRestoreBarrier` fail-safe enables suppression before Room replace. `finalizeRestoreBinding` writes the final flag atomically. `AuthenticatedMemberBootstrapCoordinator` serializes startup bootstrap under `SyncExclusiveLock`. Persistent `needsLocalProfileRecovery` is derived from `suppressAuthMemberBootstrap` + members + Firebase UID.

## Preview auth session

Inspect stores `inspectAuthUid`. Restore requires the same Firebase session; mismatch invalidates preview and requires re-inspect.
