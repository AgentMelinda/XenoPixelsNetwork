# Native NPC script path pauses

The [CustomNPCs 1.18.2 INPCAi contract](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/entity/data/INPCAi.html) exposes `setMovingPathType(type, pauses)` and `getMovingPathPauses()`.

Native Xeno NPCs persist the pause flag inside their existing path data. Enabling it makes the patrol stop for 20 ticks at each reached waypoint before advancing. This duration is a native adaptation; the API does not specify an exact dwell duration. Failed or timed-out waypoints advance without a pause. Combat interruption resets the route's transient dwell when the patrol resumes. Older saved paths omit the flag and continue without pauses.

```javascript
npc.getAi().setMovingPathType(1, true); // Backtracking with waypoint pauses.
```

The native route also supports mode 2 (one pass), an extension beyond the documented looping/backtracking pair. Changing the mode does not erase path points or change walking speed.

`getNavigationType()` reports the actual native navigator: ground 0, flying 1, swimming 2. `setNavigationType()` accepts the current mode; changing to a navigator unsupported by the native entity throws a named unsupported-operation error. Enabling the DMZ flight skill is not reported as changing the native path navigator.

Standing rotation policies and stop-on-interaction remain unsupported. A dialog-lifetime stop requires authoritative open/close tracking, including Escape and disconnected players; retaining a cached dialog tree does not prove that its screen remains open.

Focused tests cover persistence, copy independence, old-save defaults, dwell completion, and failed-waypoint behavior. In-game patrol behavior has not been verified in a fresh process.
