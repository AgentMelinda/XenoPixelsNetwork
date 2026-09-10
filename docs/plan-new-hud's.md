# Plan: Opt-In BT3/Xenoverse HUD

Date: 2026-09-08

## Goal

Build a new XenoPixels-owned HUD renderer from the reusable art in
`all_chat_clean_ui_elements_bundle.zip`. The ZIP is a source-art library, not a finished HUD: code
must select, crop, normalize, atlas, anchor, animate, and populate the clean shells with live DMZ
data.

The future renderer name is `bt3`, selected with `/xenohud renderer bt3`. Selecting it enables both
the in-game BT3 HUD and the matching replacements for DragonMineZ's normal character/status,
skills, quest, options, and server-menu screens. Existing `legacy`, `modern`, `modernunified`, and
the `ldlib` alias remain available and keep their current behavior.

## Protected Boundary

The following are explicitly out of scope and must not be edited, replaced, re-registered, or
cancelled differently while implementing this plan:

- DragonMineZ's `dragonminez:alternativehud` overlay.
- `src/main/java/net/bullettrain/xenopixelsmod/client/DmzHudOverlayBlocker.java`.
- The existing DMZ overlay allow/block lists and their event priority.
- DMZ's scouter, tracked quest, and beam-clash overlays.

The new HUD is another Xeno renderer behind the existing Xeno HUD selection. Its normal-menu work
may replace DMZ screen classes through narrowly scoped screen hooks, but it is not a DMZ overlay
patch and must not acquire its opt-in behavior by changing DMZ overlay cancellation.

## ZIP Audit

- The bundle contains 154 grouped asset files plus three bundle documentation files, for 157 ZIP
  entries total.
- There are 153 PNG files: 81 contain transparent pixels and 72 are fully opaque.
- `01_xenoverse_party_hud_clean` contains the primary combat-HUD pieces: portrait rings,
  nameplates, HP/KI/STM bars, energy orbs, badges, arrows, plates, and ability icons.
- `02_dragonmine_status_clean` contains reusable status/stat panels, headers, rows, buttons,
  nameplate pieces, and micro-accents.
- `03_other_menus_clean` is mostly future screen/menu material. Its full-screen quest, skills,
  server, options, and minigame boards are references or screen shells, not in-game HUD widgets.
- `04_legacy_clean_elements` contains large-canvas legacy pieces useful as visual references, but
  they require cropping and normalization before runtime use.
- Several nominally reusable elements are stored on 1024x1536, 1254x1254, or 1536x1024 canvases.
  They must not be uploaded as individual runtime textures at original size.

## Visual Direction

- Read as BT3 first: fast, angled, high-contrast combat information with clear segmented energy.
- Use the cleaner Xenoverse-like frames as production art instead of copying full mockup screens.
- Keep health dominant, ki immediately readable, and stamina visible without competing with the
  center crosshair or the lower technique bar.
- Use orange/gold for power and Sparking, blue/cyan for ki and player-two accents, red only for
  danger/full-ki Max Power staging, and neutral charcoal for empty shells.
- Dynamic text is rendered in code. Do not bake names, values, key prompts, or localized labels
  into the atlas.

## Runtime Layout

### Player Cluster

- Anchor the main player cluster to the top-left safe area through the existing HUD x/y/scale
  settings.
- Portrait: use `20_portrait_ring_p1.png`, cropped to its nontransparent bounds and normalized to
  a compact atlas sprite. Keep the existing live character portrait cache and transform ring.
- Nameplate: derive a nine-slice-capable frame from `03_player1_nameplate_frame.png`; draw the
  player name, form, and optional level dynamically.
- HP: use `08_hp_fill_bar.png` as the color/style source. The actual fill is clipped to current/max
  health and retains the existing delayed-damage/lost-health readability where supported.
- KI: use `09_ki_segment_bar.png` as the segmented shell and fill language. Segment count remains
  data-driven rather than being inferred from texture width.
- STM: use `10_stm_segment_bar.png` with discrete filled, spent, and regenerating states.
- Small status accents may use the energy orbs and corner brackets, but only after the core bars
  pass readability tests at GUI scales 2, 3, and 4.

### Combat Rail

- In unified mode, place the existing Xeno technique/cooldown data directly below the main cluster.
- Reuse the blank ability icons as color-coded slot shells; render actual technique icons and input
  labels above them.
- Show the contextual cinematic follow-up prompt only during its valid window: `X X X -> A` on
  Xbox/BT3 mode and the active keyboard binding names otherwise.
- The prompt must disappear immediately on timeout, target loss, guard interruption, server reject,
  or rush start.

### Optional Party/Target Pieces

- Reserve `portrait_ring_p2`, P2 badges, blue/orange arrows, and empty plates for later party and
  lock-target modules.
- Do not ship party frames until real party-member data, distance rules, dead/disconnected states,
  and screen-edge collision behavior are implemented.
- Target widgets must not replace DMZ's scouter overlay.

## DragonMineZ Menu Replacement

- When `bt3` is selected, replace the normal DMZ character/status, statistics, skills/information,
  quest tree, options/settings, and server menu presentation with Xeno screens built from bundle
  groups `02_dragonmine_status_clean` and `03_other_menus_clean`.
- Preserve DMZ's real containers, data sources, packets, permissions, buttons, tooltips, scrolling,
  and gameplay actions. This is a view/controller adaptation, not a duplicate character system.
- If a DMZ screen is not yet ported, fall back to the original DMZ screen rather than opening a
  blank or incomplete replacement.
- Keep screen replacement gated by the same explicit `bt3` renderer selection so users can return
  to the original DMZ menus by choosing `legacy`, `modern`, or `modernunified`.
- Do not replace or intercept `dragonminez:alternativehud`; it is an overlay, not one of the normal
  menu screens covered by this section.
- Build each replacement independently in this order: status/statistics, skills/information, quest
  tree, options/settings, then server menu. Do not ship an all-or-nothing screen mixin.

## Sparking Presentation

- At full normal ki, all eight ki segments enter the red Max Power staging state.
- While charge is held, convert red segments to gold from left to right using the authoritative
  charge progress already synchronized to the client.
- Each segment transition uses a short 80-120 ms emissive sweep and a small scale pulse; previously
  converted segments remain steady instead of looping.
- The final segment receives a stronger flash and gold energy-streak accent, then the whole ki bar
  settles into the steady Sparking state.
- Releasing early snaps the staged gold segments back to the normal/full-ki state as the server
  resets the charge. No client-only completion is allowed.
- During Sparking, ki drain is the timer. The gold fill recedes with real current ki and never uses
  a second fake duration bar.

## Animation Rules

- Animate state changes, not every frame. Bar values clip smoothly; static frames remain static.
- Use critically damped or short ease-out motion for damage, resource spending, and panel entrance.
- Never bounce HP/KI/STM continuously; continuous motion is reserved for active charge, danger, or
  Sparking.
- Segment lighting is deterministic from synchronized progress so pausing, lag, or low FPS cannot
  reorder the red-to-gold sequence.
- All animation clocks must be seek-safe and based on client tick/partial tick state, not accumulating
  unconstrained floating-point deltas.
- Provide `reducedHudMotion`: replace sweeps and overshoot with direct 100-150 ms fades.

## Asset Pipeline

1. Extract the ZIP into a temporary tooling directory, never directly into `src/main/resources`.
2. Use the bundle manifest/index to retain source provenance and original dimensions.
3. Crop each selected PNG to its nontransparent content bounds; record the crop rectangle.
4. Remove accidental opaque backgrounds from elements intended as overlays only after visual
   review. Preserve deliberately opaque menu panels for future screens.
5. Normalize selected HUD sprites to a consistent pixel density and integer-pixel edge alignment.
6. Build one production atlas, proposed path:
   `assets/xenopixelsmod/textures/gui/xeno_bt3_hud_atlas.png`.
7. Generate a matching Java layout table and a machine-readable atlas manifest from one source of
   truth. Do not hand-maintain UVs in two files.
8. Add a tooling check for overlap, out-of-bounds UVs, duplicate names, transparent-empty sprites,
   and unexpected atlas growth.

Proposed production sprite names:

- `player_frame`, `player_nameplate`, `portrait_ring`.
- `hp_frame`, `hp_fill`, `hp_damage_delay`.
- `ki_frame`, `ki_segment_empty`, `ki_segment_full`, `ki_segment_red`, `ki_segment_gold`.
- `stm_frame`, `stm_segment_empty`, `stm_segment_full`, `stm_segment_regen`.
- `slot_frame_purple`, `slot_frame_blue`, `slot_frame_green`, `slot_frame_orange`.
- `prompt_plate`, `prompt_arrow`, `gold_streak`, `danger_chevron`.

## Code Architecture

- Add a dedicated `XenoBt3HudView`; do not overload `XenoModernHudView` with asset-specific branches.
- Reuse the existing shared HUD snapshot/data model, `CharacterPortraitCache`, compact-number helper,
  transform progress, Sparking charge state, and technique-slot data.
- Add a renderer enum/string migration that can represent `legacy`, `modern`, `modernunified`, and
  `bt3` without ambiguous combinations of booleans. Migrate old booleans without changing the
  renderer existing users currently see.
- Extend `/xenohud renderer` with `bt3`, while keeping existing command names and the `ldlib` alias.
  The selected renderer is also the feature gate for the normal DMZ menu replacements.
- Keep renderer selection client-side and opt-in during initial release.
- Put geometry constants and atlas UVs in a BT3-specific layout class generated by the atlas tool.
- Keep widget state separate from rendering: resource smoothing and one-shot transition detection
  belong in a small client state object, not inside blit helpers.

## Implementation Phases

1. **Asset proof:** crop the portrait, nameplate, HP, KI, STM, four ability frames, and gold streak;
   generate a small atlas and contact sheet.
2. **Static renderer:** implement `XenoBt3HudView` with live portrait/name/HP/KI/STM and no motion.
3. **Renderer selection:** add the explicit `bt3` option and config migration while proving current
   renderers are pixel-identical.
4. **Combat rail:** integrate technique slots, cooldowns, charge indicators, and the contextual
   `X X X -> A` prompt.
5. **Motion pass:** add damage easing, segment changes, Max Power red-to-gold progression, and
   reduced-motion behavior.
6. **DMZ menu pass:** replace the normal DMZ screens one at a time with safe fallback to the stock
   screen for every unported or failed module.
7. **Optional modules:** party and target widgets only after the core HUD is accepted in game.

## Verification

- Unit-test atlas manifest parsing, UV bounds, layout scaling, renderer config migration, segment
  calculations, and contextual prompt lifetime.
- Capture screenshots at 16:9, 16:10, ultrawide, and 4:3; test GUI scales 2, 3, and 4.
- Test normal ki, full ki, every red-to-gold stage, Sparking drain, zero stamina, low health,
  transformation charge, long player names, large numbers, and all technique cooldown states.
- Verify controller and keyboard prompts independently.
- Open every replaced DMZ menu, exercise every button/tab/scroll action, and compare packets and
  saved data with the stock screen behavior.
- Compare `legacy`, `modern`, and `modernunified` before/after to ensure the opt-in addition causes
  no visual regression.
- Verify `dragonminez:alternativehud` and `DmzHudOverlayBlocker` are byte-for-byte unchanged in the
  HUD implementation commit.
- Perform an in-world acceptance pass; automated rendering tests do not replace Minecraft visual
  inspection.

## Definition of Done

- `/xenohud renderer bt3` selects a complete, readable Xeno-owned HUD and its implemented normal
  DMZ menu replacements; choosing another renderer restores the stock DMZ menus.
- Existing renderers remain selectable and unchanged.
- The ZIP is represented by a compact production atlas with recorded provenance, not copied wholesale.
- HP, ki, stamina, Sparking staging/drain, transform progress, techniques, cooldowns, and the
  cinematic follow-up prompt all use authoritative game state.
- No code or behavior related to DMZ's `alternativehud` overlay is changed.
