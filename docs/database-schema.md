# Database schema

```text
quests(id PK, title, description, type, verification_type, target_value,
       reward_xp, scheduled_at_epoch_ms?, deadline_at_epoch_ms?, status,
       safety_level, created_by, updated_at_epoch_ms)

app_restrictions(package_name PK, display_name, category, daily_limit_minutes?,
                 session_limit_minutes?, is_essential, enabled)

override_logs(id PK, timestamp_epoch_ms, reason, note?, duration_minutes,
              active_quest_id?)

system_state(singleton_id PK=1, state, active_quest_id?, lock_reason?,
             updated_at_epoch_ms)
```

Schedule windows are represented by domain value objects in Phase 1. A normalized schedule table is the next migration once recurring calendar requirements are finalized.
