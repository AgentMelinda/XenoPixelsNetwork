#!/usr/bin/env python3
"""Builds the atlas for XenoPixels' own DragonMineZ stats screen.

Sibling of gen_bt3_hud_atlas.py, with one difference: each sprite declares the size it is drawn at
rather than sharing one uniform scale. A panel and a 22px navigation button cannot sensibly come out
of the same factor, and storing each sprite at its drawn size means the screen blits 1:1 with no
resampling at draw time.

Those sizes are *our* layout, chosen for the screen in client/screen/XenoDmzStatsScreen.java. They
are deliberately not DragonMineZ's panel rectangles: an earlier attempt forced this art through
DMZ's 141x213 panel and the tall bundle panels came out squashed. That screen is our own, so the art
keeps its own proportions. It is reached with /xenohud menus screen; the other menu modes never load
this atlas.

Usage:  python tools/gen_bt3_menu_atlas.py [--check]
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
BUNDLE_DIR = "all_chat_clean_ui_elements_bundle/04_legacy_clean_elements/"

ATLAS_PNG = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_bt3_menu_atlas.png"
JAVA_OUT = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoBt3MenuAtlas.java"
GEN_DIR = ROOT / "tools/generated"
MANIFEST = GEN_DIR / "xeno_bt3_menu_atlas.json"
CONTACT = GEN_DIR / "xeno_bt3_menu_atlas_contact.png"

ATLAS_W = 512
ATLAS_H = 256
PADDING = 1

# name -> (source file, destination width, destination height)
SPRITES = {
    "INFO_PANEL": ("01_information_panel.png", 120, 180),
    "STATS_PANEL": ("03_statistics_panel.png", 120, 180),
    "NAMEPLATE": ("02_top_nameplate.png", 150, 50),
    "BUTTON_CHARACTER": ("04_character_button.png", 22, 22),
    "BUTTON_SKILLS": ("05_skills_button.png", 22, 22),
    "BUTTON_QUESTS": ("06_quests_button.png", 22, 22),
    "BUTTON_ITEMS": ("07_items_button.png", 22, 22),
    "BUTTON_PARTY": ("08_party_button.png", 22, 22),
    "BUTTON_SETTINGS": ("09_settings_button.png", 22, 22),
    "ORB_ICONS": ("10_top_right_orb_icons.png", 60, 20),
}


class BuildError(RuntimeError):
    pass


def load_sprites():
    if not BUNDLE.exists():
        raise BuildError("source bundle missing: " + str(BUNDLE))
    out = {}
    with zipfile.ZipFile(BUNDLE) as archive:
        available = set(archive.namelist())
        for name, (filename, width, height) in SPRITES.items():
            entry = BUNDLE_DIR + filename
            if entry not in available:
                raise BuildError("missing from bundle: " + entry)
            with archive.open(entry) as handle:
                image = Image.open(io.BytesIO(handle.read())).convert("RGBA")
            bbox = image.getbbox()
            if bbox is None:
                raise BuildError(name + " is fully transparent")
            # Crop first so the art fills its destination rather than the transparent margin the
            # bundle pads it with, then resize onto the exact rectangle the screen blits.
            out[name] = image.crop(bbox).resize((width, height), Image.LANCZOS)
    return out


def pack(sprites):
    order = sorted(sprites, key=lambda n: (-sprites[n].height, n))
    placed = {}
    x = y = shelf = 0
    for name in order:
        image = sprites[name]
        if x + image.width > ATLAS_W:
            x = 0
            y += shelf + PADDING
            shelf = 0
        if y + image.height > ATLAS_H:
            raise BuildError(
                "atlas overflow at " + name + ". Raise ATLAS_W/ATLAS_H deliberately rather than "
                "letting it creep, and remember the texture ships to every client.")
        placed[name] = (x, y, image.width, image.height)
        x += image.width + PADDING
        shelf = max(shelf, image.height)
    return placed


def validate(placed):
    for name, (x, y, w, h) in placed.items():
        wanted = SPRITES[name][1], SPRITES[name][2]
        if (w, h) != wanted:
            raise BuildError(name + " packed at " + str((w, h)) + " but must be " + str(wanted)
                             + "; the screen blits it 1:1 and assumes that size")
        if x < 0 or y < 0 or x + w > ATLAS_W or y + h > ATLAS_H:
            raise BuildError(name + " is outside the atlas")
    items = list(placed.items())
    for index, (first, a) in enumerate(items):
        for second, b in items[index + 1:]:
            if (a[0] < b[0] + b[2] and b[0] < a[0] + a[2]
                    and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]):
                raise BuildError(first + " overlaps " + second)


def render(sprites, placed):
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    for name, (x, y, _, _) in placed.items():
        atlas.paste(sprites[name], (x, y))
    return atlas


def contact_sheet(sprites, placed):
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
        " * Sprite rectangles for the XenoPixels DragonMineZ stats screen.",
        " *",
        " * <p><b>Generated by {@code tools/gen_bt3_menu_atlas.py} -- do not edit.</b> Every sprite is",
        " * stored at the size the screen draws it, so every blit is 1:1 and nothing is resampled",
        " * while rendering.",
        " */",
        "public final class XenoBt3MenuAtlas {",
        "",
        "    public static final ResourceLocation TEXTURE =",
        "            ResourceLocation.fromNamespaceAndPath(",
        "                    \"xenopixelsmod\", \"textures/gui/xeno_bt3_menu_atlas.png\");",
        "",
        "    public static final int ATLAS_WIDTH = " + str(ATLAS_W) + ";",
        "    public static final int ATLAS_HEIGHT = " + str(ATLAS_H) + ";",
        "",
        "    /** One sprite's position and size within {@link #TEXTURE}. */",
        "    public record Sprite(int u, int v, int width, int height) {}",
        "",
    ]
    for name in sorted(placed):
        x, y, w, h = placed[name]
        lines.append("    public static final Sprite " + name + " = new Sprite("
                     + str(x) + ", " + str(y) + ", " + str(w) + ", " + str(h) + ");")
    lines += ["", "    private XenoBt3MenuAtlas() {", "    }", "}", ""]
    return "\n".join(lines)


def manifest_json(placed):
    payload = {
        "generator": "tools/gen_bt3_menu_atlas.py",
        "source": BUNDLE.name,
        "atlas": {"width": ATLAS_W, "height": ATLAS_H},
        "sprites": {
            name: {"x": x, "y": y, "width": w, "height": h, "source": SPRITES[name][0]}
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
            print("out of date, re-run tools/gen_bt3_menu_atlas.py:", file=sys.stderr)
            for item in stale:
                print("  " + item, file=sys.stderr)
            return 1
        print("BT3 menu atlas is up to date")
        return 0

    GEN_DIR.mkdir(parents=True, exist_ok=True)
    ATLAS_PNG.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(ATLAS_PNG, "PNG")
    JAVA_OUT.write_text(java, encoding="utf-8")
    MANIFEST.write_text(manifest, encoding="utf-8")
    contact_sheet(sprites, placed).save(CONTACT, "PNG")

    print("packed " + str(len(placed)) + " menu sprites into "
          + str(ATLAS_W) + "x" + str(ATLAS_H))
    for name in sorted(placed):
        x, y, w, h = placed[name]
        print("  {0:18s} {1:3d}x{2:<3d} at {3:3d},{4:<3d}".format(name, w, h, x, y))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
