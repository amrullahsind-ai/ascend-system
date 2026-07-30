# ASCEND SYSTEM — Phase 1.5

An Android productivity quest foundation with deterministic rules, persistent local models, an Initial Assessment and protocol-contract flow, and an original cyan/violet System UI.

## Included

- onboarding and permission education;
- dashboard and quest CRUD vertical slice;
- rule, safety, schedule, state-machine, and progression engines;
- Room schema and repository boundaries;
- allowlist, usage-limit, sleep, blocking, and emergency-override models/screens;
- local deterministic planning provider (no cloud required);
- consumer and dedicated build flavors;
- unit tests for rule/state/schedule/progression logic.

Exercise repetition counting, exact alarm quests, running, cloud AI, and Device Owner policies are intentionally deferred.

## Phase 2.1 verification foundation

The project now includes a generic persisted verification engine, CameraX preview, on-device ML Kit pose landmarks, skeleton guidance, calibration, camera permission/error states, and debug-only verification tools. Exercise recognition and repetition counting remain intentionally deferred. See `docs/phase-2.1-audit.md` and `docs/phase-2.1-status.md`.

## Phase 1.5 flow

New users follow initialization → permission education → assessment → protocol review → contract → dashboard. Migrated Phase 1 users are routed through calibration while their existing v1 tables remain untouched. See `docs/phase-1.5-audit.md`, `docs/phase-1.5-plan.md`, and `docs/phase-1.5-status.md`.

## Safety and platform boundaries

Consumer mode is best-effort. It does not promise an inescapable device lock. Emergency Override remains visible on restriction screens. Dedicated mode requires explicit Device Owner provisioning and is currently only a build boundary—not a working DPC. This project does not use Accessibility Service, retain camera video, or embed API keys.

## Build

GitHub Actions is the primary build environment. Every push to `main` or `master`,
every pull request, and every manual workflow run performs both-flavor JVM tests,
lint, and debug APK builds. Successful runs publish these downloadable artifacts:

- `ascend-consumer-debug-apk`
- `ascend-dedicated-debug-apk`
- `ascend-verification-reports`

Open the repository's **Actions** tab, select **Android CI**, then select
**Run workflow** for a manual build. Download the APK from the run's
**Artifacts** section after the job succeeds.

Local builds remain possible with JDK 17, Android SDK 37, and an internet
connection for initial dependency resolution:

```bash
./gradlew :app:testConsumerDebugUnitTest
./gradlew :app:assembleConsumerDebug
```

On Windows use `gradlew.bat`. The Gradle 9.4.1 wrapper is included in the
repository.

## Structure

The current single module uses requested package boundaries under `com.ascendsystem.app`. Split packages into independent Gradle modules after the domain interfaces settle. Architecture artifacts live in `docs/`.

## Known Phase 1 gap

Quest CRUD is connected to Room through a Hilt ViewModel and schedules Android alarms. The consumer flavor supports user-configured best-effort app blocking through Usage Access, persisted Emergency Override, automatic sleep scheduling, and live squat verification. Dedicated Device Owner provisioning remains a separate deployment workflow.
