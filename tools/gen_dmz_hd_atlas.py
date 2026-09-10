#!/usr/bin/env python3
"""Builds the atlas for XenoPixels' rebuilt DragonMineZ character screen.

Source is `05_dragonmine_hd_ui_v4` inside dragonmine_complete_ui_elements_master_bundle: the one pack
in that bundle whose "clean" assets really are clean. Its own README is explicit that the row shells
carry no baked numbers and that the label, value and multiplier are meant to be drawn in code, which
is exactly how client/screen/XenoDmzStatsScreen.java uses them.

Each sprite declares its logical draw size and is stored at four times that density. Minecraft then
draws the high-resolution source rectangle into the logical rectangle while DragonMineZ's integer
screen scale maps it cleanly to physical pixels. Those sizes are our screen's layout, not
DragonMineZ's 141x213 panel rectangle -- this screen is our own, so the art keeps its proportions.

Two invariants here exist because of bugs that already shipped:

  * every source asset is checked for actual content. All six `clean_icon_*.png` in this pack are
    fully transparent, and an earlier bundle had the same shape -- which is how six empty navigation
    buttons reached a play session unnoticed. The glyphs live in the `example_icon_*` files, so that
    is what the buttons composite.
  * a contact sheet is written at the sizes the game samples, so a bad crop or an empty cell is
    visible without launching Minecraft.

Usage:  python tools/gen_dmz_hd_atlas.py [--check]
"""

from __future__ import annotations

import argparse
import io
import json
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "dragonmine_complete_ui_elements_master_bundle"
KIT = BUNDLE / "05_dragonmine_hd_ui_v4" / "dragonmine_hd_ui_single_elements_v4"

ATLAS_PNG = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_dmz_hd_atlas.png"
JAVA_OUT = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoDmzHdAtlas.java"
GEN_DIR = ROOT / "tools/generated"
MANIFEST = GEN_DIR / "xeno_dmz_hd_atlas.json"
CONTACT = GEN_DIR / "xeno_dmz_hd_atlas_contact.png"

SCALE = 4
ATLAS_W = 2048
ATLAS_H = 2048
PADDING = SCALE

# name -> (path below the kit, destination width, destination height)
#
# Destination sizes hold each element's own aspect: the panels are 510x700 and 500x710, the nameplate
# is 980x180, and squashing any of them into a shared rectangle is what made the first attempt at
# this look wrong.
SPRITES = {
    "INFO_PANEL": ("01_panels/clean_information_panel.png", 150, 206),
    "STATS_PANEL": ("01_panels/clean_statistics_panel.png", 150, 213),
    "NAMEPLATE": ("01_panels/clean_nameplate.png", 218, 40),
    "INFO_HEADER": ("02_headers/clean_information_header.png", 120, 25),
    "STATS_HEADER": ("02_headers/clean_statistics_header.png", 120, 25),
    "STATS_SUBHEADER": ("02_headers/clean_stats_subheader.png", 96, 23),
    "STAT_ROW": ("03_rows/clean_information_stat_row.png", 130, 18),
    "BOTTOM_BOX": ("03_rows/clean_bottom_stats_box.png", 130, 51),
    # The clean file is only the rounded shell. The example file is the same shell with the actual
    # plus glyph, which is required because this sprite is the complete clickable button in game.
    "PLUS_BUTTON": ("03_rows/example_plus_button.png", 11, 11),
    # These two "row" shells trim to a 2px sliver -- they are underlines, not frames -- so they are
    # stored and used as dividers rather than stretched into row-height boxes.
    "ROW_DIVIDER": ("03_rows/clean_statistics_row.png", 130, 2),
    "INFO_DIVIDER": ("03_rows/clean_basic_info_row.png", 130, 2),
    "KANJI_CIRCLE": ("05_icons/clean_info_kanji_circle.png", 18, 18),
    "STATS_BARS_ICON": ("05_icons/clean_stats_bars_icon.png", 18, 18),
    # The example orbs, not the clean ones: clean_top_orb_* are hollow rings (122 opaque pixels in
    # the middle against the example's 958), and a decorative orb wants to be filled. The same trap
    # as the empty clean icons, one step subtler -- these do pass a transparency check.
    "ORB_BLUE": ("05_icons/example_top_orb_blue.png", 12, 12),
    "ORB_RED": ("05_icons/example_top_orb_red.png", 12, 12),
    "ORB_YELLOW": ("05_icons/example_top_orb_yellow.png", 12, 12),
    "DIVIDER": ("06_overlays/clean_blue_orange_divider.png", 130, 3),
    "ACCENT": ("06_overlays/clean_vertical_orange_accent.png", 2, 60),
}

# The six navigation buttons, each built from a shell plus a glyph.
#
# The shell is 122x154 with content only in its top 122x122 -- the empty strip beneath is where the
# example version puts its caption, which has no room in a 22px cell. The glyph has to come from the
# example icon because the clean ones are entirely transparent.
NAV_BUTTONS = {
    "NAV_CHARACTER": "character",
    "NAV_SKILLS": "skills",
    "NAV_QUESTS": "quests",
    "NAV_ITEMS": "items",
    "NAV_PARTY": "party",
    "NAV_SETTINGS": "settings",
}
NAV_SIZE = 22
# How much of the button the glyph fills. Its own art sits inside a margin already, so this is the
# fraction of the shell the glyph's trimmed content is scaled to.
NAV_GLYPH_FRACTION = 0.56


class BuildError(RuntimeError):
    pass


def load(relative: str) -> Image.Image:
    path = KIT / relative
    if not path.exists():
        raise BuildError("missing from the bundle: " + relative)
    image = Image.open(path).convert("RGBA")
    if image.getbbox() is None:
        raise BuildError(relative + " is fully transparent. The clean icons in this pack are empty; "
                         "the glyphs live in the example_* files.")
    return image


def trimmed(image: Image.Image) -> Image.Image:
    """The art without the transparent margin the pack pads it with."""
    box = image.getbbox()
    return image.crop(box) if box else image


def resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return image.resize(size, Image.Resampling.LANCZOS)


def nav_button(icon_name: str) -> Image.Image:
    """A navigation button: the clean shell with its glyph composited into the middle."""
    shell = load("04_buttons/clean_nav_button_" + icon_name + ".png")
    # Only the top square is the button; the strip below it is the example version's caption area.
    shell = shell.crop((0, 0, shell.width, shell.width))
    source_size = NAV_SIZE * SCALE
    button = resize(shell, (source_size, source_size))

    glyph = trimmed(load("05_icons/example_icon_" + icon_name + ".png"))
    target = max(1, int(source_size * NAV_GLYPH_FRACTION))
    scale = min(target / glyph.width, target / glyph.height)
    glyph = resize(glyph, (max(1, round(glyph.width * scale)), max(1, round(glyph.height * scale))))
    button.alpha_composite(glyph, ((source_size - glyph.width) // 2,
                                   (source_size - glyph.height) // 2))
    return button


def load_sprites() -> dict[str, Image.Image]:
    if not KIT.is_dir():
        raise BuildError("source kit missing: " + str(KIT))
    out: dict[str, Image.Image] = {}
    for name, (relative, width, height) in SPRITES.items():
        out[name] = resize(trimmed(load(relative)), (width * SCALE, height * SCALE))
    for name, icon in NAV_BUTTONS.items():
        out[name] = nav_button(icon)
    return out


def pack(sprites: dict[str, Image.Image]) -> dict[str, tuple[int, int, int, int]]:
    """Shelf packing, tallest first: small and fixed, so the simplest stable packer is the right one."""
    order = sorted(sprites, key=lambda n: (-sprites[n].height, n))
    placed: dict[str, tuple[int, int, int, int]] = {}
    x = y = shelf = 0
    for name in order:
        image = sprites[name]
        if x + image.width > ATLAS_W:
            x = 0
            y += shelf + PADDING
            shelf = 0
        if y + image.height > ATLAS_H:
            raise BuildError("atlas overflow at " + name + ". Raise ATLAS_W/ATLAS_H deliberately "
                             "rather than letting it creep; the texture ships to every client.")
        placed[name] = (x, y, image.width, image.height)
        x += image.width + PADDING
        shelf = max(shelf, image.height)
    return placed


def validate(sprites: dict[str, Image.Image], placed: dict[str, tuple[int, int, int, int]]) -> None:
    for name, (x, y, w, h) in placed.items():
        if w <= 0 or h <= 0:
            raise BuildError(name + " packed empty")
        if sprites[name].getbbox() is None:
            raise BuildError(name + " has no visible content after scaling")
        if x < 0 or y < 0 or x + w > ATLAS_W or y + h > ATLAS_H:
            raise BuildError(name + " is outside the atlas")
    items = list(placed.items())
    for index, (first, a) in enumerate(items):
        for second, b in items[index + 1:]:
            if (a[0] < b[0] + b[2] and b[0] < a[0] + a[2]
                    and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]):
                raise BuildError(first + " overlaps " + second)


def render(sprites, placed) -> Image.Image:
    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    for name, (x, y, _, _) in placed.items():
        atlas.paste(sprites[name], (x, y))
    return atlas


def contact_sheet(sprites, placed) -> Image.Image:
    """Every high-density sprite boxed at the size the game samples it."""
    sheet = render(sprites, placed).convert("RGBA")
    grid = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(grid)
    for _, (x, y, w, h) in placed.items():
        draw.rectangle([x, y, x + w - 1, y + h - 1], outline=(255, 0, 128, 200))
    backing = Image.new("RGBA", sheet.size, (24, 24, 32, 255))
    backing.alpha_composite(Image.alpha_composite(sheet, grid))
    return backing


def java_source(placed) -> str:
    lines = [
        "package net.bullettrain.xenopixelsmod.client.hud;",
        "",
        "import net.minecraft.resources.ResourceLocation;",
        "",
        "/**",
        " * Sprite rectangles for the rebuilt DragonMineZ character screen.",
        " *",
        " * <p><b>Generated by {@code tools/gen_dmz_hd_atlas.py} -- do not edit.</b> Sprites are",
        " * stored at 4x logical density. Draw size and source size stay separate so Minecraft",
        " * samples the supplied HD artwork instead of magnifying a pre-shrunk copy.",
        " */",
        "public final class XenoDmzHdAtlas {",
        "",
        "    public static final ResourceLocation TEXTURE =",
        "            ResourceLocation.fromNamespaceAndPath(",
        "                    \"xenopixelsmod\", \"textures/gui/xeno_dmz_hd_atlas.png\");",
        "",
        "    public static final int ATLAS_WIDTH = " + str(ATLAS_W) + ";",
        "    public static final int ATLAS_HEIGHT = " + str(ATLAS_H) + ";",
        "",
        "    /** One sprite's atlas rectangle and logical on-screen size. */",
        "    public record Sprite(int u, int v, int sourceWidth, int sourceHeight,",
        "                         int width, int height) {}",
        "",
    ]
    for name in sorted(placed):
        x, y, source_width, source_height = placed[name]
        logical_width = NAV_SIZE if name in NAV_BUTTONS else SPRITES[name][1]
        logical_height = NAV_SIZE if name in NAV_BUTTONS else SPRITES[name][2]
        lines.append("    public static final Sprite " + name + " = new Sprite("
                     + str(x) + ", " + str(y) + ", " + str(source_width) + ", "
                     + str(source_height) + ", " + str(logical_width) + ", "
                     + str(logical_height) + ");")
    lines += ["", "    private XenoDmzHdAtlas() {", "    }", "}", ""]
    return "\n".join(lines)


def manifest_json(placed) -> str:
    sources = {name: SPRITES[name][0] for name in SPRITES}
    sources.update({name: "04_buttons/clean_nav_button_" + icon + ".png + 05_icons/example_icon_"
                    + icon + ".png" for name, icon in NAV_BUTTONS.items()})
    payload = {
        "generator": "tools/gen_dmz_hd_atlas.py",
        "source": "dragonmine_complete_ui_elements_master_bundle/05_dragonmine_hd_ui_v4",
        "scale": SCALE,
        "atlas": {"width": ATLAS_W, "height": ATLAS_H},
        "sprites": {
            name: {
                "x": x,
                "y": y,
                "source_width": source_width,
                "source_height": source_height,
                "width": NAV_SIZE if name in NAV_BUTTONS else SPRITES[name][1],
                "height": NAV_SIZE if name in NAV_BUTTONS else SPRITES[name][2],
                "source": sources[name],
            }
            for name, (x, y, source_width, source_height) in sorted(placed.items())
        },
    }
    return json.dumps(payload, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="fail if the generated files on disk are out of date")
    args = parser.parse_args()

    try:
        sprites = load_sprites()
        placed = pack(sprites)
        validate(sprites, placed)
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
            print("out of date, re-run tools/gen_dmz_hd_atlas.py:", file=sys.stderr)
            for item in stale:
                print("  " + item, file=sys.stderr)
            return 1
        print("DMZ HD atlas is up to date")
        return 0

    GEN_DIR.mkdir(parents=True, exist_ok=True)
    ATLAS_PNG.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(ATLAS_PNG, "PNG")
    JAVA_OUT.write_text(java, encoding="utf-8")
    MANIFEST.write_text(manifest, encoding="utf-8")
    contact_sheet(sprites, placed).save(CONTACT, "PNG")

    used = sum(w * h for _, _, w, h in placed.values())
    print("packed " + str(len(placed)) + " sprites into " + str(ATLAS_W) + "x" + str(ATLAS_H)
          + " (" + str(used * 100 // (ATLAS_W * ATLAS_H)) + "% filled)")
    for name in sorted(placed):
        x, y, w, h = placed[name]
        print("  {0:18s} {1:3d}x{2:<3d} at {3:3d},{4:<3d}".format(name, w, h, x, y))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
