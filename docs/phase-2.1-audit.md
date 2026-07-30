# Phase 2.1 actual-project audit

Audit date: 2026-07-28. No Phase 2 code was changed before this report.

1. **Modules/packages:** one `:app` Android module with `consumer` and `dedicated` flavors. Packages cover `core.ai`, `core.data`, `core.database`, `core.designsystem`, `core.domain`, `core.rules`, `feature.assessment`, `feature.dashboard`, and `service.scheduling`.
2. **Quest domain:** `Quest` contains ID, title/description, type, `VerificationType`, scalar target, XP reward, schedule/deadline, status, safety level, and creator. `QuestRepository` exposes list/upsert/delete.
3. **Quest status/state:** quest statuses are draft, scheduled, active, verifying, completed, failed, cancelled. The global `QuestStateMachine` includes persistent-oriented states from idle through verifying/completed/failed/lock/override/recovery.
4. **Existing verification models:** only the quest-level `VerificationType` enum exists (`TIMER`, `APP_USAGE`, `PHOTO`, `QUIZ`, `PARTNER`, `MANUAL_DEMO`). There is no request, target, session, result, progress, confidence, or metrics model. The enum should be expanded and reused rather than duplicated.
5. **Relevant ViewModels/repositories:** `DashboardViewModel` reads quests; quest CRUD UI still uses local state. `LocalQuestRepository` maps Room quests. No quest-detail or verification ViewModel/repository exists.
6. **Navigation:** initialization, permission education, calibration, assessment, protocol review, contract, dashboard, quests, allowlist, sleep, blocking, override, and settings. Routes are private in `MainActivity`; Phase 2 should add a camera route without replacing existing flows.
7. **Permission architecture:** permission education is informational only. Manifest has notification, usage access, overlay, and boot permissions. No reusable runtime-permission state/controller exists; camera permission is absent.
8. **Camera/ML dependencies:** none. No CameraX, ML Kit, MediaPipe, image analysis, preview, or camera code exists.
9. **Room/migrations:** Room version 2. V1 tables: quests, app restrictions, override logs, system state. V2 adds assessment drafts, personal protocols, and app metadata through explicit additive `MIGRATION_1_2`. No destructive fallback is configured.
10. **Tests:** 13 declared JUnit tests across rule/state/schedule/progression and assessment validation/persistence/protocol/activation/merge behavior. No Android, migration, permission, camera, pose, or verification tests.
11. **Safe Verification Engine integration points:** expand the existing `VerificationType`; add a separate verification domain package and Room table; bind a verification repository through the existing Hilt modules; bridge terminal verification results to quest status through a dedicated use case; add a lifecycle-aware camera route; reuse `SystemPanel`, semantic colors, safety notice, status indicator, and buttons. Existing quest tables, rule/state engines, onboarding, assessment, protocol, and dashboard contracts should remain unchanged.

## Constraints discovered

- There is no installed JDK, Gradle wrapper/executable, Android SDK, or adb in the current environment, so runtime camera testing and Android compilation cannot be performed locally.
- Phase 1.5 still has acknowledged prototype areas, but Phase 2.1 will not rebuild them.
- Camera frames must remain transient. Only verification session metadata and pose-derived metrics may be persisted.
