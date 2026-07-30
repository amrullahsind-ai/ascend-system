# Phase 1.5 implementation status

## Migrations

- Room `1 → 2`: adds `assessment_drafts`, `personal_protocols`, and `app_metadata`.
- No v1 table or column is renamed, dropped, or rewritten.
- No destructive-migration fallback is enabled.

## Completed

- Actual-project audit, safe plan, and migration design.
- Assessment domain draft covering all ten requested categories.
- Immutable assessment UI state/actions and Hilt ViewModel.
- Step validation, draft save, completion, deterministic protocol generation, merge conflict reporting, and activation use cases.
- Full draft round-trip codec and Room-backed repository.
- Existing-user calibration detection and metadata-based start-flow resolver.
- Protocol review with editable-entry links and explicit proposal language.
- Three-second hold activation, contract/protocol versions, activation timestamp, and persistent activation metadata.
- Emergency override remains reachable from assessment and contract.
- Refined semantic colors, spacing, shapes, motion, panels, buttons, assessment cards, protocol cards, hold control, safety card, status indicator, background, and preview.
- Repository-backed Dashboard quest state with loading, empty, and error states.
- Tests for validation, persistence round trip, strictness, emergency setup, generation, merge/data preservation, activation hold, and migrated-user metadata.

## Incomplete

- Several detailed assessment values currently use safe defaults and summary panels; dedicated editors for every schedule/profile field remain.
- Protocol conflict comparison UI records conflicts at domain level but does not yet present per-field choice controls.
- Quest CRUD and remaining Phase 1 screens still use prototype/local state and need later controlled redesign batches.
- XP, rank, streak, attributes, screen-time telemetry, and restriction counts have no persistent Phase 1 source, so Dashboard truthfully reports them as unavailable rather than inventing data.
- Room migration instrumentation test and Compose navigation/gesture tests require an Android toolchain.

## Risks

- The local environment has no JDK, Gradle executable/wrapper, Android SDK, or adb; compilation and test execution could not be performed here.
- `personal_protocols.payloadJson` currently stores a diagnostic representation; activation metadata is authoritative, but a proper versioned protocol codec is required before editing an activated protocol.
- Product flavor `dedicated` remains informational and must not be presented as an active DPC.

## Changed files

- Build: `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Database/DI: `AscendDatabase.kt`, `DatabaseModule.kt`, `RepositoryModule.kt`
- UI/navigation: `MainActivity.kt`, `AscendTheme.kt`, `AscendComponents.kt`
- Assessment: `AssessmentModels.kt`, `AssessmentUseCases.kt`, `AssessmentPersistence.kt`, `AssessmentViewModel.kt`, `StartupViewModel.kt`
- Dashboard: `DashboardViewModel.kt`
- Tests: `AssessmentUseCasesTest.kt`
- Docs: audit, plan, migration design, checklist, status, README
