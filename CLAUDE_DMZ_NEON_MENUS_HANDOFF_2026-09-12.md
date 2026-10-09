# Handoff — six-page Neon V3 menu chrome and neon character part scaling

**Date:** 2026-09-12
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `3fa456a20a43c56600a743e5cab332534af6a773`

Completes the outstanding parts of `plan_codex_1.md`. Plan of record for this session:
`C:\Users\Admin\.claude\plans\mossy-jumping-fern.md`.

## Current state

- **Nothing was committed, staged, reset or deleted.** HEAD is unchanged from the start of the
  session. All work is in the working tree.
- The tree was already very dirty on arrival (217 modified paths plus a large set of untracked
  files, all pre-existing user work). Only the paths listed under *Changes* were touched.
- `libs/dragonminez-2.1.3.jar` and `dragonminez_sha256` were not touched.
- No running client or server held classes from before the rebuild; nothing was left running.

## Changes

### Generator — `tools/gen_dmz_menu_themes.py`

Extended, not replaced. The existing THEME path is byte-for-byte unchanged (verified below).

- `NEON_PAGES` / `NEON_WIDGETS` / `CHAR_BUTTON_CELLS`: per-page source tables drawn from
  `dragonmine_complete_ui_elements_master_bundle/03_dragonmine_other_menus_clean_and_example/`.
- `dmz_contract()`: reads every `graphics.blit(...)` and every
  `.texture()/.textureCoords()/.textureSize()/.size()` builder chain out of
  `tools/generated/dmz_decompiled_full/` and writes them into the manifest. A UV that is a literal
  is recorded as a number; one that is a computed expression is recorded as the expression.
- `nine_slice_scaled()`: corner-preserving fill that scales the frame's corners by a single uniform
  factor, so a 240x209 panel fills a 141x213 slot without letterboxing or stretched brackets.
- `neon_slot()`, `load_art()`, `_art(..., cap=(y, rows))`: explicit crops per element.
- `proof_sheet()`: one per page under `tools/generated/dmz_neon_<page>_proof.png`, showing each
  panel, its header, the header composited over the panel at DMZ's own (+17, +10) offset, and the
  page's widget cells at their true size.

### Runtime

| File | Change |
|---|---|
| `client/hud/DmzMenuArt.java` | **new.** All mapping and split arithmetic, no Minecraft types. |
| `client/hud/DmzMenuThemeState.java` | now mode-aware; captures mode + DMZ canvas width per render pass; tries neon sheet, then THEME sheet, then leaves DMZ's art alone. |
| `mixin/compat/dmz/DmzScaledScreenWidthInvoker.java` | **new.** `@Invoker("getUiWidth")` on `ScaledScreen`. |
| `xenopixelsmod.compat.mixins.json` | registers the invoker in the `client` list. |
| `client/screen/neon/NeonPartTransform.java` | **new.** Shared centre-pivot transform + `Block` group transform. |
| `client/screen/neon/XenoNeonStatsScreen.java` | draws through the shared transform; groups carry their own contents; `+` hit rect == drawn rect. |
| `client/screen/XenoNeonScreenPreview.java` | shares that transform; `bounds()` is now the union of transformed rects. |
| `client/config/DmzMenuMode.java` | NEON javadoc states the real six-page scope. |
| `client/screen/HudSurfaces.java` | `DMZ_NEON_LAYOUT.name()` `"DMZ Neon"` → `"Neon Character"`. |
| `src/test/.../DmzMenuArtTest.java`, `.../neon/NeonPartTransformTest.java` | **new**, 21 tests. |

**No public API, network protocol, data schema, dependency or server behaviour change.**
`src/main/java/.../api/**` was not touched. `ModNetwork` was not touched.

## Verified

All commands run 2026-09-12 from the repository root.

| Command | Result |
|---|---|
| `py -3.14 tools/gen_dmz_menu_themes.py` | wrote 30 textures at 4x |
| `py -3.14 tools/gen_dmz_menu_themes.py --check` | `DMZ menu themes are up to date` |
| `./gradlew compileJava -PofflineMcMeta` | BUILD SUCCESSFUL |
| `./gradlew test -PofflineMcMeta` | BUILD SUCCESSFUL — **129 classes, 754 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew build jarJar serverJar -PofflineMcMeta` | BUILD SUCCESSFUL |
| `./gradlew buildApiExampleAddon -PofflineMcMeta` | BUILD SUCCESSFUL |

Content checks:

- **Server jar has 0 entries below `META-INF/jarjar/`** (`xenopixelsmod-Server-0.3.5-1.21.1.jar`,
  12,008,174 bytes, sha256 `3b9b8720310c37cb6944a41dcaa5b390f041a4af7fc857a10045d7c415288514`).
- Mod jar `xenopixelsmod-0.3.5-1.21.1.jar`, 36,155,402 bytes, sha256
  `26b5e82e46ba20ed65454a49d2ce994cc6cc008ed75c47a62017c70502eab7e0`; contains all 15
  `assets/xenopixelsmod/textures/gui/dmz_menus/neon/*.png`.
- **`git status --short` on `textures/gui/dmz_menus/` reports only the new `neon/` directory** — all
  15 tracked THEME sheets are byte-identical to HEAD, so THEME's look is provably untouched.
- `getUiWidth()` confirmed present via `javap` on `libs/dragonminez-2.1.3.jar`:
  `protected int getUiWidth();` on `com/dragonminez/client/gui/character/util/ScaledScreen.class`.
- The five per-page proof sheets were rendered and inspected; the crops were revised four times off
  what they showed (baked page titles, sheet captions, doubled badges).

### The panel-split bug, confirmed against source

`DmzMenuThemeState` split left from right on the literal `drawX < 160`. `ConfigMenuScreen` (line 546
of the tracked decompiled source) computes `getLeftPanelX()` as `getUiWidth() / 2 - 143`, and
`ScaledScreen.getUiWidth()` is dynamic, not the 320 minimum. At a 640 canvas the settings page's left
panel is drawn at x=177 and was handed the **right** panel's art. Fixed in NEON only, per your
decision; THEME keeps the literal and `DmzMenuArtTest.themeKeepsItsLegacyThresholdEvenWhereItIsWrong`
pins that it does. The other four menus draw at x=12 / `uiWidth - 158` and were never affected.

## Not verified

- **No client was launched. No menu was opened.** Nothing here is runtime-verified: not the neon art
  in game, not the panel split at a wide window, not the per-page widget sheets, not the corrected
  part scaling, not the editor's new bounds. Treat every visual claim as "generated and inspected as
  an image", not "seen in play".
- The new mixin produced no apply failure in the mod-loading phase of the test task, but
  `ScaledScreen` is loaded lazily and may not have been loaded in that run — so the invoker is
  **not** proven to apply. First in-game check should be that the settings page still themes at all.
- `run/logs/latest.log` was not regenerated or inspected.
- One pre-existing, unrelated mixin failure appears in the test log and is **not** from this work:
  `compat.mynpcs.ContainerNpcInvCuriosMixin` → `@Shadow method addSlot ... was not located in the
  target class espi.mynpcs.containers.ContainerNPCInv`.

## Known limitations, recorded rather than hidden

The manifest's `neon.fallbacks` list names every rectangle that deliberately keeps existing art, with
the reason. Summary:

- **Every "clean" PNG in all five other-menu packs is RGBA with alpha fixed at 255**, despite the
  pack README calling them transparent. They are auto-splits of larger labelled contact sheets, so
  file bounds routinely include a caption. Every crop is therefore stated explicitly and was read off
  the source's neon-border row/column profile — none is derived from alpha, and nothing in the
  manifest claims these sources are transparent.
- **Skills and quests keep DMZ's header pixels.** Neither pack ships an untitled header bar; the
  nearest files carry baked example text.
- **`party_right` reuses the left panel's body**, because the pack's right panel has a `???` heading,
  a progress row and prev/next arrows painted into it and `PartyMenuScreen` draws its own arrows and
  action button over that area.
- **`settings_right` reuses the options body**, because the values panel has a toggle pill and a
  scrollbar painted into every row and `ConfigMenuScreen` draws its own steppers at `rightPanelX+25`
  and `+108`.
- **Only party and minigames have a page-specific widget plate** (the 74x20 action button at UV
  `(0,28)`). The other three pages' `*_characterbuttons.png` are identical files (sha256 prefix
  `3d6623752099fd7e`, 8,792 bytes each) carrying the shared art. They are emitted per page anyway so
  that adding a plate later is a one-line table change rather than a runtime change.
- Skills, minigames and quests panels are **left open at the top** (no `cap`): in those three sources
  the frame's top rule runs through the 悟 badge, so lifting it would drag the title band with it.
  DMZ's own header bar sits across that edge.
- During a menu-switch transition `BaseMenuScreen` slides panels by up to ±190px with an
  `easeOutBack` overshoot. At a 320 canvas the overshoot can briefly carry the right panel past the
  midpoint. Transient, one frame or two, and equally present in THEME today.

## Next steps

1. Launch a client. `/xenohud menus neon`, then open all six V menus.
2. **Resize the window so DMZ's canvas exceeds 606 logical px** and re-check the *settings* page
   specifically — that is the case the split fix exists for, and the one most likely to reveal the
   invoker failing to apply.
3. Exercise a skill upgrade, a quest pan, a minigame launch, a party action and a settings toggle, to
   confirm DMZ still owns its own behaviour on all five themed pages.
4. `/xenohud neon edit`: set part scales below and above 1.0 on `statRows`, `statistics`, `summary`
   and `infoPanel`; confirm labels stay on their shells, the `+` clicks where it is drawn, and the
   selection outline covers the scaled art.
5. Re-run the same pass in `/xenohud menus theme` and `/xenohud menus stock` to confirm neither
   changed.
6. Read a fresh `run/logs/latest.log` for mixin or render errors before calling any of it verified.
7. Only then consider staging. Stage reviewed paths explicitly; never `git add -A` in this tree.
