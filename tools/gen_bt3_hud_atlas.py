#!/usr/bin/env python3
"""Builds the BT3 HUD atlas from the clean UI-element bundle.

The bundle is source art, not runtime textures: 153 PNGs at roughly four times the scale a
Minecraft HUD wants, most of them irrelevant to this HUD. This script selects the sprites the
BT3 view actually draws, crops each to its real (non-transparent) bounds, normalises them to one
pixel density, packs them into a single atlas, and emits every downstream artefact from that one
pass:

  * the runtime atlas PNG, the only thing written into resources
  * a machine-readable manifest
  * the Java sprite rectangles the renderer uses
  * a contact sheet for eyeballing the result

The Java constants are generated rather than hand-written on purpose. Keeping a manifest and a
parallel set of Java UVs in step by hand is exactly the kind of bookkeeping that silently drifts,
and a drifted UV renders as a neighbouring sprite rather than as an error.

Usage:  python tools/gen_bt3_hud_atlas.py [--check]

--check re-runs the build and fails if anything on disk differs, for CI.
"""

from __future__ import annotations

import argparse
import io
import json
import pathlib
import sys
import zipfile

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
# The art bundle was re-issued as new_menus_redisgn.zip, which carries the original
# all_chat_clean_ui_elements_bundle tree unchanged alongside the newer redesign trees, so only the
# archive name moved -- every path inside it is the same.
BUNDLE = ROOT / "new_menus_redisgn.zip"
BUNDLE_DIR = "all_chat_clean_ui_elements_bundle/01_xenoverse_party_hud_clean/core_elements/"

ATLAS_PNG = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_bt3_hud_atlas.png"
JAVA_OUT = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoBt3HudAtlas.java"
GEN_DIR = ROOT / "tools/generated"
MANIFEST = GEN_DIR / "xeno_bt3_hud_atlas.json"
CONTACT = GEN_DIR / "xeno_bt3_hud_atlas_contact.png"

# One uniform factor, so every sprite keeps the proportions it was drawn with and nothing is
# resampled twice. A quarter puts the nameplate at 186px, which is the width the HUD wants it.
SCALE = 0.25
ATLAS_SIZE = 256
PADDING = 1

# name -> source file. The name becomes the Java constant and the manifest key, so it is the
# stable identifier; the bundle's numbered filenames are not.
SPRITES = {
    "NAMEPLATE": "03_player1_nameplate_frame.png",
    "HP_BAR": "08_hp_fill_bar.png",
    "KI_BAR": "09_ki_segment_bar.png",
    "STAMINA_BAR": "10_stm_segment_bar.png",
    "TECHNIQUE_PURPLE": "16_ability_icon_purple.png",
    "TECHNIQUE_BLUE": "17_ability_icon_blue.png",
    "TECHNIQUE_GREEN": "18_ability_icon_green.png",
    "TECHNIQUE_ORANGE": "19_ability_icon_orange.png",
    "PORTRAIT_RING": "20_portrait_ring_p1.png",
    "PROMPT_PLATE": "28_empty_plate_orange.png",
    "PROMPT_ARROW": "32_arrow_orange.png",
    "ENERGY_STREAK": "34_energy_streak_orange.png",
}


class BuildError(RuntimeError):
    pass


def load_sprites():
    if not BUNDLE.exists():
        raise BuildError("source bundle missing: " + str(BUNDLE))
    out = {}
    seen = {}
    with zipfile.ZipFile(BUNDLE) as archive:
        available = set(archive.namelist())
        for name, filename in SPRITES.items():
            if filename in seen:
                raise BuildError(name + " and " + seen[filename] + " both use " + filename)
            seen[filename] = name
            entry = BUNDLE_DIR + filename
            if entry not in available:
                raise BuildError("missing from bundle: " + entry)
            with archive.open(entry) as handle:
                image = Image.open(io.BytesIO(handle.read())).convert("RGBA")
            # Crop to what is actually drawn. The bundle pads most sprites with transparency, and
            # packing that padding would waste atlas space and offset every UV.
            bbox = image.getbbox()
            if bbox is None:
                raise BuildError(name + " is fully transparent")
            image = image.crop(bbox)
            width = max(1, round(image.width * SCALE))
            height = max(1, round(image.height * SCALE))
            out[name] = image.resize((width, height), Image.LANCZOS)
    return out


def pack(sprites):
    """Shelf packing, tallest first.

    The sprite set is small and fixed, so the simplest packer that stays stable between runs is the
    right one. A smarter packer would reshuffle every UV whenever a single sprite changed size.
    """
    order = sorted(sprites, key=lambda n: (-sprites[n].height, n))
    placed = {}
    x = y = shelf = 0
    for name in order:
        image = sprites[name]
        if x + image.width > ATLAS_SIZE:
            x = 0
            y += shelf + PADDING
            shelf = 0
        if y + image.height > ATLAS_SIZE:
            raise BuildError(
                "atlas overflow at " + name + ": " + str(ATLAS_SIZE) + " square is too small. "
                "Lower SCALE or raise ATLAS_SIZE deliberately rather than letting it creep.")
        placed[name] = (x, y, image.width, image.height)
        x += image.width + PADDING
        shelf = max(shelf, image.height)
    return placed


def validate(placed):
    for name, (x, y, w, h) in placed.items():
        if w <= 0 or h <= 0:
            raise BuildError(name + " packed empty")
        if x < 0 or y < 0 or x + w > ATLAS_SIZE or y + h > ATLAS_SIZE:
            raise BuildError(name + " is outside the atlas: " + str((x, y, w, h)))
    items = list(placed.items())
    for index, (first, a) in enumerate(items):
        for second, b in items[index + 1:]:
            if (a[0] < b[0] + b[2] and b[0] < a[0] + a[2]
                    and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]):
                raise BuildError(first + " overlaps " + second)


def render(sprites, placed):
    atlas = Image.new("RGBA", (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
    for name, (x, y, _, _) in placed.items():
        atlas.paste(sprites[name], (x, y))
    return atlas


def contact_sheet(sprites, placed):
    """The atlas with every sprite boxed, so a bad crop is visible at a glance."""
    sheet = render(sprites, placed).convert("RGBA")
    grid = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(grid)
    for _, (x, y, w, h) in placed.items():
        draw.rectangle([x, y, x + w - 1, y + h - 1], outline=(255, 0, 128, 200))
    merged = Image.alpha_composite(sheet, grid)
    return merged.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)


def java_source(placed):
    lines = [
        "package net.bullettrain.xenopixelsmod.client.hud;",
        "",
        "import net.minecraft.resources.ResourceLocation;",
        "",
        "/**",
        " * Sprite rectangles for the BT3 HUD atlas.",
        " *",
        " * <p><b>Generated by {@code tools/gen_bt3_hud_atlas.py} -- do not edit.</b> The atlas PNG,",
        " * its manifest and this file all come out of one pass, so a sprite cannot move within the",
        " * atlas without its rectangle moving with it. A hand-edited rectangle renders a",
        " * neighbouring sprite rather than failing, which is exactly why this is generated.",
        " *",
        " * <p>Re-run the generator after changing the source art or the sprite selection.",
        " */",
        "public final class XenoBt3HudAtlas {",
        "",
        "    public static final ResourceLocation TEXTURE =",
        "            ResourceLocation.fromNamespaceAndPath(",
        "                    \"xenopixelsmod\", \"textures/gui/xeno_bt3_hud_atlas.png\");",
        "",
        "    public static final int ATLAS_WIDTH = " + str(ATLAS_SIZE) + ";",
        "    public static final int ATLAS_HEIGHT = " + str(ATLAS_SIZE) + ";",
        "",
        "    /** One sprite's position and size within {@link #TEXTURE}. */",
        "    public record Sprite(int u, int v, int width, int height) {}",
        "",
    ]
    for name in sorted(placed):
        x, y, w, h = placed[name]
        lines.append("    public static final Sprite " + name + " = new Sprite("
                     + str(x) + ", " + str(y) + ", " + str(w) + ", " + str(h) + ");")
    lines += ["", "    private XenoBt3HudAtlas() {", "    }", "}", ""]
    return "\n".join(lines)


def manifest_json(placed):
    payload = {
        "generator": "tools/gen_bt3_hud_atlas.py",
        "source": BUNDLE.name,
        "scale": SCALE,
        "atlas": {"width": ATLAS_SIZE, "height": ATLAS_SIZE},
        "sprites": {
            name: {"x": x, "y": y, "width": w, "height": h, "source": SPRITES[name]}
            for name, (x, y, w, h) in sorted(placed.items())
        },
    }
    return json.dumps(payload, indent=2) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="fail if the generated files on disk are out of date")
    args = parser.parse_args()

    try:
        sprites = load_sprites()
        placed = pack(sprites)
        validate(placed)
    except BuildError as err:
        print("error: " + str(err), file=sys.stderr)
        return 1

    atlas = render(sprites, placed)
    java = java_source(placed)
    manifest = manifest_json(placed)

    if args.check:
        stale = []
        buffer = io.BytesIO()
        atlas.save(buffer, "PNG")
        if not ATLAS_PNG.exists() or buffer.getvalue() != ATLAS_PNG.read_bytes():
            stale.append(str(ATLAS_PNG))
        for path, text in ((JAVA_OUT, java), (MANIFEST, manifest)):
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                stale.append(str(path))
        if stale:
            print("out of date, re-run tools/gen_bt3_hud_atlas.py:", file=sys.stderr)
            for item in stale:
                print("  " + item, file=sys.stderr)
            return 1
        print("BT3 HUD atlas is up to date")
        return 0

    GEN_DIR.mkdir(parents=True, exist_ok=True)
    ATLAS_PNG.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(ATLAS_PNG, "PNG")
    JAVA_OUT.write_text(java, encoding="utf-8")
    MANIFEST.write_text(manifest, encoding="utf-8")
    contact_sheet(sprites, placed).save(CONTACT, "PNG")

    used = sum(w * h for _, _, w, h in placed.values())
    filled = used * 100 // (ATLAS_SIZE * ATLAS_SIZE)
    print("packed " + str(len(placed)) + " sprites into "
          + str(ATLAS_SIZE) + " square (" + str(filled) + "% filled)")
    for name in sorted(placed):
        x, y, w, h = placed[name]
        print("  {0:18s} {1:3d}x{2:<3d} at {3:3d},{4:<3d}".format(name, w, h, x, y))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
