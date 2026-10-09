# Handoff — native MyNPCs-style scripting

**Date:** 2026-09-27  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- No commit, tag, push, reset, or cleanup was performed. The worktree was already heavily dirty at entry (about 753 `git status --short` rows); it has 755 rows at handoff. Existing changes and assets were preserved.
- The API test client was stopped after the current log showed startup, login, and the addon ping/pong. No `forgeclientdev` Java process for this worktree remained at handoff.
- `libs/dragonminez-2.1.3.jar` SHA-256: `5A6E33EF5B992E64FCCCCAED895B105318B2CE1AB8039D8193107D40D203B581`, matching `gradle.properties`. Pinned `run/mods/mynpcs-neoforge-1.5.0.jar` SHA-256: `6BBFC44E883E197719C061AE50D1A94D565141ACADFE5312524B4D1F3E0A1AAF`.

## Changes

- NPC scripts receive the native `XenoPixels` facade. The 22 unchanged `examples/customnpcs/*.js` files compile with bundled Nashorn and every `XenoPixels.*` name in those files exists on the facade. The wider facade covers MyNPCs bridge methods backed by native Xeno services; `setAimAccuracy` (stored but unused by the pinned bridge) and `setSkinUrl` (no verified native renderer path) remain absent. NPC timers now persist on the entity and dispatch `timer(event.id)`. The ki dispatcher emits guarded `rangedLaunched` hooks. Existing `kill` and dialogue hooks were confirmed and retained.
- Global player scripts have a separate `player_scripts` world-store category, editor entry, per-player scope, and `init/login/logout/chat` hooks. `ChatOnly` lets the optional chat half of `xenopixels_state_clips.js` run without its NPC `init`. Player stored script data is part of `XenoPlayerData` save/copy. Numeric imported quest slot lookup is unique-only and provenance is recorded on new imports; native player wrappers expose mapped start, finish, and stop operations through Xeno progression.
- New store category is appended after `SCRIPTS`; main protocol is `98`. `build.gradle` packages Nashorn classes directly into the server jar, keeping its `META-INF/jarjar/` empty. The client jar still uses the existing `jarJar` path.
- Documentation: `docs/native-mynpcs-script-compat.md`, `docs/xeno-npc-schema.md`, and `plan.md`.
- Code paths edited for this task: `build.gradle`; `src/main/java/net/bullettrain/xenopixelsmod/{capability/XenoPlayerData.java,compat/npc/NpcKiAttackDispatcher.java,network/ModNetwork.java,network/packet/NpcScriptPacket.java,network/packet/XenoNpcStoreWritePacket.java,npc/XenoNpcEntity.java,npc/importer/NpcImportService.java,npc/store/XenoNpcScripts.java,npc/store/XenoNpcStoreCategory.java,npc/script/NpcScriptHost.java,npc/script/NpcScriptScope.java,npc/script/PlayerScriptHost.java,npc/script/api/NativeXenoScriptApi.java,npc/script/api/ScriptApi.java,npc/script/api/ScriptEvent.java,npc/script/api/ScriptNpc.java,npc/script/api/ScriptPlayer.java,npc/script/api/ScriptTimers.java,npc/script/api/ScriptWorld.java,client/npc/XenoNpcEditorScreen.java,client/npc/XenoNpcScriptScreen.java}`; tests in `src/test/java/net/bullettrain/xenopixelsmod/npc/script/{BundledNashornTest.java,api/ScriptTimersTest.java,api/PlayerScriptDataTest.java}` and `src/test/java/net/bullettrain/xenopixelsmod/npc/store/XenoNpcScriptsTest.java`. Many of these paths were untracked before this turn; `git status --short` is the authority for the complete tree.

## Verified

- `./gradlew test --tests '*BundledNashornTest' --tests '*ScriptTimersTest' --tests '*XenoNpcScriptsTest' -PofflineMcMeta` passed. The later dedicated `PlayerScriptDataTest` and `BundledNashornTest` runs passed.
- Final `./gradlew test -PofflineMcMeta`: 2,707 tests, 1 failure in `SceneTriggersTest.damageThatDidNotLandDoesNotFire`. Its source-text assertion searches only the first 700 characters of `hurt`; the current `boolean landed = super.hurt(...)` and `if (landed...)` are beyond that window. This failure is unrelated to the script edits and was left untouched under repository validation guidance.
- `./gradlew jar jarJar serverJar buildApiExampleAddon -PofflineMcMeta` passed. The requested `build jarJar serverJar -PofflineMcMeta` was attempted but stops at the same full-suite failure; `jar` and `serverJar` did complete before that failure.
- Server artifact `build/libs/xenopixelsmod-Server-0.5.0-1.21.1.jar`: 18,213,044 bytes, SHA-256 `857FCA1FB99219336C0FB02536FA74C03E666059FC4DBDA1604152793FFEF4C6`. It has zero `META-INF/jarjar/` entries, one Nashorn factory class, and the script-engine service entry.
- Client artifact `build/libs/xenopixelsmod-0.5.0-1.21.1.jar`: 42,201,606 bytes, SHA-256 `B308F8051DAD582B285CFAA48526D0E15A7977E49CBE3491D15C0805B845240D`.
- Fresh `./gradlew runApiTestClient -PofflineMcMeta` process logged at 2026-09-27 14:34–14:35 IDT: protocol 98, `ModNetwork.script: using nashorn`, `NPC scripts: script engine: nashorn`, player login, and example addon `network.ping`/`network.pong`. The client was then manually stopped, so the Gradle task exited nonzero by interruption.

## Not verified

- Native NPC and player hook execution was not driven manually in the fresh client. The welcome chat lines in that run may come from installed MyNPCs global scripts and do not prove the new native player host.
- The standalone server jar was inspected but not started in a separate dedicated-server process; Nashorn loading from that distribution remains unverified.
- UI placement and interaction for Global → Player Scripts, multiplayer chat rewrite ordering, numeric imported-quest reward behavior, timer reload, and ranged hook behavior need direct gameplay checks.

## Next steps

1. In a fresh client, paste one unchanged NPC example into a Xeno NPC tab and one of the three player scripts into Global → Player Scripts; check the current log and visible effects for each relevant hook. For `xenopixels_state_clips.js` in the player host, enable `Chat hook only`.
2. Start the standalone server jar in a clean server process and confirm its script-engine startup log.
3. Address the existing `SceneTriggersTest` text-window assertion in its own focused task or test-only review, then rerun the full distribution command.
