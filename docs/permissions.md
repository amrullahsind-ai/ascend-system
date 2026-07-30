# Permission matrix

| Permission/access | Phase | Why | Denied fallback |
|---|---:|---|---|
| Notifications | 1 | quest and schedule reminders | in-app status cards |
| Usage access | 1 | user-configured app limits | manual focus timer; no usage claims |
| Draw over apps | 1 | optional warning panel | notification and in-app blocking screen |
| Boot completed | 1 | reschedule local work | reschedule on next launch |
| Camera | 2 | live pose verification | alternate non-camera quest |
| Location/activity | 4 | active run verification | timer/manual distance quest |
| Exact alarm | 3 | user-facing alarm | WorkManager/inexact notification |

Permissions are requested contextually after an education screen. Emergency access never depends on granting a permission.
