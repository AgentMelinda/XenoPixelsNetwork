# Plan: Complete the six-menu Neon V3 redesign and repair part scaling

## Problem and approach

The current implementation is incomplete: only DragonMineZ's Character/Stats page is a true modular Neon V3 screen. Skills, Quests, Minigames, Party, and Settings still use DragonMineZ's real screens with the older generic HD atlas fallback. The Character editor also stores `partScale`, but sprite parts ignore it; only text currently scales, and character/button hitboxes do not consistently follow visual transforms.

Complete the six V-key menus with the tracked master-bundle artwork, mapping the supplied groups as follows: Status V3 → Character, Skills/Information → Skills, Quest Tree → Quests, Minigames → Minigames, Server Menu → Party, and Options → Settings. Preserve the five complex DragonMineZ screens as the behavioral authority and replace only their verified texture/UV chrome, widgets, and visual states. Repair Character's modular transform pipeline so scaling is visible in both the live screen and editor preview and interaction bounds match what is drawn.

## Todos

1. **Lock the exact DragonMineZ 2.1.3 contracts**
   - Verify the six exact screen classes, every menu/button texture path, sampled UV rectangle, draw size, panel origin, scissor region, hover/selected state, and widget hitbox from the tracked decompiled source and pinned JAR.
   - Record a page-to-source-to-output table in the generated manifest; do not infer missing paths or states.
   - Keep Character as `XenoNeonStatsScreen`; keep the other five real DMZ screen implementations so skill upgrades/imports, quest actions and panning, minigame launch/access checks, party actions, settings persistence, navigation, scrolling, and packets remain DMZ-owned.

2. **Generate distinct Neon V3 chrome for all six pages**
   - Extend or replace `tools/gen_dmz_menu_themes.py` so NEON mode uses page-specific assets from the tracked bundle instead of repeating the same HD panel on Skills, Minigames, Party, and Settings.
   - Build exact-UV atlases for Skills, Quests, Minigames, Party, and Settings from their corresponding clean panels, rows, buttons, headers, toggles, nodes, connectors, scroll controls, and accents. Use example images only as composition/state references or for a required glyph that has no usable clean counterpart; never bake example labels, player names, or values into runtime textures.
   - Preserve stock atlas pixels only where DMZ samples a required control for which the supplied bundle has no safe equivalent, and identify each fallback in the manifest rather than hiding it.
   - Emit deterministic page atlases, a dimensions/anchors manifest, and per-page contact/proof sheets; retain `--check` freshness validation.
   - The audit found the five “clean” other-menu PNG sets are RGBA files with alpha fixed at 255 despite their README calling them transparent. Treat that as a source limitation: isolate panel silhouettes/backgrounds deterministically and verify alpha in generated sprites; do not claim those originals are transparent. If a safe mask cannot preserve an element, keep only that element's bounded panel rectangle and document the exception.

3. **Separate NEON from the older generic theme at runtime**
   - Extend `DmzMenuThemeState`/the terminal blit remapper with an explicit mode-aware Neon V3 asset table for all five retained DMZ screens, while leaving `STOCK`, `THEME`, and `SCREEN` behavior unchanged.
   - Continue using exact screen class names and verified resource paths; avoid new broad mixin targets.
   - Ensure every navigation route, including routes opened from Character and routes back to Character, remains in NEON mode and receives the correct page art.
   - Keep the world visible behind menu chrome and do not introduce a full-screen opaque redesign image or baked example screen.

4. **Fix scaling for every existing Character Neon editor part**
   - Add one shared, pivot-defined transform helper for modular sprites and use it in both `XenoNeonStatsScreen` and `XenoNeonScreenPreview`; apply `partScale`, offsets, tint, and hidden state consistently to panels, headers, row shells, multiplier fields, plus controls, ring/divider, orbs, nameplate, navigation bases, and other sprite groups.
   - Define scaling around each part/group's center so resizing does not unexpectedly walk the part across the screen. Preserve relative spacing within repeated groups such as stat rows, orbs, and navigation buttons.
   - Apply `Part.CHARACTER` scale to DMZ's entity preview and compose it correctly with the scan-ring transform.
   - Make text anchors follow their parent group's transform while retaining their own text scale/font/bold/color override, avoiding accidental double-scaling.
   - Recalculate plus-button and navigation hover/click bounds from the same transformed rectangles used for rendering; hidden controls must not remain clickable.
   - Update preview selection bounds to include scaled extents, so the editor outline and controls describe what is actually on screen.
   - Preserve the current config schema, migration of shorter arrays, `partHidden`, and the 0.25–3.0 clamp.

5. **Expose honest editing scope**
   - Keep `/xenohud neon edit|reset`, the menu entry, and `HudSurfaces.DMZ_NEON` for the modular Character surface.
   - Label that editor as Character/Stats parts unless page-specific transforms for the retained stock DMZ screens are implemented and can move their content, widgets, scissors, and hitboxes together. Do not present atlas-only page chrome as independently repositionable controls.
   - Update `DmzMenuMode.NEON` documentation to state that all six pages now receive page-specific Neon V3 art, while only Character is the custom modular replacement.

6. **Add focused regression coverage and visual evidence**
   - Add pure tests for mode-specific screen/texture mapping, six-page asset coverage, unchanged fallback modes, scaling math, center pivots, transformed rectangles, hidden hitboxes, and generated-resource freshness where the project test setup permits.
   - Run both atlas generators normally and with `--check`, verify every output's dimensions, non-empty content, alpha behavior, and source ledger, then run the smallest focused Gradle tests followed by `gradlew test`.
   - Run a fresh client where feasible and manually open all six V menus at more than one GUI/menu scale; exercise representative interaction states and Character part scales below and above 1.0. Inspect `run/logs/latest.log` for fresh mixin/render errors.
   - Report visual/runtime checks as unverified if the menus were not actually opened. Recheck the very dirty working tree and list only touched paths; do not stage, commit, reset, delete, or overwrite unrelated user work.

## Notes and considerations

- Target remains Java 21, Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3, mod id `xenopixelsmod`.
- “That zip” is interpreted as the tracked `dragonmine_complete_ui_elements_master_bundle`, specifically Status V3 plus the five matching other-menu groups; the obsolete `tools/source/dmz_our_style_v2` is not a design source.
- The supplied “server menu” is the closest explicit source for DMZ's Party page; this mapping will be documented rather than represented as a native Party-labelled source.
- The existing five-screen texture-remap architecture is deliberately retained because duplicating DMZ's large private screen state and packet behavior would be riskier and would violate the request not to invent behavior.
- Individual scaling of the five retained DMZ screens' internal controls is out of scope unless render, scissor, widget, and input transforms can all be proven together. Their normal DMZ menu/global GUI scaling remains intact.
- No public API, network protocol, dependency version, or server behavior change is planned.
