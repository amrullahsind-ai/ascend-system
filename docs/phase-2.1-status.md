# Phase 2.1 — Verification foundation and camera framework

## Audit summary

Phase 1.5 had one app module, quest-level verification type only, Room v2, no camera/runtime permission architecture, and no pose/session model. Safe integration preserved all existing tables and engines while adding an isolated verification feature.

## Dependencies added

- CameraX `camera-core`, `camera-camera2`, `camera-lifecycle`, and `camera-view` 1.6.1.
- ML Kit base pose detection 18.0.0-beta5 in stream mode.
- MediaPipe was not added.

## Database migration

Room `2 → 3` creates `verification_sessions` plus indices on `questId` and `status`. It is additive, keeps migrations `1 → 2 → 3`, and does not enable destructive migration.

## Implemented

- Generic request, target, session, result, repository, state machine, and six verification use cases.
- Persisted session progress, confidence, reasons, timestamps, status, and string metrics.
- Quest completion/final-failure/cancellation integration.
- Process recovery through Room plus `SavedStateHandle`.
- Camera permission states including denied and permanently denied with Settings recovery.
- Camera preparation, body guide, calibration, pose-active, ready, and visible error states.
- Lifecycle-bound CameraX preview and image analysis with keep-latest backpressure.
- Front/rear switching when both cameras are available.
- ML Kit on-device 33-landmark stream analysis mapped into domain-owned pose models.
- Frame timestamp, confidence, body visibility, orientation, smoothing, luminance, and processing-time reporting.
- Lightweight mirrored/rotated/aspect-filled skeleton overlay.
- Stable-hold calibration engine; no exercise classification or repetition counting.
- App-background pause/recovery and reduced-motion detection.
- Debug-only destination with permission, pose, confidence, calibration, processing, session, and terminal-state simulation.
- No video, image, or camera-frame storage/upload code.

## Tests added

11 new tests cover verification state transitions, start, completion, failure, cancellation, recovery, quest integration, body visibility, pose confidence, and calibration. Total declared project tests: 24.

## Files added

- `feature/verification/domain/VerificationModels.kt`
- `feature/verification/domain/VerificationUseCases.kt`
- `feature/verification/domain/CalibrationEngine.kt`
- `feature/verification/data/VerificationPersistence.kt`
- `feature/verification/camera/MlKitPoseAnalyzer.kt`
- `feature/verification/camera/CameraVerificationViewModel.kt`
- `feature/verification/camera/CameraVerificationScreen.kt`
- `feature/verification/VerificationEngineTest.kt`
- `docs/phase-2.1-audit.md`
- `docs/phase-2.1-status.md`

## Files modified

- dependency catalog and app build configuration;
- manifest camera permission/optional feature;
- core verification enum;
- Room database, migration registration, DAO provider, and repository bindings;
- navigation/dashboard debug entry.

## Known limitations

- The environment has no JDK, Gradle wrapper/executable, Android SDK, emulator/device, or adb. Android compilation, lint, unit execution, camera runtime QA, and screenshots could not be performed.
- ML Kit Pose Detection remains a beta API and adds a bundled model to APK size.
- Camera/overlay transforms need device-matrix validation across OEM preview implementations and all rotations.
- Low-light detection is a simple sampled luminance warning, not exposure metering.
- Debug terminal simulation intentionally bypasses reward use cases; production completion uses the domain use cases.
- There is no repetition counter or exercise classifier by design.

## Recommended next step

Install/attach an Android toolchain, generate the Gradle wrapper, run consumer debug compilation/tests/lint, then validate the preview on front/rear cameras in portrait and landscape. Only after that should Phase 2.2 add a squat state machine.
