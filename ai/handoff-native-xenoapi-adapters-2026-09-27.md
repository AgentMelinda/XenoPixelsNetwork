# Handoff — Native XenoAPI adapters (xenoapi.npcs.api over native Xeno NPCs)

**Date:** 2026-09-27
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571` (nothing committed or staged)
**XenoNPCsAPI checkout:** `b268e63f667bce504921de7ab75e30ff9e840e8e`, clean, not modified

Continues Codex session `01a0dfd1-0c97-7a93-ac74-c6995c348902`, which stopped mid Task 2 with
generated all-throwing stubs. Spec and plan: `docs/superpowers/specs/2026-09-27-native-xenoapi-adapters-design.md`,
`docs/superpowers/plans/2026-09-27-native-xenoapi-adapters.md`. Full detail and the capability table:
`docs/native-xenoapi-adapters.md`.

## Current state

- The tree was already heavily dirty before this work; see
  `.superpowers/sdd/2026-09-27-native-xenoapi-adapters/status-before.txt`. `npc/script/**`
  (main and test) is untracked user-owned code; the edits below are inside it.
- No Java/game process running after the runtime check (verified with a process listing).

## Changes (uncommitted)

- `build.gradle`, `settings.gradle`: **replaced Task 1's composite build + jar expansion** with
  `sourceSets.main.java.srcDir 'XenoNPCsAPI/src/main/java'`. Reason: in dev runs and unit tests the
  composite jar loaded in the `app` class loader, and API methods naming Minecraft types failed with
  `LinkageError: loader constraint violation` (observed). `settings.gradle` still fails clearly when the
  checkout is missing. Pre-change copies: `.superpowers/sdd/.../*.task2-before-sourceset`.
- New `npc/script/api/xeno/`: `XenoApiAdapters` (factory and checks), entity, living, NPC, player,
  world, item, NBT, pos, data, timers and damage-source adapters, plus `NativeNpcApi`. 298 contract
  methods are native and 104 throw `UnsupportedOperationException` naming the method.
- `NpcScriptHost`: `SharedState` + `sharedState(npc)` with pending-state adoption (single owner);
  `forget`/`clearAll` clear it; `XenoAPI` binding and `BINDINGS` entry.
- `PlayerScriptHost`: `XenoAPI` binding. `NativeXenoScriptApi`: `toXeno` / `fromXeno`
  (conversion entry points instead of ~120 overloads, which would make null calls ambiguous in Nashorn).
- `ScriptNpc`: test-seam constructor sharing timers; the test seam's `TimerView.now()` reads 0.
- `XenoPixelsMod`: one `NativeNpcApi.register()` call.
- Examples: 22 `xenopixels_*.js` unchanged; new `xenoapi_native_greeter.js`, `xenoapi_guard_patrol.js`,
  `examples/customnpcs/README.md`. Example addon: `/xenoapitest xenoapi` probe + README section.
- Tests: `XenoApiAdaptersTest` (rewritten, 11), `NpcScriptSharedStateTest` (3), `NativeNpcApiTest` (5),
  `XenoApiScriptCompatibilityTest` (7), `ExampleScriptsCompileTest` (1), `ScriptNpcTestAccess` helper;
  `BundledNashornTest` example-count check scoped to `xenopixels_*`.

## Verified

- `./gradlew test -PofflineMcMeta`: 2735 run, 2734 passed. Failure: `SceneTriggersTest.damageThatDidNotLandDoesNotFire`,
  a 700-character source-window check on untouched `XenoNpcEntity.java`. Unrelated; not fixed.
- `./gradlew build jarJar serverJar -PofflineMcMeta -x test`: passed. `./gradlew buildApiExampleAddon -PofflineMcMeta`: passed.
- Client jar 42,401,733 B, SHA-256 `6BF978462833E2D5E0072C5F10BCDA4B5B996DB23AEF9BF90811EFD9565CCE3C`;
  server jar 18,413,171 B, SHA-256 `F6148F1A6F2B250C66A7B123D4A09297747EBCE1E4611712DCEE6B45DA6CD87D`.
  Each jar has 217 `xenoapi` classes and 17 adapter classes, 0 duplicate paths; the server jar has 0 `META-INF/jarjar/` files.
- Fresh `runApiTestClient`, `run/logs/latest.log`: `20:33:43.031` native implementation registered;
  `20:33:43.310` example addon `XenoAPI available=true`; `20:33:51.092` sound engine started. No XenoAPI errors.

## Not verified

- Anything needing a player in a world: `spawnNPC`, speech, targeting, navigation, a XenoAPI timer
  firing the `timer` hook, `fromXeno` extension calls, both `xenoapi_*.js` examples, `/xenoapitest xenoapi`.
- Dedicated server startup with this build; multiplayer.
- Typed XenoAPI gameplay events: not posted (by design for this slice).

## Next steps

1. In game: place a native NPC, paste `xenoapi_native_greeter.js`, right-click, then run `/xenoapitest xenoapi`
   nearby; record the log lines.
2. `./gradlew runServer` (or the repo's dedicated-server check) to confirm server-side startup.
3. Owner decision: fix `SceneTriggersTest`'s source window separately.
4. Next slice: post typed XenoAPI events from native hooks, then widen coverage (dialogs, factions, IBlock).
