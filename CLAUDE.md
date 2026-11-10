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
- `workout/`, `diet/`, `settings/`, `transfer/` (later: `progress/`); `assets/starter-exercises.openweight.json` holds the
  40 common exercises (`StarterExercises`), imported like any file — one package per feature: screens, ViewModels, domain rules, repositories
- `workout/blocks/` — training blocks; `Schedule.kt` holds the schedule rule (date → block → weekday's workout; overlapping blocks: the later start wins)
- `diet/` — ingredients and recipes; `data/Nutrition.kt` holds the calories/macros rule (per 100 g or ml, pieces via grams per piece, uncountable lines listed, never silently dropped)
- `diet/shopping/` — shopping list panel; rules in `diet/data/ShoppingListBuilder.kt` (sum per ingredient and unit kind, scaled to servings, rounded up for the shop) and `ShoppingRepository.kt` (update keeps manual items, ticks and hand-edited amounts)
- `diet/calendar/`, `diet/plans/` — meal calendar and diet plans; rules in `diet/data/MealPlanRepository.kt` (copy day/week, apply a plan: skip or replace busy days)
- `home/` — Home tab (opens first): Mon–Sun week view; `WeekPlan.kt` builds each day (workout + meals)
- `AppNavigation.kt` — bottom tabs (Home, Workout, Diet, Settings), each with its own Navigation 3 back stack
- `transfer/` — Open Weight file format (`TransferFormat.kt`); `ExportService` (scopes: everything, workout, diet, picked items, date range;
  a file always includes what its records depend on); `ImportService` (preview, then Skip / Replace / Newer wins / Keep both per match,
  links follow their records, one transaction, "Replace everything"); `ImportSession` (the file being imported, shared by Settings,
  files shared from other apps and the preview screen; saves the automatic copy before Replace everything); `ui/` (export dialog and
  screen, import preview)
- `app/schemas/` — exported Room schemas, committed; a version bump needs a migration + test (`MigrationTest`)
- `plans/` — import files for real training plans (+ the script that generates the Winter Arc one)

## Rules
- The app must stay fully offline: no `INTERNET` permission (`OfflineGuardTest` enforces it).
- "Today" comes from `TimeAndIds.today()` (injectable), never `LocalDate.now()` directly.
  Screens that mark or depend on today follow `core/Today` (a flow, refreshed whenever the app comes back to the
  front), so they don't show yesterday after midnight; one-off defaults read `TimeAndIds.today()` when used.
- Numbers typed by the user go through `parseNumber` (refuses `NaN`/`Infinity`); limits live in the model classes'
  `init` checks (`SetValues`, `IngredientValues`, `RecipeInfo`), which the importer reuses.
- A repository call a screen can reach must not throw for a state the screen allows: disable the control at the limit.
- Every record has a UUID text `id` plus `createdAt`/`updatedAt` (import/export relies on them).
- User-visible text goes in `res/values/strings.xml`.
- Editing rules (limits, in-use checks, ordering) live in repositories, not in the UI; screens call them.
- Compose UI tests on Robolectric: wait for screens to settle (navigation animates), and use
  `performTextReplacement` in dialogs (`performTextInput` focuses the field and the cursor blink keeps Compose busy).
  Texts inside a clickable card merge into it: find their tags with `useUnmergedTree = true`.
- Accessibility: everything you can tap needs a label TalkBack reads (its text, or a content description);
  `a11y/AccessibilityTest` walks every screen with data and fails otherwise. A row with a checkbox is
  `toggleable` as a whole (the `Checkbox` gets `onCheckedChange = null`).
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
