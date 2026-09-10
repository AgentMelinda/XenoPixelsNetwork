# Claude Handoff - Unfinished BT3 HUD Work

**Date:** 2026-09-08
**Repository:** `XenoPixelsNetwork_qwen`
**Target:** Minecraft 1.21.1 / NeoForge / Dragon Mine Z integration

## Read This First

This handoff covers only the unfinished BT3-style HUD and normal DMZ menu replacement work. It
does not hand off the separate DMZ Hair Lab website. Codex is continuing that website after writing
this document.

Do not describe the BT3 HUD as implemented. At this handoff:

- The design plan exists in `docs/plan-new-hud's.md`.
- The source-art ZIP exists at `all_chat_clean_ui_elements_bundle.zip`.
- There is no `XenoBt3HudView.java`.
- There is no `xeno_bt3_hud_atlas.png`.
- `/xenohud renderer bt3` does not exist.
- No normal Dragon Mine Z menu replacement is implemented for this renderer.
- Current uncommitted HUD code only adds Sparking/Max Power red-to-gold KI behavior to existing
  renderers. That is related state/visual work, not the new HUD.

The working tree contains unrelated, uncommitted combat, controller, animation, config, and
Sparking work. Do not reset, clean, revert, rename, or broadly reformat it.

## Protected Boundary

The user explicitly said not to touch the DMZ alternative overlay. Leave this file byte-for-byte
unchanged:

- `src/main/java/net/bullettrain/xenopixelsmod/client/DmzHudOverlayBlocker.java`

Do not change:

- Dragon Mine Z overlay `dragonminez:alternativehud`
- Current DMZ overlay allow/block sets or event priority
- DMZ scouter, tracked-quest, or beam-clash overlays

The existing blocker already contains `dragonminez:xenoversehud` and
`dragonminez:alternativehud`. That existing state is not permission to alter it. The new renderer
must be selected through XenoPixels' renderer setting, not another overlay cancellation rule.

## Requested Result

Add an opt-in BT3/Xenoverse-inspired XenoPixels HUD using the clean UI-element bundle, and use the
same renderer selection to gate replacements for Dragon Mine Z's normal menu screens.

Required selection behavior:

- `/xenohud renderer bt3` selects the new HUD.
- Existing `legacy`, `modern`, `modernunified`, and `ldlib` alias behavior remains available.
- Selecting a non-`bt3` renderer restores stock DMZ menu screens.
- The new work must not modify the alternative DMZ overlay implementation.

Required live data:

- Character portrait, player name, form, and transformation presentation
- HP, KI, stamina, and existing transform/release data
- Sparking/Max Power charge staging and active drain
- Existing technique slots and cooldowns
- Contextual BT3 combo follow-up prompt while its authoritative state is valid

Required Sparking presentation:

- At full KI, committed Max Power charge begins with red KI segments.
- Segments turn gold one by one from authoritative charge progress.
- Active Sparking displays remaining gold KI as the drain/timer state.
- Do not replace this with a render-only timer disconnected from server state.

## Verified Renderer Architecture

Selection currently uses two booleans in
`src/main/java/net/bullettrain/xenopixelsmod/client/config/XenoHudConfig.java`:

| Command | legacy | unified | View |
|---|---:|---:|---|
| `legacy` | true | false | procedural path in `XenoHudOverlay` |
| `modern` | false | false | `XenoModernHudView` |
| `modernunified` | false | true | `XenoUnifiedHudView` |
| `ldlib` | false | false | alias of `modern` |

Command registration is in
`src/main/java/net/bullettrain/xenopixelsmod/client/command/XenoHudCommands.java`. Runtime dispatch
is in `src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java`.

The HUD config version is currently `5`. A string/enum renderer migration must preserve the view
each old boolean combination selected. Do not invert `legacyHudRenderer`, and do not migrate old
users to `bt3`.

## Verified Existing Sparking Work

Current uncommitted files:

- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/SparkingKiBar.java`
- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoHudView.java`
- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoModernHudView.java`

`SparkingKiBar` reads active state from the local Sparking effect or `SparkingClientState`, owner
charge state from `SparkingChargeClientState`, and segment count from
`Bt3SparkingCharge.SEGMENTS`. Preserve that relationship when wiring the new renderer. Do not copy
only its colors and discard its state logic.

## Verified Source Art

Direct ZIP inspection on 2026-09-08 found 157 entries: 153 PNG entries and 4 non-PNG file entries.
Verified useful paths include:

- `01_xenoverse_party_hud_clean/core_elements/03_player1_nameplate_frame.png`
- `01_xenoverse_party_hud_clean/core_elements/08_hp_fill_bar.png`
- `01_xenoverse_party_hud_clean/core_elements/09_ki_segment_bar.png`
- `01_xenoverse_party_hud_clean/core_elements/10_stm_segment_bar.png`
- `01_xenoverse_party_hud_clean/core_elements/16_ability_icon_purple.png`
- `01_xenoverse_party_hud_clean/core_elements/17_ability_icon_blue.png`
- `01_xenoverse_party_hud_clean/core_elements/18_ability_icon_green.png`
- `01_xenoverse_party_hud_clean/core_elements/19_ability_icon_orange.png`
- `01_xenoverse_party_hud_clean/core_elements/20_portrait_ring_p1.png`
- `01_xenoverse_party_hud_clean/core_elements/34_energy_streak_orange.png`

Treat the ZIP as source art, not ready runtime textures. Crop and normalize selected assets into a
compact atlas. Do not copy all PNGs into resources. Existing `tools/gen_hud_textures.py` and
`tools/hud_layout.py` may be reusable, but they do not prove the new atlas exists.

## Work Not Yet Done

These items are absent and must not be reported as complete:

1. Production extraction/cropping pipeline for selected ZIP sprites
2. Generated atlas organizers, manifest, and Java UV/layout data
3. `XenoBt3HudView`
4. A `bt3` renderer config value and migration
5. `/xenohud renderer bt3`
6. Dispatch from `XenoHudOverlay` to the new view
7. BT3 combat rail and contextual combo prompt
8. Reduced-motion behavior for new HUD animations
9. BT3-gated replacements for normal DMZ character/status, skills, quest, options, and server-menu
   screens
10. Screenshot and readability acceptance passes in a running Minecraft client

## Implementation Plan

### Phase 1 - Protect and Baseline

1. Record `git status --short` before editing.
2. Confirm `DmzHudOverlayBlocker.java` is unchanged before and after the task.
3. Build/test the current branch before HUD edits if unrelated work permits it. Record unrelated
   failures separately instead of repairing them as part of this patch.
4. Capture `legacy`, `modern`, and `modernunified` screenshots for regression comparison.

### Phase 2 - Asset Pipeline

1. Extract the ZIP into a temporary/generated tooling directory, never directly into resources.
2. Select only the first production sprites: portrait ring, nameplate, HP, KI, stamina, four
   technique shells, prompt plate/arrow, and gold energy streak.
3. Crop transparent margins to measured nontransparent bounds.
4. Normalize pixel density and integer-align edges.
5. Generate from one source of truth:
   - `src/main/resources/assets/xenopixelsmod/textures/gui/xeno_bt3_hud_atlas.png`
   - machine-readable atlas manifest
   - Java sprite rectangles/UV data
   - visual contact sheet
6. Reject duplicate names, empty sprites, overlapping rectangles, out-of-bounds UVs, and unexpected
   atlas growth.

Do not independently hand-maintain the manifest and Java UV constants.

### Phase 3 - Renderer Selection Migration

1. Add a renderer enum/string for `legacy`, `modern`, `modernunified`, and `bt3`.
2. Bump the HUD config version.
3. Migrate old booleans exactly:
   - legacy true -> `legacy`
   - legacy false and unified false -> `modern`
   - legacy false and unified true -> `modernunified`
4. Keep `ldlib` as an alias for `modern`.
5. Update command reporting and usage text.
6. Update every boolean-dependent caller or retain compatible helpers such as `unifiedActive()`.
7. Test migration before removing or ignoring the old fields.

### Phase 4 - `XenoBt3HudView`

Create a dedicated class; do not spread BT3 asset branches through `XenoModernHudView`.

Reuse verified systems where suitable:

- `XenoHudSnapshot` and `XenoHudSnapshotFactory`
- `CharacterPortraitCache`
- `HudNumbers`
- current transform progress/ring data
- `SparkingKiBar`
- current technique-slot and cooldown state
- current HUD x/y/scale editing behavior

Implement a static readable pass first:

1. Portrait and name/form plate
2. HP and current/max values, including existing delayed-damage behavior if available
3. Segmented KI
4. Segmented stamina
5. Transform/release information
6. Editor outline and correct reported dimensions

Only then add the combat rail and motion. Validate the static layout at GUI scales 2, 3, and 4
before animating it.

### Phase 5 - Smart Motion

Animations must be state-driven and frame-rate independent. Implement:

- damage easing/lost-health indication
- discrete KI and stamina segment changes
- full-KI red-to-gold Max Power progression through `SparkingKiBar`
- active Sparking gold drain
- short entrance/exit motion for the combo follow-up prompt
- immediate prompt cancellation on timeout, target loss, guard interruption, server rejection, or
  rush start
- reduced-motion rendering that keeps all data readable without major sweeps, flashes, or shakes

Do not invent combo state locally if current BT3 rush/follow-up packets already provide it. Trace
the existing combat classes first.

### Phase 6 - Normal DMZ Menus

Gate each replacement strictly on renderer `bt3`. Port one screen at a time:

1. Character/status
2. Skills
3. Quest
4. Options
5. Server menu

For each screen:

- identify the exact stock class from the installed DMZ 2.1.3 JAR
- preserve packet sends, validation, tabs, scrolling, and saved data
- use a narrowly scoped screen hook
- fall back to the stock screen if replacement initialization fails
- never alter overlay cancellation
- restore the stock screen when any non-`bt3` renderer is selected

Do not claim a menu is replaced before every interactive control has been tested.

### Phase 7 - Verification

Automated checks:

- atlas manifest parsing, UV bounds, overlap, and empty-sprite detection
- renderer config migration
- renderer command selection/reporting
- segment calculations and prompt lifetime
- project tests and compilation

Visual checks:

- 16:9, 16:10, ultrawide, and 4:3
- GUI scales 2, 3, and 4
- long names and large values
- low health and delayed damage
- empty/partial/full KI and stamina
- every Max Power red-to-gold stage
- active Sparking drain
- transformation charge/progress
- controller and keyboard prompts
- all cooldown states and every replaced DMZ menu action

Regression checks:

- `legacy`, `modern`, and `modernunified` remain unchanged
- `ldlib` still selects `modern`
- `DmzHudOverlayBlocker.java` remains byte-for-byte unchanged
- no alternative-overlay behavior is added, removed, or rewritten

## Definition of Done

- `/xenohud renderer bt3` selects a complete, readable `XenoBt3HudView`.
- The production atlas, manifest, and generated layout data exist and validate.
- Old config migration preserves each user's existing renderer.
- HP, KI, stamina, transform, Sparking, techniques, cooldowns, and combo prompt use authoritative
  state.
- Reduced-motion behavior exists.
- Each claimed DMZ menu replacement preserves its controls/data and has a stock fallback.
- Selecting a non-`bt3` renderer restores stock DMZ menus.
- The alternative DMZ overlay boundary remains untouched.
- Build/tests pass, or an unrelated pre-existing failure is documented exactly.
- A running Minecraft visual acceptance pass has been completed.

## Do Not Do

- Do not touch the alternative DMZ overlay or `DmzHudOverlayBlocker.java`.
- Do not claim the new HUD already exists.
- Do not call the ZIP a finished HUD.
- Do not copy all source PNGs into resources.
- Do not silently select `bt3` for existing users.
- Do not invert the old renderer booleans during migration.
- Do not overload `XenoModernHudView` with the new renderer.
- Do not fake Sparking or combo state from render time.
- Do not invent DMZ screen class names; inspect DMZ 2.1.3 first.
- Do not reset or clean unrelated working-tree changes.

## Start Here

```powershell
git status --short
git diff -- src/main/java/net/bullettrain/xenopixelsmod/client/DmzHudOverlayBlocker.java
Get-Content -LiteralPath "docs/plan-new-hud's.md"
Get-Content -LiteralPath src/main/java/net/bullettrain/xenopixelsmod/client/command/XenoHudCommands.java
Get-Content -LiteralPath src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java
Get-Content -LiteralPath src/main/java/net/bullettrain/xenopixelsmod/client/config/XenoHudConfig.java
```

Before handing work back, report changed files, commands run, test outcomes, untested live-client
behavior, and confirm the protected overlay file was not changed.
