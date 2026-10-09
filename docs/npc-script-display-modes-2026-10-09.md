# Native NPC display modes

Implementation and validation date: 2026-10-09.

The typed NPC adapter now implements the documented name and boss-bar modes and dead-body
visibility using persisted native profile settings. Existing saves default to visible names,
visible dead bodies, and the original boss-bar enabled flag.

```javascript
function init(event) {
    var npc = event.npc.getAPI();
    npc.getDisplay().setShowName(2);
    npc.getDisplay().setBossbar(2);
    npc.getStats().setHideDeadBody(true);
}
```

`setShowName`: 0 displays the name, 1 hides it, 2 displays it while the NPC has an attack
target. `setBossbar`: 0 disables the bar, 1 displays the enabled bar, 2 displays it while
the NPC has an attack target. Dead NPCs never display the boss bar. `setHideDeadBody(true)`
skips all native model and nameplate rendering after death; it does not change death hooks,
respawn timers or clone spawning.

The profile fields `DisplayShowName`, `BossBarMode` and `HideDeadBody` are serialized and
included in existing appearance synchronization. The server derives conditional name
visibility and synchronizes the vanilla entity flag. Editor save validation checks integer
mode ranges and the real boolean wire type. No new network packet or packet ID is added.

Focused persistence/synchronization/save-policy tests and the full 3,649-test suite passed.
A fresh client entered a world and completed the API packet round trip; actual conditional
nameplate, boss-bar and dead-body rendering remain unverified in gameplay.

Contracts: [INPCDisplay](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/entity/data/INPCDisplay.html)
and [INPCStats](https://www.kodevelopment.nl/customnpcs/api/1.18.2/noppes/npcs/api/entity/data/INPCStats.html).
