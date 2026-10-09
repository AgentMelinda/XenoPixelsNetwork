# Handoff — XenoAPI parity sub-project 1: event foundation

**Date:** 2026-09-27
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571` (nothing committed or staged; repository rule)

Spec: `docs/superpowers/specs/2026-09-27-xenoapi-event-foundation-design.md` (approved).
Plan: `docs/superpowers/plans/2026-09-27-xenoapi-event-foundation.md` (approved; native execution).
Ledger with every ruling: `.superpowers/sdd/2026-09-27-xenoapi-event-foundation/progress.md`.
Previous slice: `ai/handoff-native-xenoapi-adapters-2026-09-27.md`.

## Current state

- The tree was already dirty. Snapshots of `git status` before and after are
  `status-before.txt` and `status-after.txt` in the ledger folder.
- **A user-launched client is running** (pid 31196, started 21:08, from
  `C:\XenoPixelsNetwork_qwen`). It predates this build, so it runs the older code. It holds
  `run/` log files and Sable's native DLL.
- No processes started by this work remain.

## Changes (uncommitted)

- **Tasks 1-3, alignment with the owner's reference docs**
  (`https://www.kodevelopment.nl/customnpcs/api/1.16.5/`, a reading reference only):
  - potion flag hides particles; light is 0-1; stack size 1..max
  - unknown item ids report false / 0; creative players get no drop
  - `getOwner` returns the followed entity; `playAnimation` works (0/2/3)
  - temp and stored data for every entity, the world (`XenoWorldData` SavedData) and items
    (custom data), all with native bounds
  - item attributes and `getAttackDamage`
- **Task 4.**
  - `XenoEventBus`, a counting delegate behind `NpcAPI.events()`.
  - `XenoEventDispatch`: gate, post, cancel/damage accessors, re-entry guard.
  - `ScriptEvent.xeno` with write-through and `syncFromXeno`.
- **Task 5.** Typed `NpcEvent` at all 12 NPC hook sites. `rangedAttack` is added alongside
  `rangedLaunched`. InitEvent reaches Java for NPCs without scripts. Timers still tick for an
  NPC that has pending state but no host.
- **Task 6.** Native player timers (`PlayerScriptTimers`, saved as `XenoScriptTimers` in player
  data), `IPlayer.getTimers`, `PlayerScriptHost.hasHook/fireTyped`, and typed init, login,
  logout and chat.
- **Task 7.** `XenoBlockAdapter` and `XenoContainerAdapter`, wired into `NpcAPI.getIBlock` and
  `getIContainer`, `IWorld.getBlock`, and `IPlayer.getInventory` and `getOpenContainer`.
- **Task 8.** `PlayerXenoEvents` covers 19 player events. Specifics:
  - A cancelled toss returns the item to the inventory.
  - A cancelled death leaves health at 1.0.
  - Break `exp` is applied through `BlockDropsEvent`.
  - `pickedUp` maps cancel to `TriState`.
- **Task 9.**
  - The example addon counts typed events, and `/xenoapitest events` prints the counts. It
    registers in common setup.
  - `examples/customnpcs/xenoapi_player_events.js` shows the player events.
  - `scripts/xenoapi_capabilities.py` regenerates the capability table: 338 native,
    99 unsupported.
  - `docs/native-xenoapi-adapters.md` has a new Events section.

## Verified (2026-09-27)

- `./gradlew test -PofflineMcMeta` (after the review fix pass): 2765 run, 2764 passed. The one failure,
  `SceneTriggersTest.damageThatDidNotLandDoesNotFire`, predates this plan: it is a
  700-character source-window check.
- `./gradlew build jarJar serverJar -PofflineMcMeta -x test` exited 0.
  `./gradlew buildApiExampleAddon -PofflineMcMeta` exited 0.
- `xenopixelsmod-0.5.0-1.21.1.jar`: 42,445,935 bytes, SHA-256
  `D2AFAE9F8BD44A2098206065D45F1D0B23161A0EADDEE585A7A1BA1456EFDAE7`. One `NpcAPI.class`,
  0 duplicate paths, 3 jarjar files.
- `xenopixelsmod-Server-0.5.0-1.21.1.jar`: 18,457,373 bytes, SHA-256
  `DD9686CA031BE23BEBFF0E93AAAF0ECA9C6BB38734F51C2CF043B838A59CC1F3`. One `NpcAPI.class`,
  0 duplicate paths, **0** jarjar files.
- Fresh `runServer` at 22:17: `XenoAPI: native Xeno NPC implementation registered` and the
  addon's `XenoAPI available=true` were logged. The server then crashed creating levels:
  Sable's `AccessDeniedException` deleting `.sable/natives/sable_rapier_x86_64_windows.dll`,
  because that file is held by the running client above. The crash report is
  `run/crash-reports/crash-2026-09-27_22.17.51-server.txt`.

## Final review (fresh reviewer, 2026-09-27)

Seven findings were fixed test-first:
- toss duplication on a partial return
- stale player timers after respawn
- listener errors escaping into the tick, and log spam
- stale NPC pending state after chunk unload
- off-thread use of retained views
- duplicate `interact` on one click
- direct `event.damage` writes

Six Minor findings are deferred and listed in the ledger (`Final: minor (deferred)`).

## Not verified

- A dedicated server reaching `Done`. Blocked by the running client.
- Every in-game event: all 12 NPC and 19 player events, including the toss-return, death-cancel
  and damage-cap scripts.
- The profiler sample. No TPS claim is made.
- Multiplayer.

## Next steps

1. Close the running client. Then run `./gradlew runServer -PofflineMcMeta` and confirm `Done (`.
2. Run `./gradlew runApiTestClient -PofflineMcMeta`, join a world, and paste
   `xenoapi_player_events.js` into Player Scripts and `xenoapi_native_greeter.js` into an NPC.
   Walk the checklist in plan Task 9 Step 5, then run `/xenoapitest events`.
3. Fix or retire the stale `SceneTriggersTest` source-window check. That is an owner decision.
4. Sub-project 2 (quests, dialogues, factions, mail): brainstorm, then spec, then plan.
