# Phase 1.5 actual-project audit

Audit date: 2026-07-28. Scope: the complete checked-in project before Phase 1.5 changes.

1. **Gradle modules:** one Android application module, `:app`; `consumer` and `dedicated` product flavors.
2. **Package structure:** root app plus `core.ai`, `core.data`, `core.database`, `core.designsystem`, `core.domain`, `core.rules`, and `service.scheduling`.
3. **Navigation destinations:** onboarding, permissions, dashboard, quests, allowlist, sleep, blocking, override, settings. Routes and graph are private to `MainActivity`.
4. **Screens:** all destinations are Compose functions in `MainActivity`; allowlist, sleep, and settings share a static `InfoScreen`.
5. **ViewModels:** none.
6. **UI state classes:** none; screens use local `remember` state.
7. **Room entities:** `QuestEntity`, `AppRestrictionEntity`, `OverrideLogEntity`, `SystemStateEntity`.
8. **Room version/migrations:** version 1; no explicit migration and no destructive-migration fallback. Schema export is enabled but no schema directory is configured.
9. **DataStore preferences:** dependency exists; no DataStore implementation or preference keys.
10. **Repositories:** `QuestRepository` plus Hilt-bound `LocalQuestRepository`; no repositories for restrictions, overrides, state, profile, or settings.
11. **Use cases:** none. UI does not consume `QuestRepository`.
12. **Quest engine:** domain quest model, deterministic `QuestStateMachine`, `RuleEngine`, `SafetyEngine`, and tests. No orchestration/persistence use case.
13. **Schedule engine:** pure overnight-window and sleep-state calculations; no persistent schedule or Android scheduling implementation.
14. **Restriction implementation:** domain model and Room entity only. Blocking UI is a demo; no usage reader or restriction enforcement.
15. **Sleep protocol:** `SleepSchedule` model, schedule calculation, and static information screen only.
16. **Emergency override:** `OverrideRequest` model and local-state UI; no log persistence or activation service.
17. **Notifications:** gateway interface and fake implementation only.
18. **Fake AI:** Hilt-bound deterministic `FakeAiProvider`; returns one focus/recovery quest and explanatory text.
19. **Design system:** five top-level colors, default Material typography, one `AscendTheme`, and one `HoloPanel`. Spacing, shapes, motion, semantic components, and previews are absent.
20. **Automated tests:** five JUnit tests in one class covering state transitions, rule caps/cooldown, physical safety, overnight schedule, and progression.
21. **Incomplete features:** most Phase 1 screens are prototypes; persistence is not connected to UI; onboarding completion is not persisted; no permission requests/fallback state; dashboard data is hardcoded; no ViewModels/use cases; no DataStore; no real scheduling/notifications/restriction/override; no loading/empty/error handling.
22. **Placeholders:** all dashboard metrics, quest list, allowlist, sleep settings, blocking state, scheduler, notification gateway, and dedicated mode behavior.
23. **Files safe to redesign:** `MainActivity.kt`, `core/designsystem/AscendTheme.kt`, and static documentation. New feature packages can be introduced without changing existing domain engines.
24. **Files to preserve:** existing Room v1 table/column definitions, quest domain model, rule/state/safety/schedule behavior, repository contract/mapper, product flavors, manifest safety stance, and existing tests. Additive changes must not rename or drop v1 tables/columns.

## Audit conclusion

Phase 1 is a sound domain and schema scaffold, not a fully wired application. Phase 1.5 must first add a persistent assessment/protocol vertical slice and explicit migration, then move UI out of `MainActivity`. Existing quest data remains authoritative and assessment recommendations must never overwrite it automatically.
