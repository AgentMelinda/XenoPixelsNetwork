"""Generator self-tests: PNG round-trip, polish determinism, dest names, sizes."""
from __future__ import annotations

import tempfile
from pathlib import Path

from .atlas import ATLAS, REGIONS, paint_atlas
from .gecko import REGIONS as GECKO_REGIONS
from .gecko import SIZE as GECKO_SIZE
from .gecko import paint_controller, paint_glowmask
from .painters import SIZE, paint, wing_panel
from .pngio import read_png, write_png
from .recipes import ATLAS_DEST, GECKO_DEST, GECKO_GLOW_DEST, RECIPES, dest_ok
from .scanline import remaster


def run() -> int:
    failures: list[str] = []

    pixels = [[((x * 17) % 256, (y * 13) % 256, (x + y) % 256, 255 if y > 0 else 0)
               for x in range(8)] for y in range(8)]
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "roundtrip.png"
        write_png(path, pixels)
        decoded = read_png(path)
        if decoded != pixels:
            failures.append("pngio round-trip mismatch")

    src = wing_panel()
    if len(src) != SIZE or len(src[0]) != SIZE:
        failures.append(f"painter size {len(src[0])}x{len(src)} != {SIZE}")
    a = remaster(src, 1)
    b = remaster(src, 1)
    if a != b:
        failures.append("remaster is not deterministic")
    if len(a) != SIZE or len(a[0]) != SIZE:
        failures.append(f"polish size {len(a[0])}x{len(a)} != {SIZE}")

    painted = paint("guidance_front")
    if len(painted) != SIZE:
        failures.append("guidance_front is not 128")

    for recipe in RECIPES:
        if not dest_ok(recipe.dest):
            failures.append(f"bad dest name {recipe.dest.name}")
    for dest in (ATLAS_DEST, GECKO_DEST, GECKO_GLOW_DEST):
        if not dest_ok(dest):
            failures.append(f"bad dest {dest.name}")

    atlas = paint_atlas()
    if len(atlas) != ATLAS or len(atlas[0]) != ATLAS:
        failures.append(f"atlas size {len(atlas[0])}x{len(atlas)} != {ATLAS}")
    for name, (u, v, w, h) in REGIONS.items():
        if u < 0 or v < 0 or u + w > ATLAS or v + h > ATLAS:
            failures.append(f"region {name} out of atlas")

    gecko = paint_controller()
    glow = paint_glowmask()
    if len(gecko) != GECKO_SIZE or len(glow) != GECKO_SIZE:
        failures.append("gecko atlas size != 128")
    for name, (u, v, w, h) in GECKO_REGIONS.items():
        if u < 0 or v < 0 or u + w > GECKO_SIZE or v + h > GECKO_SIZE:
            failures.append(f"gecko region {name} out of atlas")

    try:
        import sys
        tools = Path(__file__).resolve().parents[1]
        if str(tools) not in sys.path:
            sys.path.insert(0, str(tools))
        from flight_controller_layout import REGIONS as STOCK_REGIONS
        from flight_controller_layout import SIZE as STOCK_SIZE
        if GECKO_REGIONS != STOCK_REGIONS:
            failures.append("gecko REGIONS drifted from flight_controller_layout")
        if GECKO_SIZE != STOCK_SIZE:
            failures.append("gecko SIZE drifted from flight_controller_layout")
    except ImportError as exc:
        failures.append(f"could not import flight_controller_layout: {exc}")

    if failures:
        for line in failures:
            print(f"FAIL {line}")
        return 1
    print("guidance_v2_art selftest ok")
    return 0
