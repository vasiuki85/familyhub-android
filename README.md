# FamilyHub

[![CI](https://github.com/vasiuki85/familyhub-android/actions/workflows/ci.yml/badge.svg)](https://github.com/vasiuki85/familyhub-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![minSdk 24](https://img.shields.io/badge/minSdk-24-green)
![targetSdk 35](https://img.shields.io/badge/targetSdk-35-green)

**FamilyHub** is an open-source Android app that helps families organize everyday life together: chores with a fair reward system for kids, shared shopping lists, a weekly menu planner with a recipe book, family goals, events, and encrypted backups — all synchronized across devices.

The app is offline-first: everything works locally via Room, and a Firestore-backed sync engine merges changes from all family devices with tombstone-based deletion tracking and conflict resolution.

## Features

- **Tasks & chores** — deadlines, reminders, repeating rules, assignees, categories, templates and rotations; completion cycles with an auditable points ledger.
- **Motivation for kids** — reward points per completed task, weekly family rating, reward requests (child asks → parent approves), penalties, achievements, appreciations.
- **Shopping** — shared lists with categories, purchase activity feed, per-item attribution.
- **Weekly menu & recipes** — meal planner, built-in recipe catalog, shopping-list generation from recipes.
- **Family goals** — shared and personal goals with stages and steps.
- **Events & calendar** — family events with optional Google Calendar integration.
- **Roles & onboarding** — ADMIN / PARENT / CHILD roles with role-aware permissions enforced both client-side and in Firestore security rules; join a family via QR code or deep link.
- **Cross-device sync** — transactional upload, merge-based conflict resolution, persisted outbox, tombstones for deletions, sync health indicators.
- **Encrypted backup & restore** — portable `.fhb` backups: Argon2id key derivation + AES-256-GCM, validated restore pipeline.
- **Localization** — English (default) and Russian.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9, JDK 17 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM + Clean Architecture (data / domain / ui), unidirectional Flow |
| DI | Hilt |
| Local storage | Room (39 schema versions with migrations), DataStore Preferences |
| Cloud | Firebase Auth (email/password), Firestore, Storage, Analytics |
| Background work | WorkManager |
| Crypto | Argon2id (argon2kt), AES-GCM |
| Tests | JUnit4, MockK, Coroutines Test, Espresso/Compose UI tests, Firestore rules unit tests |

## Architecture

```
app/src/main/java/com/familyhub/
├── data/        # Room entities/DAOs, repositories, Firestore sync engine,
│                # export/import, encrypted backup, notifications, work
├── domain/      # models, use cases, pure business logic
├── ui/          # Compose screens, ViewModels, components, theme
├── di/          # Hilt modules
└── util/        # helpers
```

- UI observes `StateFlow` from ViewModels; repositories expose Room `Flow`s, so every screen updates reactively.
- The sync engine (`data/sync/`) uploads local changes transactionally, merges remote state (including completion cycles and reward requests), and records deletions as tombstones so they propagate reliably across devices.
- Firestore security rules (`firestore.rules`) mirror the client's role model and are covered by 67 emulator-based tests.

Deeper docs: [architecture](docs/architecture.md) · [sync design](docs/sync.md) · [sync logic reference](SYNC_LOGIC.md) · [backup format](docs/encrypted-backup-fhb.md) · [CI gates](docs/ci.md).

## Getting started

### Prerequisites

- Android Studio (latest stable) with Android SDK 35
- JDK 17 (Temurin recommended)
- A Firebase project (free Spark plan is enough)
- Node.js 22 + JDK 21 — only if you want to run the Firestore rules tests locally

### 1. Set up Firebase

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an **Android** app with package name `com.familyhub` (and a second one with `com.familyhub.debug` if you want debug builds to use a separate app registration).
3. Enable **Authentication → Email/Password**, create a **Firestore** database and **Storage**.
4. Download `google-services.json` and place it at `app/google-services.json`
   (a structural template lives at [`app/google-services.json.example`](app/google-services.json.example)).
5. Deploy security rules and indexes with the [Firebase CLI](https://firebase.google.com/docs/cli):

   ```bash
   cp .firebaserc.example .firebaserc   # then put your project id inside
   firebase deploy --only firestore:rules,firestore:indexes,storage:rules
   ```

> The repository intentionally does **not** ship a real `google-services.json` — every contributor runs against their own Firebase project.

### 2. Build & run

```bash
git clone https://github.com/vasiuki85/familyhub-android.git
cd familyhub-android
# place app/google-services.json (see above)
./gradlew :app:assembleDebug
```

Or open the project in Android Studio and run the `app` configuration. `local.properties` is generated by the IDE; a template with optional release-signing keys is in [`local.properties.example`](local.properties.example).

Release builds are signed only when a keystore is configured (`app/keystore/FamilyHub.jks` + `storePassword`/`keyPassword` in `local.properties`); without it, use debug builds.

## Testing

```bash
# Unit tests (sync core, mappers, ViewModels, backup crypto, …)
./gradlew :app:testDebugUnitTest

# Fast JVM tests for the join-policy module
./gradlew :sync-policy-tests:test

# Instrumented tests (emulator/device, API 35 recommended)
./gradlew :app:connectedDebugAndroidTest

# Firestore security rules tests (local emulator; needs Node 22 + JDK 21)
npm ci --prefix firestore-rules-tests
npm test --prefix firestore-rules-tests
```

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and PR:

- **android-unit** — unit tests, debug APK assembly, native 16 KiB page-size guard, Room schema-drift guard, EN/RU locale parity guard (builds use the placeholder Firebase config).
- **firebase-config** — detects whether the `GOOGLE_SERVICES_JSON` repository secret is configured.
- **android-instrumented** — emulator API 35 run with a strict result verifier. Executes only when the secret is available (maintainer setup); forks without the secret skip this job.
- **firestore-rules** — all 67 rules tests against the local Firestore emulator (`demo-familyhub` project, no credentials needed).

## Localization

Default resources (`values/`) are English-only; Russian lives in `values-ru/` with matching keys. A CI guard enforces parity and blocks Cyrillic in the default locale. See [AGENTS.md](AGENTS.md) for the full contributor conventions (UTF-8 everywhere, string resources, navigation rules).

## Contributing

Issues and pull requests are welcome. Please follow the conventions in [AGENTS.md](AGENTS.md): Kotlin official code style, immutable models and pure mappers, no hardcoded UI strings, new strings added to both locales in the same PR.

## License

[MIT](LICENSE) © vasiuki85
