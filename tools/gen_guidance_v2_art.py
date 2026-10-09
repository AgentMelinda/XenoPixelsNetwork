"""Emit 128× fork block/item textures, the 1024 v2 atlas, and the fork GeckoLib computer.

Native 128× painters write void/neon faces. Stock PNGs are never read or overwritten.

    python tools/gen_guidance_v2_art.py            # 128× fork + 1024 atlas + gecko fork
    python tools/gen_guidance_v2_art.py --lookdev  # also 256× sheets (not shipped)
    python tools/gen_guidance_v2_art.py --check    # generator self-test only
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from guidance_v2_art.atlas import paint_atlas
from guidance_v2_art.gecko import paint_controller, paint_glowmask
from guidance_v2_art.painters import paint
from guidance_v2_art.pngio import write_png
from guidance_v2_art.recipes import (
    ATLAS_DEST,
    GECKO_DEST,
    GECKO_GLOW_DEST,
    LOOKDEV,
    LOOKDEV_SCALE,
    RECIPES,
    SCALE,
    dest_ok,
)
from guidance_v2_art.scanline import remaster


def emit_recipes(lookdev: bool) -> int:
    written = 0
    for recipe in RECIPES:
        if not dest_ok(recipe.dest):
            raise SystemExit(f"dest must end in _fork or start with guidance_v2_: {recipe.dest}")
        src = paint(recipe.painter)
        write_png(recipe.dest, remaster(src, SCALE))
        written += 1
        print(f"wrote {recipe.dest.relative_to(ROOT)}")
        if lookdev:
            write_png(LOOKDEV / recipe.dest.name, remaster(src, LOOKDEV_SCALE))
            print(f"wrote lookdev {recipe.dest.name}")
    return written


def emit_atlas() -> None:
    if not dest_ok(ATLAS_DEST):
        raise SystemExit(f"atlas dest rejected: {ATLAS_DEST}")
    write_png(ATLAS_DEST, paint_atlas())
    print(f"wrote {ATLAS_DEST.relative_to(ROOT)}")


def emit_gecko() -> None:
    for dest, pixels in ((GECKO_DEST, paint_controller()), (GECKO_GLOW_DEST, paint_glowmask())):
        if not dest_ok(dest):
            raise SystemExit(f"gecko dest rejected: {dest}")
        write_png(dest, remaster(pixels, SCALE))
        print(f"wrote {dest.relative_to(ROOT)}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate fork HD textures and the v2 atlas.")
    parser.add_argument("--lookdev", action="store_true",
                        help="also write 256× sheets under tools/generated/guidance_v2_lookdev/")
    parser.add_argument("--check", action="store_true", help="run generator self-tests and exit")
    args = parser.parse_args(argv)
    if args.check:
        from guidance_v2_art.selftest import run
        return run()
    count = emit_recipes(args.lookdev)
    emit_atlas()
    emit_gecko()
    print(f"emitted {count} fork textures + guidance_v2_atlas.png + gecko fork")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
