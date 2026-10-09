# Handoff — native Xeno NPC scenes

**Date:** 2026-09-23  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1` tracking `origin/1.21.1` (0 ahead, 0 behind locally)  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- No commit, tag, push, stage, or history rewrite. The tree had 647 dirty/untracked paths at the
  start of this continuation and 650 after writing this handoff. Preserve all pre-existing work.
- The `runApiTestClient` process started for this slice was stopped after the proof; PID 85900 is
  no longer running. A separate `runClient` Gradle wrapper, PID 83416, started from
  `C:\XenoPixelsNetwork_qwen` after this build; it is a different checkout and was not used as
  evidence. Do not claim that process is running these classes.
- DragonMineZ jar SHA-256:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
  MyNPCs 1.5.0 reference jar SHA-256:
  `6bbfc44e883e197719c061ae50d1a94d565141acadfe5312524b4d1f3e0a1aaf`.

## Changes

- Appended `SCENES` after `PLAYERDATA` in the world-store category enum to preserve every prior
  wire ordinal. No packet was added to the sequential `ModNetwork`, whose protocol remains 86.
- Added bounded scene definitions and a shared world-store reader under `npc/scene/`. Up to 32
  speech/clip steps over 1,200 ticks are stored per scene. Malformed editor payloads are refused;
  malformed hand-edited rows are skipped when loading.
- Added a transient server playback clock and operator `/xenoscene` commands: create, say, clip,
  show, list, remove, delete, start, stop, pause, resume, time and reset. The NPC editor now selects
  an existing scene in Advanced > Scenes and saves its `SceneId` reference.
- Suppressed ambient speech and social gestures while a scene is running. Fixed the existing editor
  save-path bug where clearing Bank or Transport selection omitted the empty replacement value;
  Scene uses the same explicit-clear path.
- Increased the synced store-index cap to cover all categories and reject malformed counts instead
  of leaving unread bytes in a packet. Updated the parity inventory, scene guide and external
  interactive status map at `C:\Users\Admin\Downloads\xeno-npc-parity-map.html`.
- Added model/store tests and an explicit-reference-clear test. All modifications are in the working
  tree only, including previously untracked native NPC source files.

## Verified

- `gradlew.bat compileJava -PofflineMcMeta`: success.
- `gradlew.bat test -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTest --tests net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStoreTest`: success.
- `gradlew.bat test -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTest --tests net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePolicyTest --tests net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStoreTest`: success.
- `gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`: success; 2,180
  tests, zero failures, errors or skips.
- Server jar `build/libs/xenopixelsmod-Server-0.3.7-1.21.1.jar`: 14,764,667 bytes,
  SHA-256 `86f4f90b89f93ed2a5e6bd228f07fc0ec766c295cf755233948a832e69e0e8d4`, zero
  entries below `META-INF/jarjar/`.
- `gradlew.bat runApiTestClient`: fresh 2026-09-23 log recorded mod protocol 86 at 19:26:29,
  API addon load at 19:26:29, integrated server startup at 19:26:40, NPC store opening at
  19:26:42, player join at 19:26:48 and addon ping/pong at 19:26:50. The long-running task was
  interrupted after those observations; its exit 1 is from that interruption.

## Not verified

- Scene command registration, authoring, playback, editor assignment, clip display, pause/seek and
  save/reload in game. No scene was started in the fresh client.
- Dedicated-server startup and multiplayer behavior for Scenes.
- Packet size at the maximum allowed store occupancy; the scene category increased the index
  envelope, and a high-cardinality world still needs a network boundary check.
- MyNPCs' full enabled/disabled scene rows and direct timeline editor; the current authoring path
  is operator commands plus an NPC assignment picker.
- The fresh log included a Controlify mixin error, Curios missing CustomNPCs entity registrations,
  and an external connection timeout. Startup and packet round trip continued. Their relationship
  to this slice is not established; do not label them scene defects without a focused reproduction.

## Next steps

1. In a fresh client, create a scene with `/xenoscene`, assign it to a native NPC, run it and check
   speech/clip timing plus pause, seek, reset, save/reload and error replies. Inspect that run's log.
2. Run a dedicated server with the same scene and check a remote client sees each step.
3. Complete the reference's enabled/disabled controls and a direct scene timeline editor, then
   continue the remaining core jobs and roles in the parity inventory.
