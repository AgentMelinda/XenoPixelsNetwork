# Handoff — Xeno NPC parity baseline and Advanced Lines

**Date:** 2026-09-23  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- No commit, tag, push, stage, or history rewrite was made. The working tree had 643 dirty or
  untracked paths before this slice and 646 before writing this handoff. Existing changes belong to
  the user. Paths touched here: `ai/repo-facts.md`, `ai/skills/networking.md`,
  `docs/xeno-npc-mynpcs-feature-boundary.md`, `docs/xeno-npc-parity-2026-09-23.md`,
  `src/main/java/net/bullettrain/xenopixelsmod/npc/store/XenoNpcStoreCategory.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorScreen.java`, and
  `src/test/java/net/bullettrain/xenopixelsmod/npc/store/XenoNpcWorldStoreTest.java`.
- Three Java processes (PIDs 16940, 69056, 76216) started before the latest rebuild. They cannot
  prove current gameplay behavior.
- DragonMineZ jar SHA-256:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
  MyNPCs 1.5.0 reference jar SHA-256:
  `6bbfc44e883e197719c061ae50d1a94d565141acadfe5312524b4d1f3e0a1aaf`.

## Changes

- Corrected the world-store runtime-reader inventory: Dialogs, Factions, Banks and Transport are
  live; Clones is reserved. Added a focused assertion.
- Routed five Advanced Lines rows to the existing native line editor. NPC-to-NPC interaction lines
  remain disabled because no Conversation job consumes them.
- Corrected the documented mod version and main protocol, marked the older parity boundary as a
  historical snapshot, and added a source-based status inventory.
- Created and opened an interactive visual status map at
  `C:\Users\Admin\Downloads\xeno-npc-parity-map.html`.

## Verified

- `node ./scripts/audit-project.mjs <repository>`: 0 failures, 6 warnings; warnings concern
  optional-mod symbols, ship integration and reflection review.
- `gradlew.bat test -PofflineMcMeta --rerun-tasks`: success before this slice, 2,176 tests.
- `gradlew.bat test -PofflineMcMeta --tests
  net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStoreTest`: success after the store change.
- `gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`: success after all
  code changes; 2,177 tests, zero failures, errors or skips.
- Server jar: `xenopixelsmod-Server-0.3.7-1.21.1.jar`, 14,746,101 bytes,
  SHA-256 `5e99843a31796bc9c142e3d6a142856863c4aebdcdcd70f88a79794cb1db5aad`, zero entries
  under `META-INF/jarjar/`.
- All 209 repository Markdown files were readable. The NPC-specific guidance, MyNPCs catalogs,
  current plans and newest handoff received targeted review.

## Not verified

- The Advanced Lines page and every other changed feature in a freshly started client.
- MyNPCs-only, CustomNPCs-only, neither-mod, both-mod, and multiplayer gameplay matrices for the
  current build. No runtime claim follows from compilation or tests.
- The audit warnings are not proven defects; exact optional-mod symbols and fail-closed behavior
  still need focused review.

## Next steps

1. Restart the old client processes or use an isolated run directory, exercise Advanced Lines and
   existing Bank, Guard, Pather, and dialogue paths, and inspect fresh logs.
2. Implement Scenes as a complete native feature: bounded world-store definition, server clock,
   commands, NPC assignment and editor. Append any category after `PLAYERDATA` to preserve wire
   ordinals.
3. Continue the remaining core roles, jobs and global editors in dependency order. Keep
   conditional integrations and MyNPCs WIP/broken surfaces outside the core parity claim.
