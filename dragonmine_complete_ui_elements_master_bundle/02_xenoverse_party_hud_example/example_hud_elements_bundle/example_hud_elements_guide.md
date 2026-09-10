# Example Xenoverse-Style HUD Elements — Asset Guide

This guide documents the **first HUD ZIP** (`example_hud_elements_bundle.zip`) and is meant to be kept with the textures while implementing the HUD in Minecraft or another game engine.

## 1. Coordinate / Measurement Rules

- All dimensions below are in **pixels**.
- `Canvas` is the complete PNG size.
- `Visible content` is the approximate non-transparent bounding area for RGBA files.
- Treat generated glow as padding; do not crop directly against the hard frame edge.
- For Minecraft GUI rendering, scale the texture at render time instead of repeatedly resizing the source PNG.
- Keep the original source files as the high-resolution master assets.

## 2. Suggested Runtime Sizes

These are practical starting points for a 1920×1080 screen. Scale with GUI scale / resolution.

| Element | Suggested rendered size |
|---|---:|
| Full player HUD | 300–420px wide, 110–160px high |
| Compact party HUD | 220–320px wide per player |
| Combat action button | 90–140px wide, 20–34px high |
| CTRL / ALT KI menu | 280–360px wide, 170–260px high |
| Portrait ring | 55–90px outside diameter |
| Ability icon | 22–38px square |
| P1/P2 badge | 28–50px wide |

For the current compact top-center party layout, a good initial target is **~300px wide per player HUD**, placed side-by-side with the portrait circles facing inward.

## 3. Circular Element Diameters

The portrait/orb PNGs include glow, so the file canvas is larger than the actual ring. These are approximate visual measurements from the current textures:

| File | Canvas | Approx. outer ring/orb diameter | Approx. inner portrait opening |
|---|---:|---:|---:|
| `example_20_portrait_ring_p1.png` | 250×235 | ~190–205px | ~145–160px |
| `example_21_portrait_ring_p2.png` | 250×235 | ~190–205px | ~145–160px |
| `example_22_energy_orb_orange.png` | 240×225 | ~175–195px | n/a |
| `example_23_energy_orb_blue.png` | 246×240 | ~180–200px | n/a |

### Recommended portrait crop
Use a **square portrait texture** and clip/mask it to a circle. If the ring is rendered at 72px outer diameter, start with a portrait diameter around **56–60px** and adjust visually.

## 4. Split HUD Elements

| File | Canvas | Visible content | Format |
|---|---:|---:|---|
| `example_00_full_transparent_asset_sheet.png` | 1536×1024px | 1536×995px | RGBA PNG |
| `example_01_player1_full_hud.png` | 765×295px | 765×295px | RGBA PNG |
| `example_02_player2_full_hud.png` | 761×295px | 753×290px | RGBA PNG |
| `example_03_player1_nameplate_frame.png` | 745×140px | 736×133px | RGBA PNG |
| `example_04_player2_nameplate_frame.png` | 745×140px | 736×131px | RGBA PNG |
| `example_05_hp_bar_with_label.png` | 511×75px | 493×60px | RGBA PNG |
| `example_06_ki_bar_with_label.png` | 497×70px | 476×70px | RGBA PNG |
| `example_07_stm_bar_with_label.png` | 495×72px | 476×65px | RGBA PNG |
| `example_08_hp_fill_bar.png` | 501×75px | 481×60px | RGBA PNG |
| `example_09_ki_segment_bar.png` | 487×75px | 463×75px | RGBA PNG |
| `example_10_stm_segment_bar.png` | 465×75px | 451×75px | RGBA PNG |
| `example_11_level_label_plate.png` | 229×99px | 207×82px | RGBA PNG |
| `example_12_level_number_45_plate.png` | 195×100px | 175×91px | RGBA PNG |
| `example_13_hp_label.png` | 158×80px | 145×64px | RGBA PNG |
| `example_14_ki_label.png` | 155×80px | 155×64px | RGBA PNG |
| `example_15_stm_label.png` | 161×80px | 155×65px | RGBA PNG |
| `example_16_ability_icon_purple.png` | 135×155px | 129×144px | RGBA PNG |
| `example_17_ability_icon_blue.png` | 140×153px | 140×135px | RGBA PNG |
| `example_18_ability_icon_green.png` | 140×150px | 140×131px | RGBA PNG |
| `example_19_ability_icon_orange.png` | 145×155px | 131×132px | RGBA PNG |
| `example_20_portrait_ring_p1.png` | 250×235px | 243×235px | RGBA PNG |
| `example_21_portrait_ring_p2.png` | 250×235px | 244×235px | RGBA PNG |
| `example_22_energy_orb_orange.png` | 240×225px | 240×222px | RGBA PNG |
| `example_23_energy_orb_blue.png` | 246×240px | 246×233px | RGBA PNG |
| `example_24_corner_bracket_orange.png` | 230×180px | 230×167px | RGBA PNG |
| `example_25_corner_bracket_blue.png` | 190×175px | 190×175px | RGBA PNG |
| `example_26_corner_chevron_orange.png` | 190×143px | 190×131px | RGBA PNG |
| `example_27_empty_plate_blue.png` | 350×95px | 350×95px | RGBA PNG |
| `example_28_empty_plate_orange.png` | 355×100px | 355×100px | RGBA PNG |
| `example_29_empty_plate_center.png` | 310×120px | 310×120px | RGBA PNG |
| `example_30_p1_badge.png` | 190×170px | 190×168px | RGBA PNG |
| `example_31_p2_badge.png` | 190×165px | 190×161px | RGBA PNG |
| `example_32_arrow_orange.png` | 115×140px | 115×111px | RGBA PNG |
| `example_33_arrow_blue.png` | 110×120px | 110×120px | RGBA PNG |
| `example_34_energy_streak_orange.png` | 85×139px | 85×126px | RGBA PNG |
| `example_35_energy_streak_blue.png` | 76×214px | 60×203px | RGBA PNG |

## 5. Action Buttons With Text

These are high-resolution master renders. Their canvas is 1536×1024, but the visible button occupies mostly the middle horizontal strip.

| File | Canvas | Visible content | Format |
|---|---:|---:|---|
| `example_back_with_text.png` | 1536×1024px | 1496×403px | RGBA PNG |
| `example_charge_with_text.png` | 1536×1024px | 1502×369px | RGBA PNG |
| `example_chase_with_text.png` | 1536×1024px | 1495×377px | RGBA PNG |
| `example_combo_with_text.png` | 1536×1024px | 1472×316px | RGBA PNG |
| `example_ki_cancel_with_text.png` | 1536×1024px | 1471×342px | RGBA PNG |
| `example_vanish_with_text.png` | 1536×1024px | 1481×371px | RGBA PNG |
| `example_z_burst_with_text.png` | 1536×1024px | 1463×355px | RGBA PNG |

### Current button mapping

| File | Intended control |
|---|---|
| `example_vanish_with_text.png` | `A/D` — Vanish |
| `example_chase_with_text.png` | `W` — Chase |
| `example_back_with_text.png` | `S` — Back |
| `example_combo_with_text.png` | `ATK` — Combo counter |
| `example_z_burst_with_text.png` | `V` — Z-Burst |
| `example_ki_cancel_with_text.png` | `C` — Ki Cancel |
| `example_charge_with_text.png` | `R/M` — Charge |

### Recommended implementation
For production, use the **blank button shell** and draw the control key + action name with the Minecraft font renderer. This prevents blurry baked text when UI scale changes and allows rebinding.

## 6. Blank Action Elements

| File | Canvas | Visible content | Format |
|---|---:|---:|---|
| `example_blank_button_style_01.png` | 1536×1024px | 1497×375px | RGBA PNG |
| `example_blank_button_style_02.png` | 1536×1024px | 1525×343px | RGBA PNG |
| `example_blank_button_style_03.png` | 1536×1024px | 1523×401px | RGBA PNG |

Use these as generic button backgrounds. Add keybind and action text at runtime.

## 7. CTRL / ALT KI Menus

| File | Canvas | Visible content | Format |
|---|---:|---:|---|
| `example_ki_alt_menu_with_text.png` | 1254×1254px | 1209×894px | RGBA PNG |
| `example_ki_ctrl_menu_with_text.png` | 1536×1024px | 1396×789px | RGBA PNG |
| `example_ki_menu_blank_style_01.png` | 1536×1024px | 1488×972px | RGBA PNG |
| `example_ki_menu_blank_style_02.png` | 1536×1024px | 1484×838px | RGBA PNG |

### Recommended menu layout

- Header height: ~15–20% of menu height.
- Four rows beneath the header.
- Selected row should use the brighter cyan/blue version.
- Left key index block should occupy ~12–16% of row width.
- Right status / availability icon should occupy ~8–12% of row width.
- Keep skill name text in the center region.

Suggested runtime dimensions for the menu shown in your screenshots: **~315×190px** at 1080p, then scale with GUI scale.

## 8. Sheets and Reference Images

| File | Canvas | Visible content | Format |
|---|---:|---:|---|
| `example_hud_asset_sheet.png` | 1254×1254px | 1254×1199px | RGBA PNG |
| `example_party_hud_battle_scene.png` | 1672×941px | n/a | RGB PNG |
| `example_party_hud_concept_scene.png` | 1672×941px | n/a | RGB PNG |
| `example_party_hud_sprite_sheet.png` | 1536×1024px | 1532×987px | RGBA PNG |
| `example_separate_elements_sheet.png` | 1536×1024px | 1420×1021px | RGBA PNG |
| `example_transparent_asset_sheet.png` | 1536×1024px | 1536×995px | RGBA PNG |

These files are reference/sprite-sheet material rather than single runtime elements.

## 9. Minecraft Texture Layout

Suggested resource structure:

```text
assets/<modid>/textures/gui/xenoverse/
├── party/
│   ├── player_hud_left.png
│   ├── player_hud_right.png
│   ├── portrait_ring_p1.png
│   └── portrait_ring_p2.png
├── bars/
│   ├── hp_fill.png
│   ├── ki_segments.png
│   └── stamina_segments.png
├── combat/
│   ├── button_blank.png
│   ├── button_selected.png
│   └── button_disabled.png
├── ki_menu/
│   ├── panel_blank.png
│   ├── row_normal.png
│   └── row_selected.png
├── icons/
└── effects/
```

## 10. Scaling Formula

Use a reference design resolution such as 1920×1080:

```text
scaleX = screenWidth  / 1920.0
scaleY = screenHeight / 1080.0
uiScale = min(scaleX, scaleY)
```

Then multiply the desired design-space position and size by `uiScale`.

Example: a 120×28 combat button at 1920×1080 becomes approximately 80×19 at 1280×720.

## 11. Nine-Slice / Stretching Notes

Do **not** stretch the entire button texture horizontally if you can avoid it because the angled ends and glows will distort.

Preferred approach:

1. Keep the left control-key cap fixed-width.
2. Keep the right arrow/end-cap fixed-width.
3. Stretch only the dark center panel.
4. Draw the blue/orange trim as part of the fixed caps or use nine-slice regions.

Suggested conceptual split for a 120px-wide runtime button:

```text
[ 28px key cap ][ 72px stretchable center ][ 20px end cap ]
```

## 12. Layering Order

Recommended render order:

1. Base black/navy panel
2. Outer metallic frame
3. Orange/cyan glow trim
4. HP / KI / STM empty track
5. Filled meter
6. Portrait / icon
7. Key labels and action names
8. Numeric values / percentages
9. Selected highlight / pulse animation

## 13. Color / State Variants To Add Later

Create these states before final integration:

- `normal`
- `hover` / `selected`
- `pressed`
- `disabled`
- `cooldown`
- `low_hp`
- `low_ki`
- `low_stamina`
- `party_member_down`

## 14. Recommended File Naming Convention

Continue using the `example_` prefix while prototyping. For production, rename by semantic role:

```text
example_combat_button_blank.png
example_combat_button_selected.png
example_ki_menu_panel_blank.png
example_ki_menu_row_normal.png
example_ki_menu_row_selected.png
example_party_portrait_ring_p1.png
example_party_portrait_ring_p2.png
example_party_hp_fill.png
example_party_ki_segments.png
example_party_stm_segments.png
```

## 15. Important Asset Note

Some generated files have a large transparent canvas surrounding the visible UI. For game use, either:

- crop the transparent padding once and store the cropped master, or
- keep the source master and use UV coordinates / source rectangles.

The split HUD elements are already much closer to runtime-ready dimensions than the large 1536×1024 button/menu masters.
