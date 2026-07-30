# Room v1 → v2 migration design

## Additive tables

```sql
CREATE TABLE assessment_drafts (
  id INTEGER NOT NULL PRIMARY KEY,
  payload_json TEXT NOT NULL,
  current_step TEXT NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE personal_protocols (
  id TEXT NOT NULL PRIMARY KEY,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL,
  protocol_version INTEGER NOT NULL,
  contract_version INTEGER,
  activated_at_epoch_ms INTEGER
);

CREATE TABLE app_metadata (
  singleton_id INTEGER NOT NULL PRIMARY KEY,
  assessment_completed INTEGER NOT NULL,
  assessment_version INTEGER NOT NULL,
  protocol_activated INTEGER NOT NULL,
  migrated_from_v1 INTEGER NOT NULL
);
```

`MIGRATION_1_2` creates all three tables and inserts metadata `(1, false, 0, false, true)`. Existing `quests`, `app_restrictions`, `override_logs`, and `system_state` are untouched.

Fresh v2 databases receive `(1, false, 0, false, false)` through a Room callback/repository initializer.

## Conflict behavior

Protocol generation produces recommendations. During approval, an existing `app_restrictions` row or user schedule is preserved and presented as `EXISTING` versus `RECOMMENDED`; only an explicit user choice is applied. Activation is transactional in a future device-policy layer and never grants new Android privileges.

## Serialization

Phase 1.5 stores assessment/protocol payloads as versioned JSON to avoid a wide, fragile schema while the questionnaire stabilizes. Status/version/index fields remain queryable. The repository owns serialization and rejects unknown schema versions.
