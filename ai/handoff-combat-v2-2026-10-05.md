# Handoff — XenoCombat v2 (first pass)

> Superseded by `ai/handoff-combat-v2-2026-10-06.md`. Kept as dated context: the prompt atlas tool,
> the artifact hashes and several behaviours described below no longer exist in the tree.

**Date:** 2026-10-05
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `e011e7bec8061d8c2228cfbdc63ee57468749d0d` (nothing committed; all v2 work is in the working tree)

## Current state

- The tree was already dirty before this work (LinearReader conversion files, `gradle.properties`,
  `plan.md`, `tools/gen_bt3_hud_atlas.py`, `tools/generated/xeno_bt3_hud_atlas.json`,
  `docs/dimension-protection.md`, `scripts/mirror_mynpcs_form_studio.py`, the
  `tools/dmz-hair-builder-site` submodule). None of those were touched.
- No game process was started by this work.
- `gradle.properties` already said version `0.5.11-1.21.1`; artifacts below carry that name.

## Changes

Design: `docs/superpowers/specs/2026-10-05-combat-v2-design.md`. Player-facing: `docs/combat-v2.md`.

New (all untracked):

- `combat/v2/**` — server and common: `V2CombatServer` (entry, tick, sync), `V2Fighter` +
  `V2FighterStore` (one state object per player), `V2Strikes` (combo walker), `V2Motion` (travel
  and step), `V2Moves` (chase, homing, Dragon Dash, counter), `V2Rush`, `V2Grab`, `V2Config`,
  and the Minecraft-free rules: `V2CombatGate`, `HitReaction`, `CounterRules`,
  `combo/ComboGraph|ComboNode|ComboMachine|ComboGraphParser|ComboGraphs`, `grab/GrabRules`,
  `motion/MotionRules`.
- `client/combat/v2/**` — `V2InputLayer`, `CombatStance`, `V2Keys`, `V2ClientState`,
  `CombatPromptOverlay`, generated `XenoCombatPromptAtlas`.
- `client/keybind/**` + `client/command/XenoKeybindCommands` — `/xenokeybind`.
- `network/packet/CombatV2InputPacket`, `CombatV2StatePacket`; `api/event/GrabEvent`.
- `data/xenopixelsmod/combat_v2/combo_graph.json`; `textures/gui/xeno_combat_prompt_atlas.png`.
- `tools/gen_combat_prompt_atlas.py` with its manifest and contact sheet under `tools/generated/`.
- Tests: `combat/v2/ComboGraphTest` (18), `combat/v2/V2RulesTest` (19),
  `client/keybind/KeybindRulesTest` (7), `network/CombatV2ProtocolTest` (3).

Edited existing files (each a small seam):

- `combat/controller/CombatControllerMode` (+`V2`), `LegacyActionPolicy` (v2 refusal set),
  `CombatControllerService` (sweep clears v2).
- `network/ModNetwork` — two packets appended, `PROTOCOL` 101 → 102.
- `network/Bt3CombatPacket` — one guard: legacy actions are refused while v2 has the player in a
  grab or a rush.
- `combat/technique/XenoRushTechniqueEvents` — under v2 the hit's movement is a v2 reaction.
- `client/combat/Bt3CombatClient` — v2 branch in the tick, v2 claim on the fists,
  `findLookTarget` made public, sway / Z-Burst / ki-cancel keys stand down under v2.
- `client/XenoServerClientState` (+`v2Controller()`), `client/config/XenoClientConfig`
  (3 keybind settings), `client/XenoHudRegistration` (prompt layer), `command/XenoPermissions`
  (+`combat.v2.use`), `command/XenoCombatCommands` (reload also reloads v2),
  `config/XenoServerConfigKeys` (help text), `lang/en_us.json` (8 keys), `CHANGELOG.md`.

Compatibility: `api/**` change is additive (`GrabEvent`). Protocol 102 requires matching client and
server. Default mode is still `legacy`.

Differences from the approved design:

1. v2 tuning is its own file, `config/xenopixelsmod-combat-v2.json`, not new keys in
   `XenoServerConfig`. Adding ~50 fields there would also have changed `SyncServerConfigPacket`.
2. `V2Motion` does not reuse `ChaseRouting` (it is package-private in `network`); it climbs over an
   obstacle or stops. It also does not grant DragonMineZ's Fly skill during a travel.
3. No grab/throw animation clips were authored and `Bt3AnimationIntent` is unchanged; the grab
   reuses `STEP_IN_DASH`.
4. No Superdesign mock was generated. The prompt art was built straight from the UI bundle as
   later instructed, and checked with a locally rendered layout preview.
5. No new characterisation tests for v1 were added; the existing suite is the v1 baseline.
6. The task-level plan under `docs/superpowers/plans/` was not written.
7. The stance is narrower than "fists out": empty hands plus a lock, a target under the crosshair,
   or recent combat. Empty hands alone would have made E unable to open the inventory.

## Verified

- Baseline before any change: `./gradlew test --offline -PofflineMcMeta` → BUILD SUCCESSFUL.
- After: `./gradlew test build jarJar serverJar -PofflineMcMeta --offline` → BUILD SUCCESSFUL;
  3284 tests, 0 failures, 0 errors (47 of them new).
- `./gradlew buildApiExampleAddon -PofflineMcMeta --offline` → BUILD SUCCESSFUL.
- `python tools/gen_combat_prompt_atlas.py --check` → up to date (3 sprites, 128 square).
- `build/libs/xenopixelsmod-0.5.11-1.21.1.jar` — 69,395,685 bytes,
  sha256 `6f09e7bd731970f0459bab6088f8a59a79e10d3c10981b8162ea6c57f39ef1b1`.
- `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar` — 45,248,836 bytes,
  sha256 `af3288e3b3006fdcb09796a6335ce274608ab61f749d933591a28e1c6c63600b`.
  `META-INF/jarjar/` holds only `metadata.json` and `aaa_particles-neoforge-1.21.1-2.3.1.jar`.

These are build and unit-test results only.

## Not verified

- Nothing was run in game: no client, no dedicated server, no multiplayer. Every behaviour in
  `docs/combat-v2.md` is unobserved, including that v1 is unchanged in `legacy` mode at runtime.
- Dedicated-server start with the new classes (the server was not started, to avoid touching the
  dev world while LinearReader conversion changes are uncommitted in the tree).
- Whether DragonMineZ fires technique slots bound to Ctrl/Alt + a mouse button.
- Whether the shared-key suppression (E, Q, V, right mouse, wheel) wins against DragonMineZ's own
  handlers in every case.
- Tick cost. No profiler capture exists; no TPS claim.
- Gamepad: no v2 rows in `client/pad` or `client/pad2`.

## Next steps

1. Start a client against a throwaway world, `/xenocombat mode v2`, and walk the "Controls" table
   in `docs/combat-v2.md`; then `/xenocombat mode legacy` mid-fight and confirm nothing is stuck.
2. Start a dedicated server and read a fresh `run/logs/latest.log` for
   `ModNetwork: registered ... (protocol 102)` and no class-loading errors.
3. Decide on the flight pose during travel (grant Fly as v1 does, or a v2 clip).
4. Author grab and throw clips through `tools/make_bt3_animations.py`; append the intents.
5. Add local pose prediction if the one-round-trip delay on strikes is noticeable.
6. Later passes from the design doc: Just Guard, stamina break, guard and vanish in v2, gamepad.
