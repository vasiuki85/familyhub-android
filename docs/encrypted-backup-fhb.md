# Encrypted backup (.fhb) — FH2-19B1

Versioned encrypted backup core. No UI and no Room restore in this slice.

## Library choice: Argon2id

| Item | Value |
|------|--------|
| Library | [argon2kt](https://github.com/lambdapioneer/argon2kt) `com.lambdapioneer.argon2kt:argon2kt:1.6.0` |
| Algorithm | Argon2id (PHC winner reference via JNI) |
| License | MIT |
| minSdk | Compatible with project minSdk 24 |
| ABI impact | Ships native `.so` for Android ABIs (armeabi-v7a, arm64-v8a, x86, x86_64). Roughly low hundreds of KB per ABI; no Java heap allocation for the Argon2 working set. |
| Why not PBKDF2 | `PBKDF2WithHmacSHA256` via Android `SecretKeyFactory` is only guaranteed from API 26; unconditional fallback would break minSdk 24. |

JVM unit tests use a deterministic fake `Argon2idKeyDeriver` so format/crypto tests stay fast. Headers under test still carry production-strength KDF parameters. Production JNI (`Argon2KtKeyDeriver`) is covered by mandatory instrumented tests. After hashing, `Argon2KtResult` direct buffers (`rawHash`, `encodedOutput`) are overwritten for their full capacity.

The public encrypt API always generates a production header. An internal/`@VisibleForTesting` overload accepts an explicit header and runs `validateHeaderForEncryption()` fail-closed before password encoding / KDF.

## Production KDF parameters (format v1 minimum)

- Memory: **19 MiB** (`19456` KiB)
- Iterations: **2**
- Parallelism: **1**
- Output key: **256-bit**
- Argon2 version byte (header offset 11, part of AAD): **`0x13`** (v1.3)

Weaker params, unknown Argon2 version, or non-32-byte derived keys are rejected (`EncryptedBackupFormatException` / `EncryptedBackupUnsupportedFormatException`) before or instead of successful encrypt/decrypt.

## File format

Magic `FHUBBKP1`, fixed 52-byte header (entire header = AES-GCM AAD), then ciphertext‖tag.

Payload (encrypted JSON): `payloadVersion`, `createdAt`, nullable `sourceFamilyId` (null = local-only, no cloud family binding), full `ExportData`. Cross-family restore policy is deferred to FH2-19B2.

Share MIME: `application/vnd.familyhub.backup`. Files: atomically unique `familyhub_backup_*.fhb` under `filesDir/backups`.

## Password contract

- Length: 8–256 UTF-16 code units
- Must be well-formed UTF-16 (no lone surrogates)
- UTF-8 encoding size capped (`MAX_PASSWORD_UTF8_BYTES`)
- Failures raise `EncryptedBackupPasswordException` before KDF
- Ownership: callers clear the source `CharArray` in `finally`; codec clears temporary UTF-8 / encoder `ByteBuffer` / key / plaintext buffers

## Limits

- Max file size: 64 MiB
- Plaintext size checked **before** Argon2: `plaintext <= MAX_FILE_BYTES - HEADER_SIZE - GCM_TAG_BYTES` (`EncryptedBackupSizeGuard`)
- Header parsed before large allocations / KDF
- KDF params from file rejected when weaker than production or above DoS ceilings
- Wrong password and ciphertext/tag/AAD tamper → `EncryptedBackupAuthException`
- Unknown magic/version/algorithms / Argon2 version → `EncryptedBackupUnsupportedFormatException`
