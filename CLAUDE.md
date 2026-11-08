# Open Weight

Offline-first Android app for planning workouts (training blocks, workouts, exercises) and diet
(recipes, meal calendar, shopping list), with import/export of any data as JSON files.

## Stack
- Kotlin, Jetpack Compose (Material 3), Navigation 3, AndroidX Lifecycle/ViewModel
- Room (KSP) for the database, DataStore for settings, Hilt for dependency injection
- Android Gradle Plugin 9 (built-in Kotlin), Gradle wrapper, JVM toolchain 21 (bytecode target 17)
- Versions live in `gradle/libs.versions.toml` — add new dependencies there
- compileSdk/targetSdk 37, minSdk 24 (java.time via core library desugaring)
- Package / applicationId: `com.openweight.app`

## Layout (`app/src/main/kotlin/com/openweight/app/`)
- `core/` — `database/` (Room entities, converters, `OpenWeightDatabase`), `di/`, `model/`, `ui/` (theme, shared composables)
- `workout/`, `diet/`, `settings/` (later: `transfer/`, `progress/`) — one package per feature: screens, ViewModels, domain rules, repositories
- `AppNavigation.kt` — bottom tabs, each with its own Navigation 3 back stack
- `transfer/` — Open Weight file format (`TransferFormat.kt`) and `ImportService`
- `app/schemas/` — exported Room schemas, committed; a version bump needs a migration + test (`MigrationTest`)
- `plans/` — import files for real training plans (+ the script that generates the Winter Arc one)

## Rules
- The app must stay fully offline: no `INTERNET` permission (`OfflineGuardTest` enforces it).
- Every record has a UUID text `id` plus `createdAt`/`updatedAt` (import/export relies on them).
- User-visible text goes in `res/values/strings.xml`.
- Editing rules (limits, in-use checks, ordering) live in repositories, not in the UI; screens call them.
- Compose UI tests on Robolectric: wait for screens to settle (navigation animates), and use
  `performTextReplacement` in dialogs (`performTextInput` focuses the field and the cursor blink keeps Compose busy).
- Design docs (product spec, data model, import/export format, delivery plan) are the source of truth for behaviour; update them when a decision changes.

## Commands
```bash
./gradlew assembleDebug                         # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                     # unit + Robolectric (DB, UI) tests, no emulator needed
./gradlew lintDebug                             # Android lint
```
CI (`.github/workflows/android.yml`) runs all three on every push and uploads the APK.

## Signing
`app/debug.keystore` is a shared debug key committed on purpose, so every build installs over the
previous one and keeps data. Never use it for a store release.

## Cloud environment notes
- Android SDK is at `/opt/android-sdk` (`ANDROID_HOME`); `local.properties` is git-ignored.
- No emulator: instrumented tests can't run; Robolectric covers DB and UI tests on the JVM.
- Maven Central rate-limits the cloud machine (HTTP 429). `~/.gradle/init.d/maven-central-mirror.gradle.kts`
  (created by the environment setup script, not in the repo) routes downloads through Google's
  Maven Central mirror, including Robolectric's Android images.
