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
- `workout/session/` — live training: `SessionRepository` (a session copies a workout's plan and is filled in set by set; one open at a time;
  suggestions come from last time; rest ends at the last done set's time + its rest) and the live screen. Sessions keep names as text, so history survives deletes
  `Progress.kt` holds the progress rules (best set; step up 2.5 kg / 5 lb when every planned set reached the top of its range);
  `HistoryScreens.kt` the history, session and per-exercise screens; `RestAlerts` the rest-over alarm and notification
- `progress/` — body weight: `BodyWeightRepository` (one weigh-in per day, 20–400 kg, not in the future) and `WeightRules.trend` (weekly
  averages Mon–Sun; change = latest week vs two weeks before)
- `progress/strength/` — power level: `StrengthStandards` (lifts matched by name → family thresholds × factor, load kind,
  muscles worked), `StrengthRules` (best set of 180 days → Epley 1RM / latest weigh-in → score 0–7, 7 tiers; muscle =
  weighted average of its lifts; power level = average of muscles), `BodyMap.kt` (front/back figure, own drawing),
  `PowerLevelScreen.kt`. Men's/women's standards are a setting (kept in backups)
- `diet/` — ingredients and recipes; `data/Nutrition.kt` holds the calories/macros rule (per 100 g or ml, pieces via grams per piece, uncountable lines listed, never silently dropped)
- `diet/household/` — people you cook for (`HouseholdRepository`: name, portion 0.1–3 of yours, eats new/planned meals by default). A calendar
  meal lists who else eats it (`meal_member`); your totals count only your portion, the shopping list counts servings × (1 + their portions)
- `diet/shopping/` — shopping list panel; rules in `diet/data/ShoppingListBuilder.kt` (sum per ingredient and unit kind, scaled to servings, rounded up for the shop) and `ShoppingRepository.kt` (update keeps manual items, ticks and hand-edited amounts)
- `diet/calendar/`, `diet/plans/` — meal calendar and diet plans; rules in `diet/data/MealPlanRepository.kt` (copy day/week, apply a plan: skip or replace busy days;
  eaten marks, swapping a meal's recipe for one day; copies are never eaten). `LiveDiet.kt`: the eaten toggle and the eaten / left line (targets in Settings)
- `home/` — Home tab (opens first): Mon–Sun week view; `WeekPlan.kt` builds each day (workout + meals)
- `AppNavigation.kt` — bottom tabs (Home, Workout, Diet, Settings), each with its own Navigation 3 back stack
- `transfer/` — Open Weight file format (`TransferFormat.kt`); `ExportService` (scopes: everything, workout, diet, picked items, date range;
  a file always includes what its records depend on); `ImportService` (preview, then Skip / Replace / Newer wins / Keep both per match,
  links follow their records, one transaction, "Replace everything"); `ImportSession` (the file being imported, shared by Settings,
  files shared from other apps and the preview screen; saves the automatic copy before Replace everything); `ui/` (export dialog and
  screen, import preview)
  Backups also carry finished sessions (an open one stays on the phone), weigh-ins (matched by date; never two a day), the household
  (matched by name), eaten marks and who eats each meal; `TransferDao.clearAll` must clear every table
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
- Menus (Workout tab, Settings, shortcuts atop list screens) use `core/ui/Menu.kt` (`MenuSection`, `MenuRow`,
  `ShortcutCard`) and the outlined `ic_menu_*` icons (one stroked path each, 2-unit strokes on a 24 grid, tinted where used).
- Design docs (product spec, data model, import/export format, delivery plan) are the source of truth for behaviour; update them when a decision changes.

## Commands
```bash
./gradlew assembleDebug                         # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                     # unit + Robolectric (DB, UI) tests, no emulator needed
./gradlew lintDebug                             # Android lint
```
CI (`.github/workflows/android.yml`) runs all three on every push and uploads the APK.
Releases: push a tag matching `versionName` (`git tag v1.6.0 && git push origin v1.6.0`); `release.yml` checks the match, builds,
tests and publishes a GitHub Release with the APK.

## Signing
`app/debug.keystore` is a shared debug key committed on purpose, so every build installs over the
previous one and keeps data. Never use it for a store release.

## Cloud environment notes
- Android SDK is at `/opt/android-sdk` (`ANDROID_HOME`); `local.properties` is git-ignored.
- No emulator: instrumented tests can't run; Robolectric covers DB and UI tests on the JVM.
- Maven Central rate-limits the cloud machine (HTTP 429). `~/.gradle/init.d/maven-central-mirror.gradle.kts`
  (created by the environment setup script, not in the repo) routes downloads through Google's
  Maven Central mirror, including Robolectric's Android images.
