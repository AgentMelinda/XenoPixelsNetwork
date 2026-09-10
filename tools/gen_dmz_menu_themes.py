#!/usr/bin/env python3
"""Generate DMZ 2.1.3-compatible menu atlases from new_menus_redisgn.zip.

The sheets are written at SCALE times DragonMineZ's own texture size. That is safe and it is the
whole reason the menus look sharp: every GuiGraphics.blit overload funnels into
blit(ResourceLocation;IIIIIIIFFII)V, which divides u/v by the texture size *passed by the caller*
rather than the size of the file on disk. DMZ passes 256x256, so a 1024x1024 sheet whose layout is
the same one scaled up samples the identical normalised region with four times the texel density --
the same mechanism an HD resource pack uses. At 1x the big panel was stored as 141x213 texels and
drawn across as many as 564x852 physical pixels, which is what made it look fuzzy.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import pathlib
import sys
import zipfile

from PIL import Image


ROOT = pathlib.Path(__file__).resolve().parent.parent
# The master bundle is a folder, and unlike the old zip its menu packs ship *named* panels per menu
# rather than one full-screen mockup to cut rectangles out of. That is why the crop column below is
# gone: cropping a screen is what cost 20-38% of several panels and left painted furniture behind.
BUNDLE = ROOT / "dragonmine_complete_ui_elements_master_bundle"
OTHER_MENUS = ("03_dragonmine_other_menus_clean_and_example/"
               "dragonmine_other_menus_single_elements/")
HD_KIT = "05_dragonmine_hd_ui_v4/dragonmine_hd_ui_single_elements_v4/"
DMZ_JAR = ROOT / "libs/dragonminez-2.1.3.jar"
OUTPUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/dmz_menus"
MANIFEST = ROOT / "tools/generated/dmz_menu_theme_manifest.json"
CONTACT = ROOT / "tools/generated/dmz_menu_buttons_contact.png"
OUR_STYLE = ROOT / "tools/source/dmz_our_style"

# Files emitted by older revisions but no longer referenced by the runtime theme. Keep this list
# narrow so the generator never deletes an artist's unrelated PNG from the output folder.
LEGACY_OUTPUTS: set[str] = set()

# Texel density multiplier. Raise for sharper menus, at 16x the pixel count per step; drop to 2 if
# the shipped textures ever get unreasonable. Every rectangle below is written in DMZ's own 1x
# coordinates and multiplied by this on the way into the atlas.
SCALE = 4

# Emit a .png.mcmeta asking for linear filtering. SimpleTexture reads TextureMetadataSection's
# blur/clamp and applies them as the GL filter. Without it a 4x sheet drawn at GUI scale 1 is
# nearest-sampled and shimmers; with it, it resolves smoothly at every GUI scale. Set False to go
# back to nearest if DMZ's own pixel art in the untouched regions ever reads too soft.
SMOOTH_FILTER = False

# Opacity of the scrim drawn behind a header's title text, 0-255.
SCRIM_ALPHA = 190

STOCK_BUTTONS_MENU = "assets/dragonminez/textures/gui/buttons/menubuttons.png"
STOCK_BUTTONS_CHARACTER = "assets/dragonminez/textures/gui/buttons/characterbuttons.png"

# The six navigation buttons along the bottom of every menu, as BaseMenuScreen.initNavigationButtons
# declares them: 20x20 cells, normal state on the top row and hover directly below it.
#
# These are the *example* files on purpose. The bundle ships each button twice and the clean half is
# the frame with no icon in it at all -- using it produced six empty frames in game. The example half
# is the one carrying the glyph.
# The six navigation buttons, at the UVs BaseMenuScreen.initNavigationButtons declares: 20x20 cells,
# normal on the top row and hover directly below.
#
# Each is a shell plus a glyph, from the HD kit. The shell is 122x154 with content only in its top
# square -- the strip beneath is where the example version puts a caption, which has no room in a
# 20px cell -- and the glyph has to come from the example icon, because every clean_icon_* in that
# pack is entirely transparent. Shipping the clean icon is what produced six blank buttons before.
NAV_BUTTONS = {
    "character": (0, "character"),
    "skills": (20, "skills"),
    "quests": (60, "quests"),
    "settings": (100, "settings"),
    "party": (120, "party"),
    # DMZ's sixth menu is minigames; the kit's nearest button is the item pouch, the same
    # substitution XenoDmzStatsScreen makes for its own nav row.
    "minigames": (140, "items"),
}

# How much of the button the glyph fills, once its own transparent margin is trimmed.
NAV_GLYPH_FRACTION = 0.56
PLUS_BUTTON = "03_rows/example_plus_button.png"

STOCK_BIG = "assets/dragonminez/textures/gui/menu/menubig.png"
STOCK_SMALL = "assets/dragonminez/textures/gui/menu/menusmall.png"
STOCK_QUEST = "assets/dragonminez/textures/gui/menu/questmenu.png"

CLEAN_LEFT_PANEL = (HD_KIT + "01_panels/clean_information_panel.png",
                    HD_KIT + "01_panels/example_information_panel.png", None)
CLEAN_RIGHT_PANEL = (HD_KIT + "01_panels/clean_statistics_panel.png",
                     HD_KIT + "01_panels/example_statistics_panel.png", None)
CLEAN_LEFT_HEADER = (HD_KIT + "02_headers/clean_information_header.png",
                     HD_KIT + "02_headers/example_information_header.png", None)
CLEAN_RIGHT_HEADER = (HD_KIT + "02_headers/clean_statistics_header.png",
                      HD_KIT + "02_headers/example_statistics_header.png", None)
CLEAN_TOP_PANEL = (HD_KIT + "03_rows/clean_bottom_stats_box.png",
                   HD_KIT + "03_rows/example_bottom_stats_box.png", None)
SKILLS_COMPACT_PANEL = CLEAN_TOP_PANEL

THEMES = {
    "character": {
        "left": CLEAN_LEFT_PANEL,
        "right": CLEAN_RIGHT_PANEL,
        "left_header": CLEAN_LEFT_HEADER,
        "right_header": CLEAN_RIGHT_HEADER,
        "small": CLEAN_TOP_PANEL,
    },
    "skills": {
        "left": CLEAN_LEFT_PANEL,
        "right": CLEAN_RIGHT_PANEL,
        "left_header": CLEAN_LEFT_HEADER,
        "right_header": CLEAN_RIGHT_HEADER,
        "compact": SKILLS_COMPACT_PANEL,
    },
    "minigames": {
        "left": CLEAN_LEFT_PANEL,
        "right": CLEAN_RIGHT_PANEL,
        "left_header": CLEAN_LEFT_HEADER,
        "right_header": CLEAN_RIGHT_HEADER,
    },
    "party": {
        "left": CLEAN_LEFT_PANEL,
        "right": CLEAN_RIGHT_PANEL,
        "left_header": CLEAN_LEFT_HEADER,
        "right_header": CLEAN_RIGHT_HEADER,
    },
    "settings": {
        "left": CLEAN_LEFT_PANEL,
        "right": CLEAN_RIGHT_PANEL,
        "left_header": CLEAN_LEFT_HEADER,
        "right_header": CLEAN_RIGHT_HEADER,
    },
}

QUEST_PANEL = CLEAN_LEFT_PANEL


class BuildError(RuntimeError):
    pass


def load(path: str) -> Image.Image:
    """One asset out of the master bundle folder."""
    full = BUNDLE / path
    if not full.exists():
        raise BuildError("missing asset: " + path)
    image = Image.open(full).convert("RGBA")
    if image.getbbox() is None:
        raise BuildError("empty asset: " + path)
    return image


def load_our_style(path: str) -> Image.Image:
    """Load one exact-UV atlas sourced from dragonminez_our_style_full.zip."""
    full = OUR_STYLE / path
    if not full.exists():
        raise BuildError("missing our-style asset: " + path)
    image = Image.open(full).convert("RGBA")
    if image.getbbox() is None:
        raise BuildError("empty our-style asset: " + path)
    return image


def load_zip(archive: zipfile.ZipFile, path: str) -> Image.Image:
    """One asset out of a jar -- still needed for DragonMineZ's own stock sheets."""
    try:
        image = Image.open(io.BytesIO(archive.read(path))).convert("RGBA")
    except KeyError as exc:
        raise BuildError(f"missing asset: {path}") from exc
    if image.getbbox() is None:
        raise BuildError(f"empty asset: {path}")
    return image


def extract(spec: tuple[str, str, tuple[int, int, int, int] | None]
            ) -> tuple[Image.Image, dict[str, object]]:
    clean_path, example_path, crop = spec
    clean = load(clean_path)
    example = load(example_path)
    if crop is None:
        crop = clean.getbbox()
    if crop is None:
        raise BuildError(f"empty clean asset: {clean_path}")
    left, top, right, bottom = crop
    if left < 0 or top < 0 or right > clean.width or bottom > clean.height or right <= left or bottom <= top:
        raise BuildError(f"invalid crop {crop} for {clean_path} ({clean.width}x{clean.height})")
    image = clean.crop(crop)
    return image, {
        "clean": clean_path,
        "example_reference": example_path,
        "clean_size": list(clean.size),
        "example_size": list(example.size),
        "crop": list(crop),
    }


# Sprites that lose more than this fraction of a side to the aspect crop are reported. With named
# per-menu panels this should now be small; a large number means a panel and its slot disagree.
ASPECT_WARN = 0.05

# A slot whose art would lose more than this fraction of a side to the aspect crop is left as
# DragonMineZ drew it. Some of these elements simply do not fit: the redesign's header bars are
# around 2.2-2.9 wide for their height while DMZ's header strip is 5.1, and no crop makes that
# anything but a mangled bar. Stock art in one strip beats a smear.
MAX_CROP = 0.35
_crop_report: list[tuple[str, float]] = []
_skipped: list[tuple[str, float]] = []


def resize(image: Image.Image, size: tuple[int, int], name: str = "") -> Image.Image:
    """Fit undistorted art inside the slot without cropping any supplied UI element."""
    target_width, target_height = size
    factor = min(target_width / image.width, target_height / image.height)
    fitted_size = (max(1, round(image.width * factor)), max(1, round(image.height * factor)))
    if name:
        _crop_report.append((name, crop_loss(image, size)))
    fitted = image.resize(fitted_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    canvas.alpha_composite(fitted, ((target_width - fitted.width) // 2,
                                    (target_height - fitted.height) // 2))
    return canvas


def scaled(box: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """A rectangle in DMZ's 1x menu coordinates, in atlas pixels."""
    return tuple(value * SCALE for value in box)


def crop_loss(image: Image.Image, size: tuple[int, int]) -> float:
    """The fraction of a side an aspect-preserving cover crop would discard."""
    source = image.width / image.height
    target = size[0] / size[1]
    return 1.0 - (min(source, target) / max(source, target))


def slot(atlas: Image.Image, image: Image.Image, box: tuple[int, int, int, int],
         name: str = "") -> bool:
    """Fills one of DMZ's rectangles, unless the art is too wrong a shape to survive it."""
    x, y, width, height = scaled(box)
    atlas.paste((0, 0, 0, 0), (x, y, x + width, y + height))
    atlas.alpha_composite(resize(image, (width, height), name), (x, y))
    return True


def nine_slice(image: Image.Image, size: tuple[int, int], border: int = 32) -> Image.Image:
    """Resize a compact frame without stretching its corners or clipping its border."""
    target_width, target_height = size
    source_border = min(border, image.width // 3, image.height // 3)
    target_border_x = min(source_border, target_width // 3)
    target_border_y = min(source_border, target_height // 3)
    source_x = (0, source_border, image.width - source_border, image.width)
    source_y = (0, source_border, image.height - source_border, image.height)
    target_x = (0, target_border_x, target_width - target_border_x, target_width)
    target_y = (0, target_border_y, target_height - target_border_y, target_height)
    output = Image.new("RGBA", size, (0, 0, 0, 0))
    for row in range(3):
        for column in range(3):
            source_box = (source_x[column], source_y[row],
                          source_x[column + 1], source_y[row + 1])
            target_box = (target_x[column], target_y[row],
                          target_x[column + 1], target_y[row + 1])
            width = target_box[2] - target_box[0]
            height = target_box[3] - target_box[1]
            if width <= 0 or height <= 0:
                continue
            piece = image.crop(source_box).resize((width, height), Image.Resampling.LANCZOS)
            output.alpha_composite(piece, (target_box[0], target_box[1]))
    return output


def frame_slot(atlas: Image.Image, image: Image.Image,
               box: tuple[int, int, int, int]) -> None:
    """Fill a DMZ atlas rectangle with a corner-preserving compact Xeno frame."""
    x, y, width, height = scaled(box)
    atlas.paste((0, 0, 0, 0), (x, y, x + width, y + height))
    atlas.alpha_composite(nine_slice(image, (width, height)), (x, y))


def scrim(size: tuple[int, int], fill: tuple[int, int, int]) -> Image.Image:
    """A soft band that darkens the middle of a header and fades out at both edges.

    The previous version pasted a flat opaque rectangle over the middle 60% of every header, which
    guaranteed DMZ's title text stayed readable but flattened the art into a smear -- one of the
    things that read as blurry. A gradient keeps the artwork visible and still gives the text
    something solid to sit on.
    """
    width, height = size
    column = Image.new("RGBA", (1, height), (0, 0, 0, 0))
    for row in range(height):
        distance = abs(row / max(1, height - 1) - 0.5) * 2.0
        strength = max(0.0, 1.0 - distance) ** 0.6
        column.putpixel((0, row), fill + (int(SCRIM_ALPHA * strength),))
    return column.resize((width, height), Image.Resampling.NEAREST)


def title_slot(atlas: Image.Image, image: Image.Image, box: tuple[int, int, int, int],
               name: str = "") -> None:
    x, y, width, height = scaled(box)
    fitted = resize(image, (width, height), name)
    opaque = [pixel for pixel in fitted.get_flattened_data() if pixel[3] >= 192]
    darkest = sorted(opaque, key=lambda pixel: pixel[0] + pixel[1] + pixel[2])
    sample = darkest[:max(1, len(darkest) // 5)]
    fill = tuple(sum(pixel[channel] for pixel in sample) // len(sample) for channel in range(3))
    fitted.alpha_composite(scrim((width, height), fill))
    atlas.paste((0, 0, 0, 0), (x, y, x + width, y + height))
    atlas.alpha_composite(fitted, (x, y))


def upscale_stock(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Enlarge DMZ's own sheet with NEAREST.

    The regions we do not replace are DragonMineZ's pixel art. Interpolating them would blur the
    very thing we are trying to sharpen, so they are enlarged as clean blocks instead.
    """
    return image.resize(size, Image.Resampling.NEAREST)


def nav_glyph(icon: str, cell: int) -> Image.Image:
    """A navigation button: the clean shell with its glyph composited into the middle."""
    shell = load(HD_KIT + "04_buttons/clean_nav_button_" + icon + ".png")
    # Only the top square is the button itself.
    shell = shell.crop((0, 0, shell.width, shell.width))
    button = shell.resize((cell, cell), Image.Resampling.LANCZOS)

    glyph = load(HD_KIT + "05_icons/example_icon_" + icon + ".png")
    box = glyph.getbbox()
    if box:
        glyph = glyph.crop(box)
    target = max(1, int(cell * NAV_GLYPH_FRACTION))
    factor = min(target / glyph.width, target / glyph.height)
    glyph = glyph.resize((max(1, round(glyph.width * factor)),
                          max(1, round(glyph.height * factor))), Image.Resampling.LANCZOS)
    button.alpha_composite(glyph, ((cell - glyph.width) // 2, (cell - glyph.height) // 2))
    return button


def brighten(image: Image.Image, factor: float) -> Image.Image:
    """A hover state: lift the colour, then wash it toward white.

    Multiplying alone is nearly invisible here, because this art is already saturated neon and the
    channels simply clip. Mixing in white on top lifts even a clipped colour, so the hover reads as
    the button lighting up rather than as no change at all.
    """
    red, green, blue, alpha = image.split()
    lift = lambda band: band.point(lambda value: min(255, int(value * factor)))
    lifted = Image.merge("RGBA", (lift(red), lift(green), lift(blue), alpha))
    wash = Image.new("RGBA", image.size, (255, 255, 255, 0))
    wash.putalpha(alpha.point(lambda value: int(value * 0.22)))
    lifted.alpha_composite(wash)
    return lifted


def encode(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def build() -> tuple[dict[str, bytes], dict[str, object],
                     dict[str, tuple[Image.Image, Image.Image]]]:
    if not BUNDLE.exists() or not DMZ_JAR.exists() or not OUR_STYLE.exists():
        raise BuildError("menu bundle, our-style sources, or libs/dragonminez-2.1.3.jar is missing")

    outputs: dict[str, bytes] = {}
    sources: dict[str, object] = {}
    with zipfile.ZipFile(DMZ_JAR) as dmz:
        stock_big = load_zip(dmz, STOCK_BIG)
        stock_small = load_zip(dmz, STOCK_SMALL)
        stock_quest = load_zip(dmz, STOCK_QUEST)
        if (stock_big.size != (256, 256) or stock_small.size != (256, 256)
                or stock_quest.size != (512, 512)):
            raise BuildError("DMZ menu UV contract changed from 256x256")
        sheet = (256 * SCALE, 256 * SCALE)
        big_base = upscale_stock(stock_big, sheet)
        small_base = upscale_stock(stock_small, sheet)

        for name, theme in THEMES.items():
            for side in ("left", "right"):
                panel, panel_source = extract(theme[side])
                header, header_source = extract(theme[f"{side}_header"])
                atlas = big_base.copy()
                slot(atlas, panel, (0, 0, 141, 213), f"{name}_{side} panel")
                title_slot(atlas, header, (142, 22, 107, 21), f"{name}_{side} header")
                title_slot(atlas, header, (142, 0, 79, 21))
                filename = f"{name}_{side}.png"
                outputs[filename] = encode(atlas)
                sources[filename] = {
                    "panel": panel_source,
                    "header": header_source,
                    "stock": STOCK_BIG,
                }
            if "small" in theme:
                small, small_source = extract(theme["small"])
                atlas = small_base.copy()
                slot(atlas, small, (0, 95, 145, 58), f"{name}_top")
                filename = f"{name}_top.png"
                outputs[filename] = encode(atlas)
                sources[filename] = {"panel": small_source, "stock": STOCK_SMALL}
            if "compact" in theme:
                compact, compact_source = extract(theme["compact"])
                atlas = small_base.copy()
                # SkillsMenuScreen samples this first box twice and the short strip once. Only
                # those exact regions are replaced; every other MENU_SMALL sprite stays stock.
                frame_slot(atlas, compact, (0, 0, 141, 94))
                frame_slot(atlas, compact, (0, 154, 141, 32))
                filename = f"{name}_top.png"
                outputs[filename] = encode(atlas)
                sources[filename] = {
                    "compact_panel": compact_source,
                    "uv_regions": [[0, 0, 141, 94], [0, 154, 141, 32]],
                    "stock": STOCK_SMALL,
                }

        quest_panel, quest_source = extract(QUEST_PANEL)
        quest_atlas = upscale_stock(stock_quest, (512 * SCALE, 512 * SCALE))
        slot(quest_atlas, quest_panel, (1, 1, 282, 426), "quests")
        outputs["quests.png"] = encode(quest_atlas)
        sources["quests.png"] = {
            "panel": quest_source,
            "stock": STOCK_QUEST,
        }

        nav_source_path = "dragonminez/textures/gui/buttons/menubuttons.png"
        nav_source = load_our_style(nav_source_path)
        if nav_source.size != (256, 256):
            raise BuildError("our-style menubuttons UV contract changed from 256x256")
        nav_atlas = nav_source.resize(sheet, Image.Resampling.NEAREST)
        nav_cells = {}
        for menu, (u, _icon) in NAV_BUTTONS.items():
            nav_cells[menu] = (
                nav_source.crop((u, 0, u + 20, 20)),
                nav_source.crop((u, 20, u + 20, 40)),
            )
        outputs["menubuttons.png"] = encode(nav_atlas)
        sources["menubuttons.png"] = {"art": nav_source_path, "scale_mode": "nearest"}

        char_source_path = "dragonminez/textures/gui/buttons/characterbuttons.png"
        char_source = load_our_style(char_source_path)
        if char_source.size != (256, 256):
            raise BuildError("our-style characterbuttons UV contract changed from 256x256")
        char_atlas = char_source.resize(sheet, Image.Resampling.NEAREST)
        nav_cells["stat +"] = (
            char_source.crop((0, 0, 10, 10)),
            char_source.crop((0, 10, 10, 20)),
        )
        outputs["characterbuttons.png"] = encode(char_atlas)
        sources["characterbuttons.png"] = {
            "art": char_source_path,
            "uv": [0, 0],
            "hover_uv": [0, 10],
            "sample_size": [10, 10],
            "scale_mode": "nearest",
        }

    if SMOOTH_FILTER:
        meta = json.dumps({"texture": {"blur": True, "clamp": False}}, indent=2) + chr(10)
        for name in list(outputs):
            outputs[name + ".mcmeta"] = meta.encode()

    manifest = {
        "generator": "tools/gen_dmz_menu_themes.py",
        "source_archive": BUNDLE.name,
        "dmz_contract": DMZ_JAR.name,
        "scale": SCALE,
        "smooth_filter": SMOOTH_FILTER,
        "aspect_crop": {
            name: round(lost, 4) for name, lost in sorted(_crop_report) if lost > ASPECT_WARN
        },
        "outputs": {
            name: {**sources[name], "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in sorted(outputs.items())
            if name in sources
        },
    }
    return outputs, manifest, nav_cells


def button_contact_sheet(cells: dict[str, tuple[Image.Image, Image.Image]]) -> Image.Image:
    """Every navigation cell as the game will sample it, beside its hover state.

    Drawn at the 20x20 the cell really is and then magnified with NEAREST, so what this shows is what
    a player sees. It exists because the first version of these buttons shipped completely empty --
    the bundle's clean art has no icon in it -- and nothing in the pipeline noticed.
    """
    cell, gap, zoom = 20, 3, 6
    width = (cell + gap) * len(cells) + gap
    sheet = Image.new("RGBA", (width, cell * 2 + gap * 3), (24, 24, 32, 255))
    for index, (name, states) in enumerate(sorted(cells.items())):
        x = gap + index * (cell + gap)
        normal, hover = states
        size = (10, 10) if name == "stat +" else (cell, cell)
        sheet.alpha_composite(resize(normal, size), (x, gap))
        sheet.alpha_composite(resize(hover, size), (x, cell + gap * 2))
    return sheet.resize((sheet.width * zoom, sheet.height * zoom), Image.Resampling.NEAREST)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        outputs, manifest, nav_cells = build()
    except BuildError as exc:
        print(exc, file=sys.stderr)
        return 1

    expected = {OUTPUT / name: data for name, data in outputs.items()}
    expected[MANIFEST] = (json.dumps(manifest, indent=2) + "\n").encode()
    stale = [path for path, data in expected.items() if not path.exists() or path.read_bytes() != data]
    obsolete = [OUTPUT / name for name in LEGACY_OUTPUTS if (OUTPUT / name).exists()]
    obsolete.extend(path for path in OUTPUT.glob("*.png.mcmeta") if path not in expected)
    if args.check:
        if stale or obsolete:
            for path in stale + obsolete:
                print(f"out of date: {path}", file=sys.stderr)
            return 1
        print("DMZ menu themes are up to date")
        return 0

    for path, data in expected.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    for path in obsolete:
        path.unlink()
    CONTACT.parent.mkdir(parents=True, exist_ok=True)
    button_contact_sheet(nav_cells).save(CONTACT, "PNG")
    sheets = [name for name in outputs if name.endswith(".png")]
    total = sum(len(data) for data in outputs.values())
    print(f"wrote {len(sheets)} DMZ menu theme textures at {SCALE}x ({total / 1024:.0f} KiB total)")
    for name, lost in sorted(_crop_report, key=lambda item: -item[1]):
        if lost > ASPECT_WARN:
            print(f"  aspect mismatch {lost * 100:.0f}% for {name}; fitted without cropping")
    for name, lost in sorted(_skipped, key=lambda item: -item[1]):
        print(f"  left stock: {name} would have lost {lost * 100:.0f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
