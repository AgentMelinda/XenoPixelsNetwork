# DragonMine Z Redesigned UI — Single Elements V3

This pack is the fully broken-apart version of the redesigned UI.

## What was requested
- Every reusable UI piece exported as its own PNG.
- **Statistics panel:** only **one filled example row** (`example_melee_dmg_row.png`), plus a **clean row for every statistic**.
- **Information stats:** every stat has:
  - a clean row,
  - a filled example row,
  - a separate label PNG,
  - a separate value PNG,
  - a separate multiplier PNG.
- Multipliers are included next to **every Information stat example**, as requested.

## Information stat example values
- STR — 126,727 — **x3.9**
- SKP — 130,361 — **x3.9**
- RES — 32,203,590 — **x3.2**
- VIT — 10,006,999 — **x1**
- PUR — 86,939 — **x3.9**
- ENE — 10,011,999 — **x1**
- TPC — 37,638,882 — **x1**

The original screenshot did not visibly show a bonus beside every row. To satisfy the request that **every stat has a multiplier**, rows without an explicit bonus are represented as **x1**.

## Rendering recommendation for Minecraft
Render in this order:

1. clean panel/frame
2. clean row
3. plus button / icon
4. selection/highlight overlay
5. stat label
6. dynamic numeric value
7. multiplier
8. optional glow / animation overlay

This avoids baking changing values into textures.

Suggested path:

```text
assets/<modid>/textures/gui/dragonmine/
```

## Main folders
- `00_reference/` — full UI reference
- `01_panels/` — full Information and Statistics panels
- `02_headers/` — isolated headers
- `03_information_basic_rows/` — Level, TPs, Form, Class
- `04_information_stat_rows/` — STR, SKP, RES, VIT, PUR, ENE, TPC
- `05_statistics_rows/` — right-side statistics
- `06_nameplate/` — player/race top bar
- `07_navigation_buttons/` — Character, Skills, Quests, Items, Party, Settings
- `08_icons/` — small orb/status icons
- `09_micro_elements/` — plus button, multiplier field, standalone labels/values/multipliers
- `10_overlays/` — scan ring and divider accents

## Clean vs example
- `clean_*.png` = no dynamic text/numbers baked in.
- `example_*.png` = demonstration of how the element should look when populated.

All PNG files use RGBA transparency.
