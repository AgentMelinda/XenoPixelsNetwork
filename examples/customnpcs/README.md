# Script examples

Paste a file into an NPC's Scripts tab (enable it and save). Every file here compiles in the
bundled sandboxed engine; `./gradlew test` checks that (`BundledNashornTest`,
`ExampleScriptsCompileTest`). Compiling is not proof of in-game behaviour.

## Cross-runtime examples (`xenopixels_*.js`)

These 22 scripts run on CustomNPCs, My NPCs and native Xeno NPCs. They use `event.npc`,
`event.player` and the `XenoPixels` binding only, so they were left unchanged when XenoAPI
arrived on 2026-09-27: `XenoAPI` is bound only for native Xeno NPCs and player scripts.

## Native XenoAPI examples (`xenoapi_*.js`)

| File | Shows |
| --- | --- |
| `xenoapi_native_greeter.js` | The greeter written against typed XenoAPI: `XenoPixels.toXeno(event.npc)`, stored data, `sayTo`, timers firing the `timer` hook, home position, and `XenoPixels.fromXeno` to reach XenoPixels extras. |
| `xenoapi_guard_patrol.js` | `IWorld.getClosestEntity`, `setAttackTarget`, `navigateTo`/`isNavigating`, and `IPos` distance to patrol around home. |
| `xenoapi_npc_settings.js` | Every editor and DMZ setting from a script: `setSkill`, Ki Sense (`setKiSense`, `setKiSenseLockOn`, `isKiSenseLockedOn`), `setDmz`/`getDmz`/`toggleDmz`/`listDmz` by setting key, and native identity (`setTitle`, `setLeashRadius`, `setRespawn`). |
| `xenoapi_forge_events.js` | A Forge script tab (scripter tool, right-click the air → Forge Scripts): cancel a block break, scale damage in `livingHurt`, announce kills, cancel explosions. |
| `xenoapi_player_bubbles.js` | Player script: `XenoPixels.bubble(player, text[, color[, shape]])` speech bubbles over players (join, login, every chat line, death, level up) next to `XenoPixels.say` private lines. |
| `xenoapi_player_events.js` | Player script (Global → Player Scripts): typed `broken`, `damaged` (damage cap), `toss` (cancel returns the item), `levelUp`, player timers, and `event.xeno` on `login`. |

The greeter, patrol and settings scripts need a native Xeno NPC; `xenoapi_forge_events.js` is a Forge tab and `xenoapi_player_events.js` a player script. When `XenoAPI` is
absent, the greeter says so and the others do nothing. Unsupported XenoAPI methods throw an error naming themselves; the full list is in `docs/native-xenoapi-adapters.md`.
