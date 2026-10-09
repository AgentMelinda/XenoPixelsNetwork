# Handoff for Claude — native GeckoLib NPC combo clips

**Date:** 2026-09-27  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- `git status --short` produced 756 lines at the start of handoff review and 758 lines at the last check (2026-09-27 18:17 Asia/Jerusalem). The tree was already heavily dirty before this task and appears to be changing; preserve all existing content and recount status before editing. Do not attribute all dirty content to the animation change. No commit, tag, push, or staging was done.
- Relevant implementation and task files are `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcDmzAnim.java`, `NpcMeleeDamage.java`, `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcGeoModel.java`, `XenoNpcGeoRenderer.java`, `src/main/java/net/bullettrain/xenopixelsmod/npc/XenoNpcEntity.java`, `src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcAnimationClient.java`, `src/main/java/net/bullettrain/xenopixelsmod/api/anim/XenoAnimApi.java`, `examples/customnpcs/xenopixels_combo_rush.js`, `docs/native-mynpcs-script-compat.md`, and `src/test/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcGeoModelTest.java`.
- The 2026-09-27 16:38 client log showed `combat.xeno_*` packets queued with no corresponding Full renderer drain during the user's combo. The native GeckoLib renderer had no packet drain.
- The pinned `libs/dragonminez-2.1.3.jar` SHA-256 is `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`, matching `gradle.properties`. Its Master Gohan geometry and texture exist; `master_gohan.animation.json` contains only `idle` and `walk`. The Xeno combat file names matching arm and leg bones.
- `C:\XenoPixelsNetwork_qwen` is a symbolic link to this worktree. A user-launched `runClient` through that link was observed at 17:54 on 2026-09-27; it was no longer running at the latest process check. I did not stop it. `run/logs/latest.log` was last modified at 18:14.

## Changes

- Native GeckoLib Xeno NPCs now qualify for Xeno combat packets. Their renderer drains packets into the GeckoLib attack controller, and their model searches the Xeno combat animation file after its own animation file. Studio clips remain available as an additional fallback.
- The explicit melee animation chosen by a script takes priority over the attack-page cycle. Duplicate calls to play the same clip on one entity in one game tick no longer restart the pose.
- Updated the published animation API description, `docs/native-mynpcs-script-compat.md`, and the `examples/customnpcs/xenopixels_combo_rush.js` guidance. Added a focused Master Gohan resource-path test.
- No network protocol or persisted schema changed. No commit was made.

## Verified

- `./gradlew compileJava -PofflineMcMeta`: passed.
- Focused `./gradlew test --tests net.bullettrain.xenopixelsmod.client.npc.XenoNpcGeoModelTest --tests net.bullettrain.xenopixelsmod.client.compat.npc.NpcAnimationClientTest --tests net.bullettrain.xenopixelsmod.npc.NpcAttackClipSelectorTest --tests net.bullettrain.xenopixelsmod.api.anim.XenoAnimApiTest -PofflineMcMeta`: passed after the final controller change.
- `./gradlew test -PofflineMcMeta`: 2708 tests, 1 failure at `SceneTriggersTest.damageThatDidNotLandDoesNotFire`, the same failure seen before this change. The exact `build jarJar serverJar -PofflineMcMeta` run produced jars, then failed on that test.
- `./gradlew buildApiExampleAddon -PofflineMcMeta`: passed.
- `./gradlew runApiTestClient -PofflineMcMeta`: passed after the final code change. The fresh 2026-09-27 17:07 log shows protocol 98, Nashorn NPC scripts, 19 animation clips received, v27 scripts loaded, and the player joining singleplayer. The process exited after normal world save.
- A later user-launched `runClient` log from 17:54 through 18:14 shows script startup and XenoPixels v27. At 17:59:27–17:59:32, the combo's `combat.xeno_jab_left_v3`, `jab_right_v3`, `body_punch_left_v3`, `cross_right_v3`, `hook_left_v3`, `knee_right_v3`, `uppercut_right_v3`, `spin_kick_right_v3`, `flying_kick_v4`, and `heavy_finish_v3` each logged as queued and drained by the Gecko renderer. This proves Gecko renderer packet consumption in that session. The log does not identify which NPC consumed each packet or prove visible motion or texture on screen; some other clips also logged a Full-renderer drain.
- `build/libs/xenopixelsmod-0.5.0-1.21.1.jar`: 42,204,869 bytes, SHA-256 `a29f6d29325abaddb23f7c48b657864f8db23412ffe78ca88d18c0d888709aae`.
- `build/libs/xenopixelsmod-Server-0.5.0-1.21.1.jar`: 18,216,307 bytes, SHA-256 `9c8d96932e927cd34cb9dd2d10403e455df816eec412ffb2a900175e2084ffb9`; zero entries below `META-INF/jarjar/`.

## Not verified

- The log does not establish that the Gecko-drained packets belonged to the configured Master Gohan NPC. Visible limb motion and texture appearance during its hits, and multiplayer playback, remain unverified.
- The extra mixin warnings and the unrelated `SceneTriggersTest` failure were not changed.

## Next steps

1. Check with the user whether the 17:59 run was the Master Gohan NPC and whether its limbs and texture appeared correctly. Do not infer this from renderer drain lines alone.
2. If it was another Gecko NPC, reproduce on Master Gohan and compare the fresh log and visible pose. The log file is generated under `run/`; inspect it after the user run rather than editing it.
3. If the clips drain but the pose is poor, adjust the authored clip for Master Gohan's geometry pivots; matching bone names do not prove visual quality.
4. Keep the `SceneTriggersTest` failure separate from the animation work.
