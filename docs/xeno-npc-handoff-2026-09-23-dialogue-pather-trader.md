# Handoff — native NPC Global Dialogues, Pather, Trader and preview Aura

**Date:** 2026-09-23  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1` tracking `origin/1.21.1` (0 ahead, 0 behind)  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- No commit, tag, push, stage, or history rewrite. The tree had 650 dirty or untracked paths at
  the start and 653 after this handoff; preserve all pre-existing work. Most NPC source files in
  this slice were already untracked before this turn.
- No Java process from this checkout remained after validation. A separate `runClient` wrapper
  from `C:\XenoPixelsNetwork_qwen` was observed; it does not prove this checkout's code.
- DragonMineZ 2.1.3 jar SHA-256:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
- MyNPCs 1.5.0 reference jar SHA-256 from prior inspected handoff:
  `6bbfc44e883e197719c061ae50d1a94d565141acadfe5312524b4d1f3e0a1aaf`.

## Changes

- The NPC consumes Pather right-click before vanilla reaches the item's handler. The NPC now binds
  the tool from either hand before wand, trade or speech handling. The tool still checks permission
  and writes the binding on the server.
- Preview Aura no longer depends on the NPC's saved aura setting.
- Global Dialogues now list categories, fetch complete entries for editing, save the draft with a
  revision check, preserve node palettes, and refresh the open index after a server write. Two
  operator-only request/response packets were appended to the main channel; protocol is now 87.
- NPC dialogue resolution uses the first valid assigned slot's exact category and ID after an
  NPC-owned dialogue and before the role default. Open and option processing use the same resolver.
  Clearing the last slot or trade sends an explicit empty list.
- Trader offers are invalidated after an accepted stock edit or linked propagation. The editor
  diagnoses incomplete and unknown item IDs and warns when the NPC role is not Trader.
- Added focused tests for category collisions, clear operations, Pather ordering, preview Aura,
  node palette preservation, and an actual `minecraft:stone` merchant result. Updated the parity
  inventory and `C:\Users\Admin\Downloads\xeno-npc-parity-map.html`.
- No published `api/**` signature changed. No world-store category ordinal or existing packet ID
  changed.

## Verified

- `gradlew.bat compileJava -PofflineMcMeta`: success.
- Focused dialogue, Pather, Trader, preview and save-policy tests: success.
- `gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`: success;
  2,187 tests, zero failures, errors or skips.
- Server jar `build/libs/xenopixelsmod-Server-0.3.7-1.21.1.jar`: 14,773,710 bytes,
  SHA-256 `585f7fcf43be7a58eca997d1f0e672cad6277f9f3c269af54e0c60cd8b90ae6c`,
  zero entries under `META-INF/jarjar/`.
- Client jar `build/libs/xenopixelsmod-0.3.7-1.21.1.jar`: 38,920,937 bytes,
  SHA-256 `64670af935e7ddc6e64376d48f1c9f8a10096bb80b614b617a1d418e4628d4a1`.
- Standalone API example jar `examples/xenopixels-api-addon/build/libs/xenopixels-api-addon-1.0.0.jar`:
  11,796 bytes, SHA-256 `1342c1b482895c96326376c8d435153fa781ea9fe3e465a3b05ba6816fe9f745`.
- `gradlew.bat runApiTestClient`: a fresh 2026-09-23 client logged 61 packets on protocol 87 at
  20:08:05, loaded the API example at 20:08:06, opened the NPC store at 20:08:19, and completed
  API ping/pong at 20:08:27. The run was stopped after proof.
- `gradlew.bat runServer` reached protocol registration and opened the NPC store at 20:12:16,
  then Sable failed to load its Rapier native DLL with `AccessDeniedException`. The Gradle task
  exited 0 despite the server crash; dedicated-server gameplay was **not** verified.

## Not verified

- The reported Global Dialogue create/edit/link/click/option flow, including concurrent edits,
  multiplayer, and a server restart. Build and startup are not proof of interaction behavior.
- The Pather binding confirmation and waypoint placement by an actual right-click.
- A Trader's configured stone offer appearing in the shop and completing a purchase.
- Preview Aura visibly rendering when the NPC's in-world aura is off.
- The remaining unavailable editor controls. Global Quests, Recipes, Natural Spawns, Global Linked
  NPCs, player data administration, many appearance/stats/AI/inventory controls, and reserved jobs
  still need native schema and runtime consumers. Do not mark them complete from this slice.
- Dedicated-server startup beyond the Sable native failure; do not attribute that crash to this
  slice without reproducing it independently.

## Next steps

1. In a fresh client, exercise the four reported flows and inspect its current log. Rebuild first
   if any code changes; an older running process is not evidence for new classes.
2. Diagnose any interaction failure from current logs and exact server state. In particular, check
   Global Dialogue save acknowledgment and assigned-slot resolution after world reload.
3. Continue the remaining unavailable controls by data owner, enabling each only when its server
   validation, persistence and runtime reader are present. Start with Global Quests and player
   administration, then inventory/AI/appearance, linked definitions, Recipes and Natural Spawns.
4. Retry dedicated startup only after the Sable native file-access condition is resolved. Leave
   unrelated native files and existing dirty paths intact.
