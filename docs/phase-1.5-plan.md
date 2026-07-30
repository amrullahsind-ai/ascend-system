# Phase 1.5 safe implementation plan

## Invariants

- Preserve all v1 tables, columns, quest behavior, state-machine rules, and product flavors.
- Never use `fallbackToDestructiveMigration`.
- Existing quests/restrictions/settings win over generated recommendations.
- Protocol activation requires explicit review and a three-second hold.
- AI text is explanatory only; deterministic code generates and activates proposals.

## Batches

1. Add assessment/protocol domain models, validation, generation, approval, and merge rules.
2. Add Room v2 tables and `MIGRATION_1_2`; introduce repository and use cases.
3. Add assessment ViewModel with immutable state/actions and process-safe Room persistence.
4. Add routes for calibration, assessment, review, and contract; preserve old route behavior.
5. Refine design tokens/components, then move screens out of `MainActivity` in controlled batches.
6. Connect dashboard/quest UI to repository-backed state; no preview data in runtime.
7. Add unit and migration-oriented tests; retain all existing tests.

## Schema changes

Add `assessment_drafts`, `personal_protocols`, and `app_metadata`. Do not modify existing tables. `app_metadata` stores assessment/protocol flags and versions. New users receive default metadata; existing installations are detected by migration/default row and routed to calibration.

## Navigation changes

Add `calibration`, `assessment/{step}`, `protocolReview`, and `systemContract`. Start-route resolution reads metadata:

- new install: initialization → permissions → assessment;
- existing v1 migration: calibration → assessment;
- activated protocol: dashboard.

## Safe UI replacement

Safe: theme tokens, panels/buttons/cards, private screen functions, and hardcoded dashboard prototype.
Preserve contracts: `QuestRepository`, Room v1 entities, rule engines, emergency path, and flavor messaging.
