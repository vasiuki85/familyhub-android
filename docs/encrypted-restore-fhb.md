# Encrypted restore — FH2-19B2

Crash-safe destructive restore for `.fhb` backups. Core/use-case only (no Compose UI). Local-only: never auto-enables sync or uploads after restore.

## API

`EncryptedRestoreCoordinator`:

1. `inspect(file, password, authenticatedUserId?) → RestorePreview` — on `@IoDispatcher`: resolve app-private canonical path, one bounded read (≤ 64 MiB), SHA-256 over those bytes, decrypt the same buffer, validate; returns metadata/counts/fingerprint/`containsAuthenticatedUser` only; no Room/DataStore mutations; clears password `CharArray` in the outer `finally` (including cancellation before the mutex).
2. `restore(file, password, expectedFingerprint, authenticatedUserId?) → RestoreResult` — on `@IoDispatcher`: path-only resolve outside the lock; inside `SyncExclusiveLock`: re-resolve, bounded re-read, fingerprint match **before Argon2**, decrypt same bytes (AES-GCM `doFinal` over the file buffer without a ciphertext copy), validate, `beginRestoreBarrier` (fail-safe suppression **before** Room replace), Room replace via `applyValidatedRestoreSnapshot` (production settle delay), then `finalizeRestoreBinding(expectedFingerprint, source, suppressAuthMemberBootstrap)` in one DataStore edit. No ciphertext retained across waiters. No singleton pending payload.

## Barrier and UID suppression

Persistent DataStore flags (`restoreInProgress`, fingerprint, nullable source family). `beginRestoreBarrier` fail-safe sets `suppressAuthMemberBootstrap=true` before Room replace. `finalizeRestoreBinding` atomically writes the final suppression flag and clears the barrier. While `restoreInProgress`: sync lifecycle and `AuthenticatedMemberBootstrapCoordinator` refuse bootstrap. Sign-out and family-binding transition clear suppression.

## Family binding after success

Sync off; account/device/lastSync/lastError/tombstones cleared; `hasPendingChanges=false`; `localDataFamilyId = sourceFamilyId` (removed when null); Google Calendar id preserved; Firebase auth untouched.

## Validation notes

Historical orphans without Room FK are accepted: completion→deleted task, reward request→deleted reward, shopping activity→deleted item, appreciation→deleted members. Hard FK relations (goals/stages/steps, menu entries, rotations, task history, shopping list/category) stay strict. Unique Room constraints (global recipe child ids, step order, achievements, menu slots, daily assignments) fail closed before barrier/DAO.
