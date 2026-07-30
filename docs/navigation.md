# Navigation graph

```mermaid
flowchart TD
  Splash --> Onboarding
  Splash --> Dashboard
  Onboarding --> PermissionEducation --> Dashboard
  Dashboard --> QuestList --> QuestDetail
  Dashboard --> Progress
  Dashboard --> Allowlist
  Dashboard --> Schedule
  Dashboard --> Sleep
  Dashboard --> Settings
  Dashboard --> Blocking
  Blocking --> Override
  Override --> Dashboard
```
