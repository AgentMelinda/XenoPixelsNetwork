# Atlas UI — how to work on the generated panel UI

Updated: 2026-09-21 · Target: MC 1.21.1 / NeoForge 21.1.248 / DragonMineZ 2.1.3 / mod id `xenopixelsmod`

This is the working guide for the generated-panel UI system: the sprite atlas, the widget kit built
on it, and the screens that use it. It is written for agents picking this up cold.

**Evidence discipline.** Every claim below is either repo source, a decompiled jar this repo depends
on, or a command that was actually run and whose output was checked. Where something has *not* been
confirmed, it says so. Nothing here is in-world proof: at the time of writing **no screen in this
system has been visually verified in a running game**. See [What is not verified](#what-is-not-verified).

Related, and not superseded by this file:

- Root `doco.md` — MyNPCs feature documentation compiled from that mod's jar. The reference this UI
  is modelled on.
- `docs/doco.md` — UI Studio implemented-vs-needed inventory.
- `ai/skills/client-ui-assets.md` — the short client-asset rules; this file is its long form.

---

## 1. The one rule: sprites are never stretched

The atlas ships **one PNG per shape per size per palette**. Border proportions are baked in when the
PNG is generated, so scaling a sprite at runtime distorts its frame. This is stated by the asset
author in `tools/atlas-panels/UPSTREAM-README.txt`:

> Every blit is 1:1, no stretching — same convention the real DragonMineZ mod uses […] If you need a
> size that doesn't exist yet, add a new PanelSpec to dmz_atlas_generator.py's base_specs, rerun it,
> then rerun gen_java_enum.py — don't stretch an existing texture at runtime, the border proportions
> are baked in at generation time and will distort.

So when a layout needs a size the atlas does not have, **generate the size** (section 5). Do not
scale, and do not pick a wrong-sized sprite and hope.

### The bug this rule exists to prevent

`XenoAtlasSprites.blitSized` originally called the nine-argument `GuiGraphics.blit`. Verified against
the pinned decompiled source at `net/minecraft/client/gui/GuiGraphics.java:540`, that overload
forwards to the eleven-argument form with **sample size = destination size**:

```java
// GuiGraphics.java:540
blit(rl, x, y, u, v, width, height, texW, texH)
  -> blit(rl, x, y, /*destW*/width, /*destH*/height, u, v, /*uWidth*/width, /*vHeight*/height, texW, texH)
```

It therefore **crops and over-samples; it never scales**. Drawing `panel_huge` (280x360) into a
420x322 box sampled 140px past the right edge of the texture. In game this showed as a hard vertical
seam across the panel and doubled tab borders.

The scaling overload is `GuiGraphics.java:522`, and `blitSized` now uses it. But it is a **fallback,
not the layout mechanism** — the only caller that should reach it is the uniform-shrink guard for a
viewport too small to hold a frame (`XenoAtlasSprites.blitFitted` / `fittedSize`).

---

## 2. Where everything lives

| Path | What it is |
|---|---|
| `src/main/resources/assets/xenopixelsmod/textures/gui/atlas/` | 156 PNGs = 39 shapes x 4 palettes |
| `client/ui/atlas/XenoAtlasSprites.java` | Shape registry: name → `ResourceLocation` + native w/h; palette enum; blit helpers |
| `client/ui/atlas/Atlas{Button,Toggle,Cycle,TabStrip,Panel,Notice}.java` | The widget kit |
| `src/test/java/.../client/ui/atlas/` | `XenoAtlasSpritesTest`, `GeneratedSpriteFamiliesTest`, `AtlasWidgetStateTest` |
| `tools/atlas-panels/` | The generator, its manifest, and this repo's extra specs |

Paths above are relative to the repo root; Java paths omit
`src/main/java/net/bullettrain/xenopixelsmod/`.

### The registry is the source of truth for dimensions

`XenoAtlasSprites` stores a verified native width/height per shape. Two guards keep it honest:

- `XenoAtlasSpritesTest.everyRegisteredShapeHasAnExtractedPngForEveryTheme` fails if a registered
  shape has no PNG on disk for any of the four palettes.
- `GeneratedSpriteFamiliesTest` pins the generated families' widths and heights.

A registered shape with no PNG, or a PNG with no registration, is a bug the tests will catch. Adding
one without the other is the most common way to break this system.

The 24 original shapes were independently cross-checked: the `PanelTexture` enum shipped in
`java_integration.zip` agrees with `XenoAtlasSprites` on every shape name and every dimension.

---

## 3. The widget kit

All six widgets blit at native size. None of them scale their face.

| Widget | Sprite(s) | Notes |
|---|---|---|
| `AtlasButton` | any shape | Prefer the 5-arg constructor, which sizes the button from the sprite. The 7-arg form centres the native face inside larger bounds; the extra area only widens the click target. Hover is a palette swap to gold. |
| `AtlasToggle` | `mynpcs_button_row` 64x22 | State is a palette swap — green for yes, red for no. Extra width past the cell is label area. |
| `AtlasCycle` | `mynpcs_button_arrow` 22x20 + `mynpcs_button_row` 64x22 | Fixed 108px wide; it has no width parameter. Only the arrow cells step the value. |
| `AtlasTabStrip` | `tab_docked_w*` | Picks the narrowest generated width that holds each label, so cells vary per label without stretching. |
| `AtlasPanel` | any panel shape | `ofNative` first. `fittedInto` only for a viewport too small; shrinks uniformly. |
| `AtlasNotice` | `mynpcs_toast` 160x32 | Fixed width; long text is trimmed, the strip does not grow. |

### Text sizing in atlas controls

Atlas control labels use a common scale within their visual group. The NPC editor shares a scale
across controls on the same row; appearance subtabs, dialogue choices, sound choices, and each atlas
tab strip use one scale across their group. Cycle values, toggle states and labels, and stepper
values use the same fitting rules. The scale stops at 0.75; if text still does not fit, it ends in an
ellipsis and the full label or value is available in a tooltip. Cycle, toggle, and stepper narration
includes the row label and current value. Sprite sizes and click bounds do not change. Unit tests
cover common scale, the readable floor, ellipsis bounds, and the tab strip's generated-width fit.
Visual behavior still requires a fresh in-game check.

### State and hover are palette swaps, not geometry

The atlas draws one look per shape per palette, with no lit/unlit pair. So a state change swaps the
palette on the *same* sprite at the *same* size. An earlier `AtlasToggle` swapped `hex_badge` (40x40)
for `hex_badge_lg` (64x64) between off and on, which made the control resize as it was clicked. Do
not reintroduce that pattern.

---

## 4. Coordinate spaces — the subtle trap

There are two coordinate spaces in this codebase and mixing them silently breaks hit-testing.

### DragonMineZ `ScaledScreen` (virtual canvas)

`com.dragonminez.client.gui.character.util.ScaledScreen` is in `dragonminez-2.1.3.jar`, which this
repo already depends on. **Use the real class; do not vendor a copy** — `java_integration (1).zip`
ships a port for mods that lack DMZ, which we are not.

Verified via `javap` against that jar, it provides `getUiWidth()`, `getUiHeight()`, `getUiScale()`,
`toUiX(double)`, `toUiY(double)`, `toScreenCoord(double)`, `beginUiScale(GuiGraphics)`,
`endUiScale(GuiGraphics)`, and overrides all four mouse methods to convert window coordinates into
canvas coordinates before dispatching to widgets.

Canvas sizing, read from the ported source of the same class:

```
availableScale = min(guiWidth / getMinGuiWidth(), guiHeight / getMinGuiHeight())   // defaults 320, 240
uiScale        = clamp(sqrt(availableScale) * multiplier, getMinUiScale(), availableScale)
uiWidth        = guiWidth  / uiScale
uiHeight       = guiHeight / uiScale
```

At 1080p with GUI scale 2 this gives roughly **640x360**, and `uiWidth`/`uiHeight` never drop below
`getMinGuiWidth()`/`getMinGuiHeight()`. **This is why `panel_editor` (842x471) can never be a menu
frame** — it does not fit the canvas at any resolution. It is a full-screen backdrop shape.

`ScaledScreen.render` only walks its renderables; it never calls `renderBackground`, so it does not
apply the vanilla background blur. Screens draw their own dim (a plain `graphics.fill`). That is why
moving a screen from `UnblurredScreen` to `ScaledScreen` does not bring the blur back.

The house pattern, matching `ExamplePanelScreen` in the bundle and the DMZ screens already in this
repo:

```java
@Override
protected void init() {
    super.init();
    int canvasW = getUiWidth();      // lay everything out in these units
    int canvasH = getUiHeight();
    ...
}

@Override
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    graphics.fill(0, 0, width, height, 0xA0000000);   // dim in WINDOW units
    beginUiScale(graphics);
    ...                                                // draw in CANVAS units
    super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
    endUiScale(graphics);
}
```

Note the asymmetry: the full-screen dim uses `width`/`height` (window units, outside the scale),
everything else uses canvas units. Any custom bounds test in a mouse handler must convert first —
`ScaledScreen` converts for widgets, but your own `if (mouseX >= panelX ...)` is not a widget.

### Real screen space

Not every screen should move to the canvas. `XenoPartyHudEditScreen` deliberately stays on
`UnblurredScreen` because it edits `XenoPartyHudConfig.x/y/scale`, which are **real HUD
coordinates**, and previews them by calling the live `XenoPartyOverlay.renderCards` at those same
coordinates. Adding a virtual canvas there would put the preview, the drag maths, and the saved
values in three different spaces.

**Rule of thumb:** a screen that only draws itself belongs on `ScaledScreen`. A screen that edits or
previews real HUD coordinates stays in window space.

### Current assignment

| Screen | Space | Frame |
|---|---|---|
| `client/npc/XenoNpcEditorScreen` | `ScaledScreen` | `xeno_editor_panel` 600x320 |
| `client/screen/XenoPartyScreen` | `ScaledScreen` | `xeno_editor_panel` 600x320 |
| `client/screen/XenoPartyHudEditScreen` | `UnblurredScreen` (real HUD coords) | no frame; chrome only |
| `client/ui/studio/UiStudioScreen` | `UnblurredScreen` | full-screen bands + atlas chips |
| `client/ui/studio/DmzGuiStudioScreen` | `ScaledScreen` | unchanged DMZ chrome |
| `client/ui/studio/DmzGuiStudioAtlasScreen` | `ScaledScreen` | atlas chrome, opt-in |

`DmzFormTrainerScreen`, `XenoNeonStatsScreen` and `XenoDmzStatsScreen` were already on
`ScaledScreen` before this work — it was the existing house pattern for DMZ-style screens, not a new
invention.

---

## 5. Adding a new sprite size

This is the correct response to "the sprite I need does not exist at this size".

**Prerequisite:** `python -m pip install Pillow`.

1. Add a `PanelSpec` to `extra_specs()` in `tools/atlas-panels/xeno_extra_specs.py`, with a comment
   saying why that size is needed. Copy the shape knobs (`shape`, `radius`, `header_band`,
   `edge_bars`, `grid`, `shine_corner`) from the closest existing shape in
   `dmz_atlas_generator.py`'s `base_specs`, so the new size looks like the family it joins.
2. Run it:
   ```
   cd tools/atlas-panels
   python xeno_extra_specs.py --only xeno_quest_journal_panel xeno_quest_complete_rounded xeno_quest_complete_banner xeno_quest_toast --out ../../src/generated/resources/assets/xenopixelsmod/textures/gui/atlas
   ```
   It generates all four palettes for every spec and installs them.
3. Register each new shape in `XenoAtlasSprites` with the same dimensions.
4. Run `gradlew.bat test --tests "net.bullettrain.xenopixelsmod.client.ui.atlas.*" -PofflineMcMeta`.
   The asset-existence test catches a registration with no PNG.

### Why this is safe

Re-running the upstream generator unmodified reproduces **all 96 original PNGs pixel-identically** —
only PNG encoder metadata differs. `xeno_extra_specs.py` likewise reproduces this repo's generated
extra PNGs pixel-identically. Both were checked with a per-pixel `ImageChops.difference` comparison
on 2026-09-21, not by eye and not by file hash.

Do **not** re-run `dmz_atlas_generator.py` directly and copy its `panels/` output over the atlas: it
emits only the 24 base shapes and would not touch this repo's extras, but it is also the script the
manifest and `gen_java_enum.py` are keyed to. Leave it as the upstream reference and add through
`xeno_extra_specs.py`.

### Shapes this repo added

| Shape | Size | Why |
|---|---|---|
| `xeno_editor_panel` | 600x320 | Menu frame that fits the DMZ canvas; `panel_editor` (842x471) cannot |
| `tab_docked_w{20,28,36,44,52,60,68}` | w x 26 | Per-label tab widths, so tabs vary like the reference without stretching |
| `ui_chip_w{20,32,40,48,56,64,112}` | w x 18 | Dense studio toolbars where `mynpcs_button_row` (64x22) overflows |
| `mynpcs_button_row_w{96,128,176,256,360}` | w x 22 | Native-width MyNPCs editor rows for half-column hubs and full-width lists |

### Model kinds

`NpcCombatProfile` carries a model block (`modelKind`, `modelId`, `modelTexture`, `modelTint`,
`modelGlowing`) and `client/npc/XenoNpcRenderer` dispatches on it inside a single registered
renderer, because an `EntityRenderer` is chosen once at registration:

| Kind | Drawn by |
|---|---|
| `VANILLA` | `HumanoidModel`, with the profile's texture and `baseSize` scale |
| `GECKOLIB` | `XenoNpcGeoRenderer` + `XenoNpcGeoModel`, resolving geo/texture/animation from `modelId` |
| `ENTITY` | a cached stand-in of any registered `EntityType`, rendered through the shared dispatcher |

`ENTITY` is what supports other mods' models with no per-mod code. It guards against mimicking a
Xeno NPC (which would recurse) and falls back to the humanoid when an id does not resolve or a
third-party renderer throws, so a typo never makes an NPC invisible.

Tint is applied on the GeckoLib path only, via `GeoRenderer.getRenderColor`. Vanilla's
`LivingEntityRenderer` hard-codes its model colour (`LivingEntityRenderer.java:128`), so a
vanilla-path tint would need a mixin; that is deliberately not done, and the editor's Display tab
says so rather than offering a control that does nothing.

---

## 5b. The editor layout engine

`client/npc/editor/` holds the row model the NPC editor is built on, and any new paged screen should
reuse it rather than placing rows by hand.

- `EditorRow` — a sealed set of row *declarations* (`Heading`, `Text`, `Field`, `Toggle`, `Cycle`,
  `Action`). A row says what a control is and where its value goes, never where it sits.
- `EditorLayout` — takes the body rectangle and a row list, pairs narrow rows two per line, gives
  headings and informational text a full line, and splits the result into pages. Pure geometry, so
  `EditorLayoutTest` covers it without a client.

Two rules this encodes, both of which were real bugs:

**Never place a widget before the layout has resolved its rectangle.** Fields used to be constructed
at width 1 and resized afterwards, but `EditBox.setValue` runs `scrollTo` immediately, and at width 1
the inner width is negative (`getInnerWidth() = width - 8`), so `displayPos` was pushed to the end of
the string. The later resize never recomputes it, and every populated field rendered **blank**. Build
the widget once, at its final size.

**Reserve the footer out of the body budget.** The body height passed to `EditorLayout` is
`frame - header - footer`, so rows physically cannot reach the Save button or the bottom of the
frame. Content overflowing the panel was the original complaint.

## 6. Recipe: a new atlas screen

1. Extend `ScaledScreen` (or `UnblurredScreen` if it edits real HUD coordinates — section 4).
2. In `init()`, call `super.init()` first, then lay out in `getUiWidth()`/`getUiHeight()` units.
3. Pick a frame whose **native** size fits the canvas. Use `AtlasPanel.fittedInto(...)` so the
   common case is a 1:1 blit and only an undersized viewport shrinks, uniformly.
4. Build controls from the kit, preferring the constructors that size from the sprite.
5. In `render()`, follow the `beginUiScale` / `endUiScale` pattern above, passing `toUiX`/`toUiY`
   mouse coordinates to `super.render`.
6. If you add a custom mouse bounds test, convert with `toUiX`/`toUiY` first, and divide drag deltas
   by `getUiScale()`.

### Do not enable a control for a field that does not exist

Where the reference menu offers something the native model has no field for, render either
informational text or an exact **disabled** reference control. Never leave an enabled widget that
silently discards input. The NPC Inventory, navigation AI, global databases, Night, Linked, Scenes,
and Marks screens use disabled controls to preserve the screenshot order without claiming backing.

The Delete tab is backed by `XenoNpcDeletePacket`. It uses operator, range, and revision checks and
requires an in-screen confirmation before sending.

---

## 7. Invariants

Breaking any of these is a regression even if it compiles.

**Save completeness.** `XenoNpcEditorScreen`'s profile controls mutate `NpcCombatProfile` directly
and mark their serialized tag key dirty. `save()` sends only those dirty keys, and
`XenoNpcSavePolicy` validates every key and NBT type before the server merges the subset. This avoids
overwriting transform/mastery state changed while the editor was open. **Do not reintroduce shadow
profile fields**, and do not add a control without an allowlisted serialized key.

**Server authority.** `XenoNpcSavePacket.handle` requires operator level 2, a distance of 64 blocks
or less, a matching revision, an unlocked NPC, and an allowlisted key/type subset, then performs one
transactional mutation and one revision increment. `XenoNpcEditorLockPacket` owns lock changes.
Client code does not get to skip any of it.

**Packet channel order.** Registration order in `ModNetwork` is a sequential-channel invariant. Do
not reorder it.

**Studio non-send.** Neither `DmzGuiStudioScreen` nor `DmzGuiStudioAtlasScreen` sends
`CreateTechniqueC2S`. Both files mention it only in documentation; verified zero sends.

**Studio chrome is switchable, DMZ is default.** `DmzGuiStudioScreen` is the proven screen and keeps
DragonMineZ's real `ColorSlider` HSV controls. `DmzGuiStudioAtlasScreen` is a copy with atlas chrome
and, because the atlas ships no slider shape, bounded numeric boxes with step buttons instead — a
stated deviation, not an invented widget. Select with `/xenoui studio chrome dmz|atlas`; the config
key is `XenoClientConfig.dmzStudioChromeAtlas`, default `false`.

Do not flip that default, or delete either screen, until the atlas one has been confirmed in a
running game. The same principle applies to any future chrome replacement: **ship the new route
alongside the proven one and leave the proven one on by default.**

**Untracked work is unrecoverable.** Much of this system is untracked in git — at the start of the
2026-09-21 editor-parity work, `git status --porcelain` reported 579 dirty paths, 359 untracked,
including the whole
`client/ui/` tree, `client/npc/`, and every atlas PNG. Check the current figures yourself rather
than trusting those; the point is that git cannot recover these files once overwritten. Back up
the trees you are about to touch before editing, and never use `git add -A`, `git reset --hard`,
or a blanket checkout here.

---

## 8. Entity previews

Do not write new entity-in-GUI rendering. Two verified pieces already exist:

- `client/screen/StudioViewport.java` — orbit yaw/pitch, wheel zoom, pan. Its class doc explains why
  `InventoryScreen.renderEntityInInventoryFollowsMouse` is wrong for a large viewport (the `atan`
  saturates, and it restores only five of the eight entity rotation fields, leaking the rest into the
  world renderer). `StudioViewport` saves and restores all eight in a `finally`.
- `client/compat/npc/NpcFullDmzRenderer.renderPreview(LivingEntity, GuiGraphics, int x, int y,
  int scale, float yaw, float pitch, float partialTick, boolean showAura)` — DMZ-aware model and
  aura. Returns `false` when the client has no appearance state for that entity.

The NPC editor composes them: `StudioViewport` drives yaw/pitch/zoom, `renderPreview` draws, and
`StudioViewport.render` is the fallback when `renderPreview` returns `false`.

---

## 9. Validation

```
gradlew.bat compileJava -PofflineMcMeta
gradlew.bat test --tests "net.bullettrain.xenopixelsmod.client.ui.atlas.*" -PofflineMcMeta
gradlew.bat test -PofflineMcMeta
gradlew.bat build jarJar serverJar -PofflineMcMeta
gradlew.bat buildApiExampleAddon -PofflineMcMeta
```

Then confirm the server jar has zero `META-INF/jarjar/` entries.

All of the above passed on 2026-09-21. **None of it is evidence that anything renders correctly.**

---

## What is not verified

No screen in this system has been opened in a running game since the atlas rework. Specifically
unconfirmed:

- That the editor frame draws with no seam and no doubled tab borders.
- That no row draws outside the frame and the footer never overlaps a control, on every tab, and
  that `Page n / N` plus the wheel page a tab that overflows.
- **That the text fields now show their values.** They were rendering blank because of the width-1
  `EditBox` bug in section 5b; the cause is proven from the decompiled source but the fix is not
  yet confirmed on screen.
- That each model kind draws: a vanilla texture swap, a GeckoLib rig, and mimicking another mod's
  entity. Nothing about the model system has been run.
- That all ten MyNPCs-order tabs are clickable and correctly sized.
- That the live NPC preview renders, rotates, zooms, and that its aura toggle works.
- **That the "values not saving" bug is fixed.** The likeliest cause is now identified: the fields
  rendered blank, so a saved value looked lost on reopen. Two further changes back it up - saves send
  only the keys the editor changed, and every rejected save now answers instead of returning
  silently. None of that is confirmed in game. Edit a stat, save, reopen, and check.
- That the DMZ tab's Transform / Descend / Stack / Unstack actually change the NPC, and that the
  form cycles list real forms for the race.
- That the appearance sub-editor's parts and colours persist through the parent's Save.
- That the wand opens the editor from either hand. `XenoNpcEntity.mobInteract` now checks both hands;
  previously a wand in the off hand never got a turn, because vanilla runs `mobInteract` for the main
  hand first and returns early once it consumes (verified in the decompiled `Player.interactOn` and
  `Mob.interact`), and both branches consume — so the main-hand pass fell through to the report
  branch, printed `Name - role - Brain v5 - revision N`, and ate the click.
- That `XenoPartyScreen`, `XenoPartyHudEditScreen` and `UiStudioScreen` render and their controls
  work.
- That `DmzGuiStudioScreen` still opens by default with working HSV sliders, and that
  `/xenoui studio chrome atlas` opens the new one.

## Known gaps

- `BaseMenuScreen` (`com.dragonminez.client.gui.character.util.BaseMenuScreen`, also in the DMZ jar,
  extends `ScaledScreen`) offers a registered tab bar, an open/close zoom animation, and panel-slide
  transitions. Nothing here uses it. It is the obvious route to DMZ-style menu transitions, but it
  couples to DMZ's menu system, so adopt it deliberately rather than by default.
- `AtlasWidgetStateTest` exercises local pure-state records rather than the widget classes
  themselves. `GeneratedSpriteFamiliesTest` and `XenoAtlasSpritesTest` test the registry directly;
  widget behaviour beyond that is untested.
- Protocol is **77**. `XenoNpcEditorLockPacket` is appended after the existing registrations; do not
  reorder the sequential `ModNetwork` channel.
- The source bundles (`java_integration*.zip`, `panels_all_colors*.zip`) are kept at the repo root.
  `tools/atlas-panels/` holds the extracted generator, manifest and upstream README, so the zips are
  reference material rather than a build input.
