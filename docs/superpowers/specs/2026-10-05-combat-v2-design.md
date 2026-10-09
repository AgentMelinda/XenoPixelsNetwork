# XenoCombat v2 — staged rewrite beside v1

## Context

Player combat today is one 1,537-line packet handler (`network/Bt3CombatPacket.java`) plus per-player
static maps spread over five classes. Each move polls its own keys, owns its own movement code and
its own cooldown map. Directional styling has decayed: `Bt3ComboChoreography.GROUND_ROUTES` is now
only `{JAB_LEFT, JAB_RIGHT}`, styling knows two directions (A = cross, D = uppercut), and the
step-3 kick launcher in `Bt3ComboBeat` can never fire because no route contains a kick. There is no
grab or throw.

Goal: keep all of that as **v1 (default, untouched)** and build **v2** from scratch beside it,
following `C:\Users\Admin\Downloads\xenoverse2_pc_combat_ki_system_design.md`: one input layer, one
per-fighter state machine, a data-driven combo graph, shared movement, plus grab/throw, a
centre-screen prompt, and a keybind system.

### Decisions already made (owner, 2026-10-05)

| Topic | Decision |
|---|---|
| Selector | Server-wide, v1 default, reuse `combatControllerMode` |
| Key clashes (Q, E, V, MMB, wheel, RMB) | Combat stance: keys are combat only while locked on / fists out in v2 |
| Keybind cleanup | Automatic (configurable) **and** a command, always with a backup |
| 4th beat after 3 lights | Player input picks the branch; the prompt shows what is open |
| Prompt art | Green, built from the existing DMZ-style atlas tooling, generated at the size it needs |

### v2 scope for this pass

Rewritten in v2: Dragon Dash, cinematic rush, the four rush strikes, chase dash / chase flight,
dragon homing, Z-Burst, sonic sway (becomes Step), super counter, directional styling, combo graph
("combo calculator"), grab/throw, prompt HUD, keybind system.

Not rewritten yet (v2 input reaches the **v1 implementation** unchanged): guard, vanish, ultimate,
sparking, Hakai, Zanzoken, Multi-Form, ki blast cancel, all ki mixins. Just Guard, stamina break and
gamepad layouts are later passes.

## Architecture

New packages: `combat/v2/**` (common, no client imports), `client/combat/v2/**`,
`client/keybind/**`. v1 files are not edited except the four seams listed under "v1 touch points".

1. **Selector** — add `V2("v2")` to `combat/controller/CombatControllerMode`. `LEGACY` stays
   `DEFAULT`. `CombatControllerService` already sweeps live state and broadcasts on any mode change;
   extend `clearLiveCombatState` to also clear v2 fighters. `LegacyActionPolicy` refuses, under V2,
   the v1 actions v2 owns (`COMBO_HIT`, `CINEMATIC_RUSH`, `CHARGE_FIST`, `CHARGE_KICK`,
   `DRAGON_DASH`, `CHASE_DASH`, `CHASE_STOP`, `Z_BURST`, `RUSH_CHAIN`, `SONIC_SWAY`,
   `SUPER_COUNTER`). New `V2CombatGate` mirrors `ManualCombatGate` (mode, `bt3CombatEnabled`,
   permission) and every v2 handler asks it first. `bt3_manual` is left as is (it has no handler).
2. **Wire** — two packets appended at the end of `ModNetwork` (never inserted), `PROTOCOL` 101 → 102:
   - `CombatV2InputPacket` C2S: input ordinal, target entity id, direction byte, client sequence.
     Intent only; server re-derives range, cost, step and timing.
   - `CombatV2StatePacket` S2C: fighter state, combo node id, open-branch bitmask, window ticks
     left, grab-available flag. This is the only source the prompt HUD reads.
3. **Input layer** (`client/combat/v2/V2InputLayer`) — the single place that reads keys in v2 and
   emits `V2Input` (`LIGHT_PRESS/HOLD`, `HEAVY_PRESS/HOLD`, `GUARD_PRESS/RELEASE`, `STEP` + dir,
   `VANISH`, `KI_PRESS/HOLD`, `GRAB`, `CHASE`, `DRAGON_DASH`, `SUPER_1..4`, `ULTIMATE_1..2`,
   `AWOKEN`, `EVASIVE`). `Bt3CombatClient.onClientTick` returns early when the synced mode is V2.
4. **Fighter state** (`combat/v2/V2Fighter`, `V2FighterStore`) — one object per player holding
   state enum, combo node, target, input buffer, windows and cooldowns; one map, cleared on logout,
   death, dimension change, server stop and mode switch. Ticked only for players that have a live
   state (no global scans).
5. **Combo graph** (`combat/v2/combo/ComboGraph`, `ComboNode`) — Minecraft-free. Loaded from
   `data/xenopixelsmod/combat_v2/combo_graph.json` with a `config/xenopixelsmod/` override, same
   loader shape as `features/transformation/passive/FormPassives`. A node carries input, direction
   variant, `Bt3AnimationIntent`, phase ticks (startup/active/cancel/recovery), damage scale,
   ki/stamina, `HitReaction`, and next nodes. Default graph: `L1→L2→L3`; after L3 the branches are
   `LMB` kick finisher, `RMB` launcher/knockback, `Guard+LMB` grab; `RMB` branches from L1/L2;
   W/A/S/D held picks the node's directional variant (fixes styling properly). Input buffer length
   is config.
6. **Hit reactions** (`combat/v2/HitReaction`) — named reactions (light, heavy, launch up/forward/
   down, knockback short/long, knockdown, guard hit, guard break) resolved to impulses and applied
   only through the existing `combat/CombatKnockback` so DMZ master protection keeps working.
7. **Shared motion** (`combat/v2/motion/V2Motion`) — one velocity-based mover with profiles, used by
   chase, dragon homing, Dragon Dash, Z-Burst, step and rush approach. Reuses
   `network/ChaseRouting` (obstacle detour), `combat/Bt3Landing` (landing geometry, open-spot
   test) and `combat/RushCamera` (view assist). No teleports for tracking moves.
8. **Moves on top** — `V2Chase` (launch → W), `V2DragonHoming` (window after a launcher),
   `V2DragonDash` (N), `V2ZBurst` (gap closer when a swing is out of reach), `V2Step`
   (Space + direction, i-frames; replaces sonic sway), `V2SuperCounter` (window opened when hit,
   consumed by Vanish), `V2CinematicRush` (keeps `api/registry/RushRegistry`,
   `Bt3RushDefinition` and `api/event/RushEvent` so addons are unaffected), rush strikes (the four
   `XenoRushTechniques` ids stay registered in DMZ; under V2 `XenoRushTechniqueEvents` hands the
   resolved hit to a v2 reaction instead of its own knockback).
9. **Grab / throw** (`combat/v2/V2Grab`) — Guard + LMB in range. Startup, then a hold that pins the
   victim each tick (velocity zeroed, same per-tick revalidation shape as `HakaiChannelSystem`),
   then a throw whose direction comes from the held key (forward, back, up, down). Beats guard;
   loses to a hit during startup. Victim players can tech with the same input inside a config
   window. Refused against DMZ masters (`event/DmzMasterProtection`) and party members
   (`features/party`). New `GrabEvent` under `api/event` (additive).
10. **Prompt HUD** (`client/combat/v2/CombatPromptOverlay`) — new layer in
    `client/XenoHudRegistration`, centred just under the crosshair. Shows a green plate with the key
    glyph and a shrinking timer for each open branch, and a distinct "GRAB" prompt when
    `grab-available` is set. Art from a new `tools/gen_combat_prompt_atlas.py`, copied from the
    pattern in `tools/gen_bt3_hud_atlas.py`: crops `PROMPT_PLATE`, `PROMPT_ARROW` and the green
    technique ring from the UI bundle, tints green, packs to the smallest power-of-two atlas that
    fits, and emits the PNG, manifest and generated `XenoCombatPromptAtlas.java` with `--check`.
    A Superdesign mock of the indicator is produced first and shown for a look before art is locked.
11. **Keybind system** (`client/keybind/XenoKeybinds`, `/xenokeybind`) —
    - `clean`: writes `config/xenopixelsmod/keybind-backup-<timestamp>.json` (every mapping's key
      and modifier), then unbinds every mapping that is not vanilla, `key.dragonminez.*` or
      `key.xenopixelsmod.*`. Extra mod ids to keep come from a client config allow-list.
    - `restore [file]`, `status`, `auto on|off`, `preset xv2`.
    - Auto: client config `keybindAutoClean` (default on), runs once the first time V2 is active,
      records that it ran, and prints the restore command in chat.
    - `preset xv2` applies the doc layout to Xeno v2 mappings only. Combat stance
      (`client/combat/v2/CombatStance`: mode is V2 and locked on or fists out, built on the existing
      `Bt3CombatClient.fistsActive` rule) suppresses the vanilla/DMZ meaning of Q, E, V, middle
      mouse, wheel and RMB only while active.
    - The existing `/xenobind` is left alone.

### Default v2 layout (from the doc)

LMB light · RMB heavy · hold = charged · WASD + attack = variant · E guard · Guard+LMB grab ·
V vanish · Q ki blast · C ki charge (DMZ's own key) · MMB lock · wheel cycle target ·
Space + direction step · W after launch chase · N Dragon Dash ·
Ctrl + LMB/RMB/Q/Space → DMZ technique slots 1–4 · Alt + LMB/RMB → slots 5–6 ·
Alt + Q transform · Alt + Space evasive (Zanzoken when unlocked).

Assumption to confirm in play: the Ctrl/Alt palettes drive DMZ's existing technique slots rather
than a new skill system.

### v1 touch points (the only edits to existing combat files)

- `combat/controller/CombatControllerMode.java`, `LegacyActionPolicy.java`,
  `CombatControllerService.java` — new mode, refusal set, sweep.
- `config/XenoServerConfig.java` + `XenoServerConfigKeys.java` — accept `v2` in
  `normalizeCombatControllerMode`; new `v2*` tuning keys in their own block.
- `network/ModNetwork.java` — two appended packets, protocol 102.
- `client/combat/Bt3CombatClient.java` — early return when mode is V2;
  `combat/technique/XenoRushTechniqueEvents.java` — branch to v2 reaction under V2.

## Slices (each ends green on its own)

0. **Spec and baseline** — save this design to `docs/superpowers/specs/2026-10-05-combat-v2-design.md`
   and the task-level plan to `docs/superpowers/plans/`; run `./gradlew test` and record the v1
   baseline; add characterisation tests for v1 behaviour v2 must match (rush follow-up window,
   landing geometry, chase routing already have tests to reuse).
1. **Selector and skeleton** — mode, gate, packets, protocol, empty fighter store. V2 on does
   nothing yet except sync state; switching back sweeps cleanly.
2. **Input layer, stance, keybinds** — `V2InputLayer`, `CombatStance`, `/xenokeybind` with backup,
   restore, auto and preset.
3. **State machine and combo graph** — fighter states, graph loader, buffering, directional
   variants, hit reactions.
4. **Motion** — `V2Motion`, then chase, dragon homing, Z-Burst, step, Dragon Dash.
5. **Rush and counter** — cinematic rush on `RushRegistry`, rush strikes, super counter.
6. **Grab/throw and prompt** — grab logic, `GrabEvent`, Superdesign mock, atlas generator, overlay.
   Grab/throw clips added through `tools/make_bt3_animations.py` and appended (never inserted) to
   `Bt3AnimationIntent`.
7. **Docs and handoff** — `docs/combat-v2.md`, changelog, dated handoff from
   `ai/handoff-template.md`.

Tests are written first per slice (JUnit for every Minecraft-free class: graph, gate, reactions,
motion maths, grab timing, keybind classification and backup round-trip).

## Compatibility and rollback

- v1 stays default; a server that never sets `combatControllerMode=v2` behaves exactly as today.
- Rollback is `/xenocombat mode legacy`; the service sweeps v2 state. Keybinds roll back with
  `/xenokeybind restore`.
- Protocol 102 means client and server must update together (same as every earlier bump).
- `api/**` changes are additive only. Wire enums are append-only.
- Dirty user files in the tree (`tools/gen_bt3_hud_atlas.py`, `tools/generated/xeno_bt3_hud_atlas.json`
  and the others in `git status`) are not touched; the prompt atlas is a new generator and new
  outputs. No commits unless asked.

## Verification

1. `./gradlew test` — new v2 tests plus all existing v1 tests unchanged and passing; update
   `network/ProtocolVersion` and extend `SyncServerConfigPacketControllerModeTest` for `v2`.
2. `python tools/gen_combat_prompt_atlas.py --check`.
3. `./gradlew build jarJar serverJar -PofflineMcMeta`, confirm `META-INF/jarjar` contents rule;
   `./gradlew buildApiExampleAddon -PofflineMcMeta`.
4. Dedicated server start with no client classes loaded (fresh `run/logs/latest.log`).
5. Fresh client run, checked in a new log and by play:
   - legacy mode: v1 combat unchanged;
   - `/xenocombat mode v2`: L-L-L shows three green branch prompts; LMB kicks, RMB launches,
     Guard+LMB grabs and throws in the held direction; W after a launch chases without teleporting;
     N Dragon Dash; Space+direction step; counter window on being hit; cinematic rush plays the
     form's registered definition;
   - stance: E opens inventory out of combat and guards in combat;
   - `/xenokeybind clean` writes a backup, other mods' keys are unbound, `restore` brings them back;
   - switch back to legacy mid-fight: no stuck state.
6. Not verified until measured: tick cost of v2 under many fighters. Take a profiler baseline in
   legacy and compare in v2 with the same scenario before any TPS claim.

## Amendments after the first playtest (2026-10-06)

The owner played the first build and reported eleven problems. These change the design above.
Where this section and the text above disagree, this section is current.

| Area | Above | Now |
|---|---|---|
| Prompt position | Centred just under the crosshair | Bottom centre, above the hotbar and the action-bar text |
| Prompt art | Cropped from the UI bundle by a new `tools/gen_combat_prompt_atlas.py` | Generated by `tools/atlas-panels/xeno_extra_specs.py`; five outlines, drawn through `XenoAtlasSprites`. The cropping tool and its atlas were deleted |
| Prompt labels | Light / Heavy | What the branch does (Punch, Kick, Launch, Smash), sent in two spare bits of the branch mask byte |
| Vanish | Not rewritten; v2 key reaches v1 | Rewritten in v2 (`V2Moves.vanish`), on B instead of V |
| Step | Space + direction | Double-tap A / D / S; no default key |
| Guard key | E is guard in the stance | E held is guard, E tapped is the inventory |
| Stance | Locked on or fists out | Locked on, recently engaged, or a hostile mob under the crosshair |
| Lock | Own pick | DragonMineZ's `LockOnEvent.toggleLock()` |
| Heavy attack | Launcher / knockback | Kicks, with direction and charge choosing launcher, slam or arc |
| Damage | Added on top of a DragonMineZ hit | DragonMineZ melee damage times the beat's scale, set in `DMZEvent.DamageModifyEvent` (`V2Damage`) |
| Reach | Feet to feet | Eyes to nearest point of the hitbox, facing and line of sight (`ReachRules`, `V2Targeting`) |
| Combo timing | Startup then window | Startup, cancel (recovery), then window; inputs during recovery are held |
| Travel | Velocity only, no pose | Same mover, plus aura, flight pose and the `ChaseRouting` detour (`V2TravelPose`) |
| Rush strikes | v2 reaction when the strike fires | Recorded when it fires, applied when DragonMineZ's strike lock clears (`V2State.STRIKE`) |
| Rush strike view | Not considered | For a strike at the caster's lock: the attacker lands on their own side and the server does not aim them, leaving the view to lock-on (`XenoRushStrikeView`, `StrikeAttackRushViewMixin`). Both controllers |
| When v2 runs | Combat stance: locked on, or fists out | Only while locked on a target (`CombatStance`, `LockRules`, `V2Lock`). Every move is made at the lock; nothing auto-targets |
| Keybind cleanup | Unbinds every other mod's binding | Leaves held modifiers (default key Shift, Ctrl or Alt) bound, and repairs profiles cleaned before that rule |
| Tuning file | `v2*` keys in `XenoServerConfig` | Its own file with a `version`, re-based once from the first build's file |
| Grab and throw | Part of v2 only | Shared with `legacy` and `bt3_manual` on guard + punch (`grabOutsideV2`, default on; `combat.grab.use`), still only at a locked target. The other controllers admit that one input (`V2CombatGate.decideGrabOnly`) and get its prompts |
| "v1 stays untouched" | A server that never sets `combatControllerMode=v2` behaves exactly as before | No longer true by default: `legacy` has the grab unless `grabOutsideV2` is `false`. The fighter ticker and `CombatV2StatePacket` run under every controller |
| Grab pose | The grab node's `intent` | Was never sent; now sent, with a borrowed strike pose for the throw. No authored clips yet |

Wire: `V2Input.VANISH` and `V2State.STRIKE` were appended, `CombatV2StatePacket` gained the id of
whoever opened the counter window, and the target id in `CombatV2InputPacket` now means the
sender's lock-on target. No packet was added. The protocol is 103. Sharing the grab changed no
packet shape: the server accepts `GRAB` under every controller and sends the state packet under
every controller.

Still open from the list above: grab and throw clips, local pose prediction, gamepad rows, a
profiler comparison, and Just Guard / stamina break. A command to move the prompt was considered
and left out; its position is one constant in `CombatPromptOverlay`.
