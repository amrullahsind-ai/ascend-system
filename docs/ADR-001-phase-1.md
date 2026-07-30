# ADR-001 — Phase 1 architecture

Status: accepted, 2026-07-28.

## Decision

Use a single Android application module for the first compilable vertical slice, with strict package boundaries matching the requested future modules. UI is Compose/MVI-like immutable state; use cases depend on repository interfaces; Room and DataStore are local sources; device actions are behind policy interfaces. Hilt wires production implementations. The deterministic `RuleEngine` and `SafetyEngine` are the only path to restrictions.

The project exposes `consumer` and `dedicated` product flavors. Phase 1 implements consumer education and simulated blocking only. The dedicated flavor is a clearly labelled placeholder; it does not claim Device Owner privileges.

## Consequences

- Package boundaries can become Gradle modules after the domain stabilizes.
- The app works without cloud AI through `FakeAiProvider`.
- Consumer blocking is best-effort and always exposes Emergency Override.
- No Accessibility Service, camera, location, Health Connect, or Device Owner behavior is included in Phase 1.
