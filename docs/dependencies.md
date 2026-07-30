# Dependency list

| Area | Choice |
|---|---|
| Language/toolchain | Kotlin 2.3.21, Java 17, AGP 9.2, compile/target SDK 37, min SDK 26 |
| UI | Compose BOM 2026.06.00, Material 3, Navigation Compose |
| DI | Hilt/Dagger, KSP |
| Persistence | Room, Preferences DataStore |
| Background scheduling | WorkManager; exact alarms deferred |
| Tests | JUnit 4 and pure Kotlin domain tests |

Versions are centralized in `gradle/libs.versions.toml`.
