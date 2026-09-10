# Vendored source art — our-style bundle v2

Provenance: `C:\Users\Admin\Downloads\Compressed\dragonminez_our_style_clean_example_dimensions_2.zip`,
supplied 2026-09-09. Only the files `tools/gen_dmz_neon_atlas.py` actually consumes are vendored
here, mirroring the ZIP's own relative paths — the same policy `REPO_HANDOFF.md` records for
`tools/source/dmz_our_style/`.

| here | in the ZIP |
| --- | --- |
| `clean/latest_split/` | `_OUR_STYLE_UI_ELEMENTS/clean/latest_split/` |
| `clean/redesign_v3/` | `_OUR_STYLE_UI_ELEMENTS/clean/master_previous/04_dragonmine_status_redesign_v3/dragonmine_redesign_single_elements_v3/{09_micro_elements,10_overlays}/` |
| `example/current_example_full_hud.png` | `_OUR_STYLE_UI_ELEMENTS/example/latest/current_example_full_hud.png` |
| `ALL_ELEMENTS_DIMENSIONS.csv` | `_OUR_STYLE_UI_DOCS/ALL_ELEMENTS_DIMENSIONS.csv` |

`clean/latest_split/` is the bundle's own split of `current_clean_asset_sheet.png` into transparent
pieces. The generator's `SPRITES` table records what each numbered piece is; the ones it does not
list are ring sub-arcs, loose row shells and corner accents that the whole-slab panels already
contain.

`example/current_example_full_hud.png` is a **reference render, not a texture**. It carries baked
numbers, and per the bundle's own README those must be drawn in code over the clean shells. It is
vendored solely so the generator can sample the design's text colours from it at recorded
coordinates instead of anyone choosing values that look close.

`clean/redesign_v3/` holds the standalone multiplier field, plus button, divider and scan ring from
the earlier v3 kit, kept as the fallback for anything the split does not cover cleanly. The neon
screen does not currently draw any of them; the slabs already include their own plus buttons and
dividers.

`ALL_ELEMENTS_DIMENSIONS.csv` is the bundle's dimension record — width, height and non-transparent
alpha bounds for every PNG it ships. Consult it before re-measuring a crop by hand.
