# DragonMine HD UI Single Elements V4

This pack breaks the latest redesigned UI into **single transparent PNG elements** for Minecraft HUD / overlay implementation.

## What you asked for
- break everything into single elements
- put it into a ZIP
- inside stats, do not bake numbers into the clean elements
- leave one example and one clean element per element type
- include buttons, icons, and reusable parts for Minecraft implementation

## Included groups
- `01_panels/`
- `02_headers/`
- `03_rows/`
- `04_buttons/`
- `05_icons/`
- `06_overlays/`

## Example vs Clean
- `example_*.png` = filled demo version showing the intended final look
- `clean_*.png` = blank/implementation-ready version with no dynamic numbers or labels baked in

## Notes about stats
The clean stat assets intentionally do **not** contain the changing numbers.
Use the clean row shells in code, then render:
- the stat label
- the value
- the multiplier

This is better for Minecraft HUD overlays.

## Notes about buttons
Each bottom navigation button is included:
- as an example version
- as a clean shell version
- with its icon also exported separately

## Scrollers
This specific redesign screen does **not** show a scroller element, so no scroller was present to separate from the source design.
If you want, I can make a matching scroll bar pack next.

## Suggested Minecraft usage
Suggested texture folder:
`assets/<modid>/textures/gui/dragonmine/`

Suggested layer order:
1. panel
2. header
3. row shell
4. icons
5. labels
6. values
7. overlay glow / scan ring

## Main files
### Panels
- `example_information_panel.png`
- `clean_information_panel.png`
- `example_statistics_panel.png`
- `clean_statistics_panel.png`
- `example_nameplate_dev_saiyan.png`
- `clean_nameplate.png`

### Rows
- `example_basic_info_row.png`
- `clean_basic_info_row.png`
- `example_information_stat_row.png`
- `clean_information_stat_row.png`
- `example_statistics_row.png`
- `clean_statistics_row.png`
- `example_bottom_stats_box.png`
- `clean_bottom_stats_box.png`

### Buttons
- one example and one clean version for each:
  - character
  - skills
  - quests
  - items
  - party
  - settings

### Icons
- navigation icons
- top-right orb icons
- information kanji circle
- statistics bars icon

### Overlays
- scan ring overlay
- divider accents

All PNGs are RGBA and transparent-background friendly.
