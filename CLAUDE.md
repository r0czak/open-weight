# Open Weight

Android app for tracking workouts and diet.

## Stack
- Kotlin, Jetpack Compose (Material 3), Navigation 3, AndroidX Lifecycle/ViewModel
- Android Gradle Plugin 9, Gradle wrapper (see `gradle/wrapper/gradle-wrapper.properties`)
- Versions live in `gradle/libs.versions.toml` — add new dependencies there
- compileSdk/targetSdk 37, minSdk 24, JVM toolchain 17
- Package / applicationId: `com.openweight.app`

## Layout
- `app/src/main/kotlin/com/openweight/app/` — `MainActivity`, navigation, `data/`, `ui/<feature>/`
- `app/src/test/` — local JVM unit tests
- `app/src/androidTest/` — instrumented/Compose UI tests (need a device or emulator)

## Commands
```bash
./gradlew assembleDebug          # build APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
```

## Cloud environment notes
- Android SDK is at `/opt/android-sdk` (`ANDROID_HOME`); `local.properties` is git-ignored.
- No emulator in the cloud container: instrumented tests can't run there; rely on unit tests.
- Maven Central may answer HTTP 429 during a cold dependency download; re-run with
  `./gradlew --max-workers=1 ...` — cached artifacts make each retry go further.
