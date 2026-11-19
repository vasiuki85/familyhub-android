# Continuous Integration

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

Triggers: `pull_request`, push to `main`, and `workflow_dispatch`.

## Jobs

| Job | JDK | Purpose |
| --- | --- | --- |
| `android-unit` | 17 (Temurin) | Verifier self-tests, JVM unit tests, debug assemble, native 16 KiB page-size guard, androidTest compile, Room schema drift guard, locale parity guard (builds use the placeholder `google-services.json.example`) |
| `firebase-config` | — | Detects whether the `GOOGLE_SERVICES_JSON` repository secret exists and exposes `available` as a job output |
| `android-instrumented` | 17 (Temurin) | KVM enablement, `connectedDebugAndroidTest` on API 35 x86_64 emulator; runs only when `firebase-config` reports the secret is available |
| `firestore-rules` | 21 (Temurin) + Node 22 LTS | Firestore Security Rules via local emulator (`demo-familyhub` only) |

Job-level `if` conditions cannot read the `secrets` context directly, so the `firebase-config` detector job turns secret availability into an output and `android-instrumented` gates on `needs.firebase-config.outputs.available == 'true'`. Forks and contributors without the secret still get `android-unit` and `firestore-rules`; the maintainer configures `GOOGLE_SERVICES_JSON` (the full private `google-services.json` content) to enable the emulator job.

Workflow permissions are `contents: read` only. Artifact upload works without extra workflow permissions. There is no `pull_request_target`, no production Firebase access, and no deploy step.

Gradle wrapper validation and dependency caching run through `gradle/actions/setup-gradle` with `validate-wrappers: true`. `actions/setup-java` does not use `cache: gradle` because it conflicts with Setup Gradle caching.

The `android-unit` job runs verifier `--self-test` suites (androidTest results, Firestore results, native page-size) before any Gradle work so CI catches regressions in the verifier scripts themselves.

## Local equivalents

From the repository root:

```bash
chmod +x gradlew
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest
./gradlew --no-daemon --stacktrace :app:assembleDebug
bash .github/scripts/verify-native-page-size.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon --stacktrace :app:compileDebugAndroidTestKotlin
bash .github/scripts/guard-room-schema-drift.sh
npm ci --prefix firestore-rules-tests
npm test --prefix firestore-rules-tests
bash .github/scripts/verify-firestore-test-results.sh firestore-test.log 76
```

Verifier self-tests:

```bash
bash .github/scripts/verify-android-test-results.sh --self-test
bash .github/scripts/verify-firestore-test-results.sh --self-test
bash .github/scripts/verify-native-page-size.sh --self-test
```

Instrumented tests (device or emulator required):

```bash
./gradlew --no-daemon connectedDebugAndroidTest
bash .github/scripts/verify-android-test-results.sh
```

## Room schema drift failure

CI fails the `android-unit` job when `app/schemas` is not fully committed and tracked:

1. `git status --porcelain=v1 --untracked-files=all -- app/schemas` is non-empty (modified, deleted, or untracked JSON).
2. The JSON file for the current `FamilyHubDatabase.version` is missing on disk.
3. `git ls-files --error-unmatch` fails for that version file (file exists locally but is not tracked).

`git diff --exit-code` alone is insufficient because untracked schema JSON does not appear in the diff.

Typical causes:

- `exportSchema = true` is disabled or `room.schemaLocation` is misconfigured.
- Database `version` was bumped without committing the generated schema JSON.
- Historical schema files restored locally but never `git add`ed.

`RoomSchemaExportContractTest` runs in `:app:testDebugUnitTest` and enforces the export contract in addition to the post-build schema guard.

## Mandatory instrumented coverage

`android-instrumented` enables KVM on Ubuntu before `android-emulator-runner` (udev rule, reload, trigger) so the cold API 35 emulator can use hardware acceleration instead of slow software rendering.

The job runs `connectedDebugAndroidTest`, always captures `adb logcat -d` to `logcat.txt`, then runs the verifier even when Gradle fails.

The Android verifier parses only `TEST-*.xml` with Python `ElementTree`. A mandatory class counts as executed only when it has at least one non-skipped `<testcase classname="…">`. A class name appearing only in failure text or a skipped testcase does not satisfy the guard.

Required classes:

- `FamilyHubMigrationsTest`
- `TaskManualOrderTransactionInstrumentedTest`
- `TaskCompletionTransitionCoordinatorInstrumentedTest`
- `AccountingMutationCoordinatorInstrumentedTest`
- `MenuMutationCoordinatorInstrumentedTest` (transaction-safe local menu replace core)
- `ShoppingTopBarTest`
- `Argon2KtEncryptedBackupInstrumentedTest` (production argon2kt JNI + Android AES-GCM path)
- `EncryptedRestoreRoomInstrumentedTest` (crash-safe encrypted restore Room replace)
- `EncryptedBackupRestoreSectionTest` (encrypted backup/restore Settings UI smoke)

Zero non-skipped testcases is treated as failure.

## Native 16 KiB page-size guard

After `:app:assembleDebug`, CI runs `.github/scripts/verify-native-page-size.sh` on `app/build/outputs/apk/debug/app-debug.apk`:

1. `zipalign -c -P 16 -v 4` (requires Android SDK build-tools / `ZIPALIGN`)
2. For every `lib/**/*.so` in the APK, every ELF `PT_LOAD` segment must have `p_align >= 0x4000`

Self-tests (`--self-test`) cover passing and failing ELF fixtures without an SDK. Android requires 16 KiB page-size compatibility for relevant Play publications targeting API 35+.

## Firestore rules tests

- Project id: `demo-familyhub` (local emulator only).
- No Firebase tokens, service accounts, or deploy commands.
- `firebase emulators:exec` wraps `node --test`.
- The Firestore verifier reads counters only from the **last** Node test summary block in the log (not earlier blocks from prior runs or emulator boot output).
- CI requires `tests == pass == 76` and `fail == skipped == cancelled == todo == 0`.
- Missing counters in the latest block, or `66 pass + 1 skipped`, fail even if an earlier complete `67/67` summary appears earlier in the log.

Node prints summary lines such as `ℹ tests 67` (info prefix, not `#`).

## GitHub-hosted emulator note

The instrumented job configuration is validated locally (Gradle compile, verifier self-tests in `android-unit`), but a cold API 35 emulator boot on GitHub-hosted runners is only fully confirmed by the first real workflow run.
