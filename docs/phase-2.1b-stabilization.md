# Phase 2.1B — Build, Test, and Runtime Stabilization

Date: 2026-07-28  
Status: **partially verified; not ready to claim runtime completion**

## Environment

- Host: Windows 11
- Project initially had no Gradle wrapper, JDK, Android SDK, `adb`, emulator, or configured Android environment variables.
- Portable JDK: Eclipse Adoptium 17.0.19+10
- Gradle wrapper restored: Gradle 9.4.1
- Android SDK prepared locally:
  - platform-tools 37.0.0
  - platform android-37 revision 2
  - build-tools 36.0.0
- AGP: 9.2.0
- Kotlin: 2.3.21
- KSP: 2.3.3
- Hilt: upgraded from 2.57.1 to 2.59.2 for AGP 9 compatibility

## Commands and results

```text
gradlew.bat --version
PASS — Gradle 9.4.1, JVM 17.0.19

gradlew.bat projects --no-daemon
Initial FAIL — org.jetbrains.kotlin.android cannot be applied with AGP 9 built-in Kotlin
Fixed by removing the obsolete Kotlin Android plugin application.

gradlew.bat projects --no-daemon
Second FAIL — Hilt 2.57.1 used removed Android BaseExtension API
Fixed by upgrading Hilt plugin/runtime/compiler together to 2.59.2.

gradlew.bat projects --no-daemon
Third FAIL — KSP registers generated sources through kotlin.sourceSets
Fixed with AGP's KSP compatibility switch android.disallowKotlinSourceSets=false.

gradlew.bat :app:assembleConsumerDebug --no-daemon
INCOMPLETE — reached :app:kspConsumerDebugKotlin; dependency transfer timed out.

gradlew.bat :app:kspConsumerDebugKotlin --no-daemon --offline
FAIL — three artifacts were not yet cached:
  kotlinx-coroutines-core-jvm:1.10.2
  kotlinx-serialization-core-jvm:1.7.3
  androidx.exifinterface:exifinterface:1.0.0
```

The remaining build failure is an environment/network-transfer issue, not a reported Kotlin compiler error. It must not be interpreted as a successful APK build.

## Static safety checks

- Database version is 3.
- `MIGRATION_2_3` exists and is registered with `.addMigrations(...)`.
- No `fallbackToDestructiveMigration` reference was found.
- Verification debug navigation and simulation controls are guarded by `BuildConfig.DEBUG`.
- No `MediaRecorder` or `VideoCapture` reference was found; the verification path does not intentionally record video.
- 24 JVM test methods are declared under `app/src/test`.

## Device/runtime status

- Android emulator package is not installed and no AVD is available.
- `adb` exists, but this sandbox cannot create the default user `.android` directory, so device enumeration could not be completed.
- Installation, launch, camera permission flow, CameraX binding, ML Kit inference, pose overlay alignment, calibration behavior, lifecycle recovery, thermal behavior, and long-session stability were **not runtime verified**.
- Room migration was statically inspected but no migration instrumentation test could run without a device/emulator.

## Files changed in this stabilization pass

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `docs/phase-2.1b-stabilization.md`

## Readiness

The wrapper and compatible build-plugin baseline are now present. Phase 2.1B is **not fully complete** until dependencies resolve, both variants compile, JVM tests and lint pass, migration is exercised, and camera/runtime checks pass on real Android hardware or an emulator with a usable camera source.

## GitHub build handoff

The project now includes `.github/workflows/android-ci.yml`. GitHub Actions is
the primary clean build environment and runs both-flavor unit tests, lint, and
debug APK assembly. Successful runs upload the consumer APK, dedicated APK, and
verification reports as separate artifacts. Runtime camera and migration
instrumentation testing still requires an Android device or emulator and is not
claimed by this workflow.
