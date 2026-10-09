# Rush Combo Strikes and Custom DragonMineZ Menus

**Date:** 2026-09-20  
**Mod:** XenoPixels (`xenopixelsmod`) for Minecraft 1.21.1 / NeoForge 21.1.248 / DragonMineZ 2.1.3

This is a tutorial for the work done in the 2026-09-20 session: grant-gated Budokai Tenkaichi 3-style rush/combo strikes registered as DragonMineZ attacks, and a DMZ-style UI Studio that authors custom menus.

In-game listing, combo playback, and menu swap were **not** proven on a fresh client in that session. Commands and JSON below match the code as of 2026-09-20.

---

## 1. Rush combo (BT3-style strike chain)

### What it is

A granted DragonMineZ **strike attack** that rushes to the lock-on target, lands a 3/4-hit string made of the existing Xeno rush attacks, knocks the target diagonally away and up (steep look-down slams down), then auto-rushes in once more for a second string.

| Skill id | DMZ strike id | String |
|---|---|---|
| `rushcombo` | `xenopixelsmod:rush_combo` | `rush_left` → `rush_right` → `rush_breaker` → `rush_finisher` |
| `liftcombo` | `xenopixelsmod:lift_combo` | same rush beats, then launch + chase |

The four older rush strikes stay in the Attacks list too:

- `xenopixelsmod:rush_left`
- `xenopixelsmod:rush_right`
- `xenopixelsmod:rush_breaker`
- `xenopixelsmod:rush_finisher`

### Direct key (`/xenobind`)

```
/xenobind list
/xenobind rushcombo true
/xenobind liftcombo true
```

Then bind **Rush Combo** / **Lift Combo** in Controls (they ship unbound). Direct keys are on by default once bound; `/xenobind rushcombo false` turns the key off and leaves the DMZ Attacks slot as the only route.

### Enable and grant (required)

Combo routes start **off**. Players cannot `/xenoskill unlock rushcombo` with skill points. An operator grants the first level.

```
/xenoserver set comboRoutesEnabled true
/xenoskill grant rushcombo <player>
```

Then open DragonMineZ **Attacks**, equip `xenopixelsmod:rush_combo`, and fire the equipped strike.

Lift combo:

```
/xenoskill grant liftcombo <player>
```

Revoke:

```
/xenoskill revoke rushcombo <player>
```

Permission nodes:

- Grant/revoke: `xenopixelsmod.skill.exclusive.grant` (OP)
- Use: `xenopixelsmod.skill.rushcombo.use` / `xenopixelsmod.skill.liftcombo.use` (everyone by default)

The four old rush strikes still auto-unlock on login unless you set `rushAutoUnlock` false, then they need `/xenoskill grant rush`.

### Mastery

The first level is grant-only. After that, levels **2 and 3** can be bought with `/xenoskill unlock rushcombo` using skill points. Combo hits also add DragonMineZ technique XP. Knockback scales with mastery (`1.0 + 0.15 × (level − 1)`).

### Range and distance (no upper cap)

`0` on **start/hit range** means unlimited. `0` on **knock travel** means no send on that axis. Any positive number is allowed; there is no max.

Start range is **not** how far the opponent flies.

```
/xenoserver set comboRouteRange 0
/xenoserver set comboRouteHitRange 0
```

| Key | Meaning | Default |
|---|---|---|
| `comboRouteRange` | How far you may **start** the combo. `0` = unlimited | 16 |
| `comboRouteHitRange` | How close you must be to **land hits** after the rush-in. `0` = unlimited | 4.5 |
| `rushComboKnockTravel` | Rush-combo horizontal send (how far the opponent is thrown) | 1.65 |
| `rushComboKnockUp` | Rush-combo upward send | 1.85 |
| `rushComboKnockDown` | Rush-combo downward send (steep look-down) | 0.8 |
| `liftComboKnockTravel` | Lift-combo horizontal send | 0.4 |
| `liftComboKnockUp` | Lift-combo upward send | 0.9 |
| `liftComboKnockDown` | Lift-combo downward send | 0.8 |

Legacy ids `comboRouteKnockbackDistance` / `Up` / `Down` still set the **rush** trio. Aliases: `rushcomborange`, `comborange`, `rushcombohitrange`, `rushcomboknock`, `liftcomboknock`.

```
/xenoserver set rushComboKnockTravel 2.5
/xenoserver set rushComboKnockUp 2.0
/xenoserver set liftComboKnockTravel 0.8
/xenoserver set liftComboKnockUp 2.5
```

### Knockback path

Look pitch vs `rushKnockbackVerticalPitch` (default 50°):

- Steep look-**down** (pitch ≥ threshold) → away on XZ plus `-down`
- Every other look, including steep look-**up** → away on XZ plus `+up` (diagonal launch, not a vertical lift)

The path is flattened XZ away from the attacker plus an explicit Y. It is not a look-scaled 3D vector.

Per-rush-attack distances (the four rush *strikes*, not combo finishers; also no upper cap):

```
/xenoserver set rushKnockbackLeftRight 0.35
/xenoserver set rushKnockbackLeftRightUp 0.12
/xenoserver set rushKnockbackBreaker 0.75
/xenoserver set rushKnockbackBreakerUp 0.85
/xenoserver set rushKnockbackFinisher 1.55
/xenoserver set rushKnockbackFinisherUp 0.55
/xenoserver set rushKnockbackDown 0.8
/xenoserver set rushKnockbackVerticalPitch 50
```

### Other combo toggles

```
/xenoserver set comboRouteHitCount 3
/xenoserver set comboRouteAutoReapproach true
/xenoserver set comboRouteMaxReapproach 1
/xenoserver set comboRouteKiCost 25
/xenoserver set comboRouteCooldownTicks 80
/xenoserver set rushcomboEnabled true
/xenoserver set liftcomboEnabled true
```

Hit count is 3 or 4. Auto re-rush happens at most once by default.

### Left-click mash

Unchanged. Default combat controller is still `legacy`. These combos are equipped DMZ strikes, not a replacement for mash.

---

## 2. Custom DragonMineZ menus (UI Studio)

### What it is

`/xenoui studio` opens a DragonMineZ ScaledScreen editor (TechniqueCreator plate, `menunpc.png`, 74×20 `TexturedTextButton`s, HSV sliders). It writes JSON packs under `config/xenopixelsmod/ui/`. A document with `kind: "dmz_menu"` can replace a DragonMineZ V-key page when menu mode is `studio`. It does **not** create ki techniques.

DragonMineZ still owns technique-creator validation, wishes, race selection, and skill unlock math. Authored menus cannot grant exclusive combat skills.

`/xenohud menus stock` always restores DragonMineZ’s own screens.

### Open the editor

```
/xenoui studio
```

Chrome (right-hand DMZ creator plate, canvas on the left):

- Studio opens on a **new DMZ menu** (centered `menunpc.png` plate), not the old top-left HUD
- **NEW** — new V-key menu (`kind=dmz_menu`, 365×293, DMZ plate)
- **HUD** — new overlay (`kind=hud`, corner plate, not `demo_hud`)
- **AS** — save as the id in the id box (letters/numbers/underscore)
- **MENU** loads the bundled stats example (`xeno_stats`)
- **PAGE** / arrows cycle `stats|skills|quests|party|options|custom`
- **SAVE** writes `config/xenopixelsmod/ui/<id>.json` only if the document validates
- Pack HUD overlay is hidden while Studio is open. After close: `/xenoui enable true` and `/xenoui hud <id>`

### Create a custom DMZ menu (in Studio)

1. `/xenoui studio` (already a blank DMZ menu)
2. Edit widgets on the centered plate. **NEW** starts another menu; **HUD** starts an overlay.
3. Type an id (e.g. `my_stats`) and click **AS**, or **SAVE** to keep the generated id.
4. For a V-page: set page with **PAGE**, then `/xenohud menus studio` and press V.
5. For an overlay: **HUD**, **AS** `my_hud`, then `/xenoui hud my_hud` and `/xenoui enable true`.
6. Add a **Tab** (`NAV_TAB`) with action `dmz_page:skills` to jump to another authored page.
7. Add a **Button** with action `close` to leave a menu.

The editor seeds `config/xenopixelsmod/ui/xeno_stats.json` from the jar on first run if that file is missing.

### Create a custom DMZ menu (JSON by hand)

Put a file at `config/xenopixelsmod/ui/my_stats.json`:

```json
{
  "version": 1,
  "id": "my_stats",
  "kind": "dmz_menu",
  "page": "stats",
  "canvasW": 1920,
  "canvasH": 1080,
  "root": {
    "id": "root",
    "type": "ROOT",
    "x": 0,
    "y": 0,
    "w": 320,
    "h": 180,
    "anchor": "TOP_LEFT",
    "visible": true,
    "children": [
      {
        "id": "panel",
        "type": "PANEL",
        "x": 8,
        "y": 24,
        "w": 240,
        "h": 140,
        "color": "#E6080B12"
      },
      {
        "id": "title",
        "type": "TEXT",
        "x": 16,
        "y": 32,
        "w": 160,
        "h": 12,
        "text": "CHARACTER",
        "color": "#FFD54A"
      },
      {
        "id": "name",
        "type": "STAT_ROW",
        "x": 16,
        "y": 50,
        "w": 200,
        "h": 14,
        "text": "Name",
        "bind": "player.name"
      },
      {
        "id": "level",
        "type": "STAT_ROW",
        "x": 16,
        "y": 66,
        "w": 200,
        "h": 14,
        "text": "Level",
        "bind": "player.level"
      },
      {
        "id": "hp",
        "type": "PROGRESS_BAR",
        "x": 16,
        "y": 102,
        "w": 180,
        "h": 10,
        "bind": "player.hpPercent"
      },
      {
        "id": "tab_stats",
        "type": "NAV_TAB",
        "x": 8,
        "y": 6,
        "w": 48,
        "h": 14,
        "text": "Stats",
        "action": "dmz_page:stats"
      },
      {
        "id": "tab_skills",
        "type": "NAV_TAB",
        "x": 58,
        "y": 6,
        "w": 48,
        "h": 14,
        "text": "Skills",
        "action": "dmz_page:skills"
      },
      {
        "id": "close",
        "type": "BUTTON",
        "x": 292,
        "y": 6,
        "w": 20,
        "h": 14,
        "text": "X",
        "action": "close"
      }
    ]
  }
}
```

Reload packs:

```
/xenoui reload
```

`id` in the JSON must match the filename without `.json` for easy finding; the runtime keys by the `id` field.

### Widget types

| Type | Use |
|---|---|
| `ROOT` / `PANEL` | Chrome |
| `TEXT` | Label or bound text |
| `STAT_ROW` | Left label (`text`) + bound value |
| `PROGRESS_BAR` | Fill from a percent bind |
| `NAV_TAB` | V-menu tab; action `dmz_page:<page>` |
| `SKILL_SLOT` | Display-only slot chrome (cannot unlock skills) |
| `BUTTON` | Click action |
| `IMAGE` / `ICON` | `texture` is a resource location |
| `PORTRAIT` | Player face |
| `HBOX` / `VBOX` / `SCROLL` | Layout |

### Bindings (allow-list)

Unknown binds fail validation instead of drawing zero.

Player: `player.name`, `player.level`, `player.form`, `player.health`, `player.maxHealth`, `player.hpPercent`, `player.ki`, `player.maxKi`, `player.kiPercent`, `player.stm`, `player.maxStm`, `player.stmPercent`, `player.release`, `player.releasePercent`, `player.sparking`, `player.sparkingActive`, `player.dmzPresent`, plus vanilla food/air/armor/xp/gamemode keys.

Master context: `master.name`, `master.id`, `master.nearby`, `master.prerequisite`.

### Actions

| Action | Effect |
|---|---|
| `open_document:<id>` | Open that pack as a screen |
| `dmz_page:stats` (or skills/quests/party/options/custom) | Open the document assigned to that V-page |
| `master_menu:<id>` | Same as open_document (master flow) |
| `play_clip:<clip>` | Play a Xeno animation clip |
| `close` | Close the screen |

Packs cannot run commands, mutate inventory, or grant skills.

### How a page is assigned

On `/xenoui reload` (and after SAVE), every loaded document with `kind: "dmz_menu"` is assigned by its `page` field. One document per page; later loads overwrite.

| `page` | Replaces this DragonMineZ screen |
|---|---|
| `stats` | Character stats (V-key character page) |
| `skills` | Skills menu |
| `quests` | Quest tree |
| `party` | Party menu |
| `options` | Config / settings menu |
| `custom` | Not swapped onto a V-page; open with `/xenoui screen <id>` |

Unassigned pages keep DragonMineZ’s own screen.

### Open the menu

**A. Preview any document (does not change V-key):**

```
/xenoui screen xeno_stats
/xenoui screen my_stats
```

**B. Replace the V-key character page with your authored stats menu:**

```
/xenoui reload
/xenohud menus studio
```

Then press **V** (DragonMineZ character menu). If a `dmz_menu` with `"page": "stats"` is loaded, that document opens instead of DragonMineZ’s stats screen. Skills/quests/party/options do the same when a document is assigned to that page.

**C. Restore DragonMineZ:**

```
/xenohud menus stock
```

Other modes (unchanged): `theme`, `screen` (alias `v3` / `bt3`), `neon`.

### Overlay HUD packs (not V-menus)

Documents with `"kind": "hud"` are the in-world overlay, off until:

```
/xenoui enable true
/xenoui hud demo_hud
```

F1 still hides it. This is separate from DMZ V-menus.

---

## 3. Quick operator checklist

Rush combo:

1. Restart so the strike-skills mixin is loaded.
2. `/xenoserver set comboRoutesEnabled true`
3. `/xenoskill grant rushcombo <player>`
4. Equip `xenopixelsmod:rush_combo` in DragonMineZ Attacks.
5. Optional: `/xenoserver set comboRouteRange 0` and `comboRouteHitRange 0` for unlimited range.

Custom menu:

1. `/xenoui studio` → MENU → edit → SAVE
2. Confirm `kind` is `dmz_menu` and `page` is `stats` (or another V-page)
3. `/xenoui reload`
4. `/xenoui screen <id>` to preview, or `/xenohud menus studio` then V to play it
5. `/xenohud menus stock` to go back

---

## 4. Not verified in-game (2026-09-20)

- Fresh client showing rush combo on the DragonMineZ Attacks tab
- Full rush-in → 3/4 hits → knockback → auto re-rush chain
- `/xenohud menus studio` swapping the V-key page
- Unlimited range against a distant dummy

Unit tests for grant gates, combo routes, strike-id lists, knockback path, range `0` = unlimited, and `dmz_menu` JSON validation passed in that session (`./gradlew.bat test -PofflineMcMeta`).
