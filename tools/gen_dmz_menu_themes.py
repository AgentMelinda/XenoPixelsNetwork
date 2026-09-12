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
import re
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


# ---------------------------------------------------------------------------
# NEON mode
# ---------------------------------------------------------------------------
# `/xenohud menus neon` gets its own sheets, written into a `neon/` subfolder, so the THEME sheets
# above keep their exact bytes and the two menu reworks stay switchable side by side.
#
# CharacterStatsScreen is deliberately absent. In NEON mode XenoDmzScreenSwap replaces that screen
# outright with XenoNeonStatsScreen, so no themed character sheet is ever sampled.
#
# Every crop below is explicit, and none of them could be derived from alpha. The packs under
# 03_dragonmine_other_menus_clean_and_example are auto-splits out of larger labelled contact sheets,
# so a file's own bounds routinely contain a caption -- "Clean (Empty)", "LEFT PANEL (CLEAN)",
# "STATS BUTTON (CLEAN)" -- or a slice of the element next to it. And every one of those files is
# RGBA with its alpha channel fixed at 255, whatever the pack README says about transparency, so
# getbbox() returns the whole rectangle and a crop has to be stated rather than measured. Each one
# here was read off the source's neon-border row/column profile and is checked by the per-page proof
# sheets in tools/generated/.
NEON_DIR = "neon/"


def _art(path, crop, note, cap=None):
    """One bundle element, with the crop that isolates it from its contact sheet.

    `cap` is `(source_y, rows)`: that band is lifted off the source and laid over the top of the
    crop. A panel cropped below its own title bar has no top edge left -- its rows run straight off
    the cut -- and the frame's real top rule is sitting unused higher up the same file, at the same
    width, so it is put back rather than invented.
    """
    return {"path": OTHER_MENUS + path, "crop": tuple(crop), "note": note, "cap": cap}


NEON_PAGES = {
    "skills": {
        "screen": "com.dragonminez.client.gui.character.SkillsMenuScreen",
        "group": "skills_information",
        "left": _art("skills_information/01_panels/clean_left_panel.png", (0, 38, 240, 209),
                     "panel body, below the pack's own 'Skills' title bar; the 'Clean (Empty)' "
                     "sheet caption below y=209 is dropped too. No cap: this frame's top rule and "
                     "its title band overlap, so the body is left open at the top"),
        "right": _art("skills_information/01_panels/clean_detail_panel.png", (0, 0, 222, 105),
                      "detail frame, which carries no title of its own; its bottom rule is at "
                      "y=104 and the 'Clean (Empty)' sheet caption sits below that"),
        # No header entry. skills_information's only header-shaped file is
        # 03_headers_accents/clean_top_information_panel.png, which is the top of a *panel* -- it
        # carries a cropped title and a 'Clean (Empty)' caption, not a bar -- so DragonMineZ's own
        # header pixels are kept and the fallback is recorded.
        "small": _art("skills_information/01_panels/clean_detail_panel.png", (0, 0, 222, 105),
                      "the same detail frame, for MENU_SMALL's 141x94 box"),
        "strip": _art("skills_information/01_panels/clean_bottom_strip.png", (12, 0, 270, 19),
                      "the strip alone; the divider at y>=25 and its caption are sheet furniture"),
    },
    "quests": {
        "screen": "com.dragonminez.client.gui.character.QuestTreeScreen",
        "group": "quest_tree",
        "quest": _art("quest_tree/01_panels/clean_left_quest_panel.png", (7, 50, 205, 274),
                      "panel body, below the pack's own 'Quest Tree' title bar; the "
                      "'NODE ELEMENTS' swatch row below y=274 is a sheet label. No cap: this "
                      "frame's top rule runs through its 悟 badge, so the body is left open at "
                      "the top and DragonMineZ's own chrome sits across it"),
    },
    "minigames": {
        "screen": "com.dragonminez.client.gui.character.MinigamesScreen",
        "group": "minigames",
        # No cap: this frame's outer top rule and its 悟 badge overlap, so lifting the rule would
        # lift the title band with it. The body is left open at the top and DMZ's header bar sits
        # across it.
        "left": _art("minigames/01_panels/clean_list_panel.png", (4, 48, 197, 258),
                     "list body, below the pack's own 'MINIGAMES' title bar"),
        # Kept as-is: this frame's painted bottom bar is the page's own housing for a start
        # control, and MinigamesScreen draws its start button from menubuttons (0,50) 105x20 over
        # that area rather than beside it, so the bar reads as the button's frame.
        "right": _art("minigames/01_panels/clean_detail_panel.png", (0, 0, 297, 264),
                      "detail frame, which carries no title of its own; the "
                      "'SCROLLBAR / ACCENTS' caption below y=264 is dropped"),
        "header": _art("minigames/03_headers_accents/title_bar_clean.png", (10, 0, 204, 51),
                       "title bar; the sliver at x<10 belongs to the element beside it"),
    },
    "party": {
        "screen": "com.dragonminez.client.gui.character.PartyMenuScreen",
        "group": "server_menu",
        "left": _art("server_menu/01_panels/clean_left_panel.png", (0, 59, 203, 362),
                     "list body, below the pack's own 'SERVER' title bar; this file's frame starts "
                     "at y=17 because a 'LEFT PANEL (CLEAN)' caption occupies the rows above it",
                     cap=(17, 14)),
        # Deliberately the left panel again. server_menu's right panel is not clean: it has an
        # example '???' heading, a progress row and a pair of arrows painted into it, and DMZ draws
        # its own arrows and action button over that area. Two sets of arrows is worse than two
        # matching frames, so the clean list body is used on both sides and it is said so here.
        "right": _art("server_menu/01_panels/clean_left_panel.png", (0, 59, 203, 362),
                      "the left panel's body reused: the pack's right panel has example content "
                      "painted into it -- a '???' heading, a progress row and prev/next arrows",
                      cap=(17, 14)),
        "header": _art("server_menu/03_headers_accents/clean_header_bar.png", (16, 0, 324, 69),
                       "header bar; the accent rule below y=69 is a separate element"),
    },
    "settings": {
        "screen": "com.dragonminez.client.gui.character.ConfigMenuScreen",
        "group": "options",
        "left": _art("options/01_panels/options_panel_clean.png", (0, 38, 228, 296),
                     "options body, below the pack's own 'Options' title bar",
                     cap=(12, 10)),
        # Deliberately the options panel again. options/values_panel_clean has a toggle pill and a
        # scrollbar painted into every row, and ConfigMenuScreen draws its own +/- steppers at
        # rightPanelX+25 and +108 and its own scrollbar over exactly that area. Two sets of controls
        # is worse than two matching frames.
        "right": _art("options/01_panels/options_panel_clean.png", (0, 38, 228, 296),
                      "the options body reused: the pack's values panel has a toggle pill and a "
                      "scrollbar painted into it, and DragonMineZ draws its own there",
                      cap=(12, 10)),
        "header": _art("options/03_headers_accents/header_bar_clean.png", (0, 0, 221, 42),
                       "header bar; the caption below y=42 is a sheet label"),
    },
}


# The characterbuttons cells DragonMineZ really samples, as normal UV, hover UV and sample size.
# Read off the decompiled builder chains rather than inferred -- see dmz_contract() for the full
# table this is a subset of.
CHAR_BUTTON_CELLS = {
    "increase": ((0, 0), (0, 10), (10, 10)),
    "decrease": ((142, 0), (142, 10), (10, 10)),
    "action": ((0, 28), (0, 48), (74, 20)),
    "next": ((20, 0), (20, 14), (8, 14)),
    "prev": ((32, 0), (32, 14), (8, 14)),
}

# Page-specific widget plates, per cell. Only cells whose bundle art is a self-contained plate are
# listed; every other cell on every page keeps DragonMineZ's own pixels and is recorded under
# "neon_widget_fallbacks" in the manifest rather than being quietly filled with something wrong.
NEON_WIDGETS = {
    "party": {
        "action": _art("server_menu/02_rows_buttons/clean_stats_button.png", (17, 0, 188, 39),
                       "the stats button plate; its caption sits below y=39"),
    },
    "minigames": {
        "action": _art("minigames/02_rows_buttons/start_button_clean.png", (0, 0, 145, 50),
                       "the start button plate; a separate element sits at x>=156"),
    },
}

# Corner size for the nine-slice, as a fraction of the source's shorter side. These panels are neon
# frames with heavy corner brackets, so the corners are carried across at a uniform scale and only
# the middle is stretched -- an aspect-preserving fit would letterbox a 240x209 source into a 141x213
# slot and leave most of the panel empty.
NEON_BORDER_FRACTION = 0.22


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


def load_art(art: dict) -> Image.Image:
    """One NEON element, cropped to the rectangle that isolates it from its contact sheet."""
    image = load(art["path"])
    left, top, right, bottom = art["crop"]
    if (left < 0 or top < 0 or right > image.width or bottom > image.height
            or right <= left or bottom <= top):
        raise BuildError(f"invalid crop {art['crop']} for {art['path']} "
                         f"({image.width}x{image.height})")
    cropped = image.crop(art["crop"]).copy()
    cap = art.get("cap")
    if cap:
        cap_top, rows = cap
        if rows >= cropped.height or cap_top + rows > image.height:
            raise BuildError(f"cap {cap} does not fit {art['path']}")
        cropped.alpha_composite(image.crop((left, cap_top, right, cap_top + rows)), (0, 0))
    if cropped.getbbox() is None:
        raise BuildError("empty crop for " + art["path"])
    return cropped


def nine_slice_scaled(image: Image.Image, size: tuple[int, int],
                      border_fraction: float = NEON_BORDER_FRACTION
                      ) -> tuple[Image.Image, dict[str, object]]:
    """Fill a slot with a framed panel, carrying its corners across at a single uniform scale.

    `nine_slice` above keeps the corner *in source pixels*, which is right when the slot and the art
    are a similar size and wrong here: a 240x209 panel going into 564x852 atlas pixels would keep a
    32px bracket reading as 6% of the slot instead of the 17% of the panel it is. The corner is
    scaled by the smaller of the two axis factors -- so it can never overflow the slot -- and only
    the middle is stretched. An aspect-preserving fit is the other obvious option and it is worse:
    it would letterbox that same source into the tall slot and leave most of the panel empty.
    """
    target_width, target_height = size
    source_border = max(1, round(border_fraction * min(image.width, image.height)))
    source_border = max(1, min(source_border, image.width // 3, image.height // 3))
    scale = min(target_width / image.width, target_height / image.height)
    border_x = max(1, min(round(source_border * scale), target_width // 3))
    border_y = max(1, min(round(source_border * scale), target_height // 3))

    source_x = (0, source_border, image.width - source_border, image.width)
    source_y = (0, source_border, image.height - source_border, image.height)
    target_x = (0, border_x, target_width - border_x, target_width)
    target_y = (0, border_y, target_height - border_y, target_height)
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
    return output, {
        "source_border": source_border,
        "corner_scale": round(scale, 4),
        "target_border": [border_x, border_y],
    }


def neon_slot(atlas: Image.Image, art: dict, box: tuple[int, int, int, int]) -> dict[str, object]:
    """Replace one of DragonMineZ's rectangles with a page's own framed art."""
    x, y, width, height = scaled(box)
    image = load_art(art)
    filled, geometry = nine_slice_scaled(image, (width, height))
    atlas.paste((0, 0, 0, 0), (x, y, x + width, y + height))
    atlas.alpha_composite(filled, (x, y))
    return {
        "source": art["path"],
        "crop": list(art["crop"]),
        "cap": list(art["cap"]) if art.get("cap") else None,
        "crop_size": list(image.size),
        "uv": list(box),
        "note": art["note"],
        **geometry,
    }


# ---------------------------------------------------------------------------
# DragonMineZ's own contract, read out of the tracked decompiled source
# ---------------------------------------------------------------------------
DECOMPILED = ROOT / "tools/generated/dmz_decompiled_full/com/dragonminez/client/gui/character"

CONTRACT_SCREENS = (
    "CharacterStatsScreen",
    "SkillsMenuScreen",
    "QuestTreeScreen",
    "MinigamesScreen",
    "PartyMenuScreen",
    "ConfigMenuScreen",
    "util/BaseMenuScreen",
)

_RESOURCE_LOCATION = re.compile(
    r'ResourceLocation\s+(\w+)\s*=\s*ResourceLocation\.fromNamespaceAndPath\('
    r'\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')
_BLIT = re.compile(r'graphics\.blit\(\s*([^;]*?)\s*\)\s*;', re.S)
_CHAIN = re.compile(r'\.texture\(\s*(\w+)\s*\)(.*?)\.build\(\)', re.S)
_COORDS = re.compile(r'\.textureCoords\(\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)')
_TEXTURE_SIZE = re.compile(r'\.textureSize\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)')
_SIZE = re.compile(r'\.size\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)')

NEWLINE = chr(10)


def split_arguments(raw: str) -> list[str]:
    """Top-level comma split, so a nested cast or call is not torn in half."""
    out: list[str] = []
    depth = 0
    current = ""
    for character in raw:
        if character == "," and depth == 0:
            out.append(current.strip())
            current = ""
            continue
        if character in "([":
            depth += 1
        elif character in ")]":
            depth -= 1
        current += character
    if current.strip():
        out.append(current.strip())
    return out


def dmz_contract() -> dict[str, object]:
    """Every texture draw the six V-menus make, from the tracked decompiled DragonMineZ source.

    Recorded rather than inferred. A UV that is a literal is written as a number; one that is a
    computed expression is written as that expression, because putting a guessed number there is how
    a theme ends up sampling a rectangle the game never draws. Anything this cannot read gets no
    entry rather than an assumed one.
    """
    if not DECOMPILED.exists():
        return {"unavailable": "tools/generated/dmz_decompiled_full is not present"}

    contract: dict[str, object] = {}
    for screen in CONTRACT_SCREENS:
        path = DECOMPILED / (screen + ".java")
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        constants = {match.group(1): match.group(2) + ":" + match.group(3)
                     for match in _RESOURCE_LOCATION.finditer(text)}

        blits = []
        for match in _BLIT.finditer(text):
            arguments = split_arguments(match.group(1))
            # A texture built at the call site is recorded as the expression it is, not resolved to
            # whatever constant happens to share its name.
            texture = constants.get(arguments[0], "<expression> " + arguments[0])
            entry = {"line": text.count(NEWLINE, 0, match.start()) + 1, "texture": texture}
            if len(arguments) == 9:
                entry.update(x=arguments[1], y=arguments[2], u=arguments[3], v=arguments[4],
                             width=arguments[5], height=arguments[6],
                             texture_size=[arguments[7], arguments[8]])
            elif len(arguments) == 11:
                entry.update(x=arguments[1], y=arguments[2],
                             width=arguments[3], height=arguments[4],
                             u=arguments[5], v=arguments[6],
                             source_size=[arguments[7], arguments[8]],
                             texture_size=[arguments[9], arguments[10]])
            else:
                entry["arguments"] = arguments
            blits.append(entry)

        widgets = []
        for match in _CHAIN.finditer(text):
            body = match.group(2)
            coords = _COORDS.search(body)
            texture_size = _TEXTURE_SIZE.search(body)
            size = _SIZE.search(match.group(0))
            entry = {
                "line": text.count(NEWLINE, 0, match.start()) + 1,
                "texture": constants.get(match.group(1), match.group(1)),
            }
            if coords:
                numbers = [int(value) for value in coords.groups()]
                entry["uv"] = numbers[:2]
                entry["hover_uv"] = numbers[2:]
            if texture_size:
                entry["sample_size"] = [int(value) for value in texture_size.groups()]
            if size:
                entry["draw_size"] = [int(value) for value in size.groups()]
            widgets.append(entry)

        name = "com.dragonminez.client.gui.character." + screen.replace("util/", "util.")
        contract[name] = {"blits": blits, "widgets": widgets}
    return contract


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




# DragonMineZ's own rectangles, in its 1x menu coordinates. Every one of these is read off the
# decompiled screens (see dmz_contract()); none is guessed from a file name.
BIG_PANEL_UV = (0, 0, 141, 213)
BIG_HEADER_UV = (142, 22, 107, 21)
SMALL_PANEL_UV = (0, 0, 141, 94)
SMALL_STRIP_UV = (0, 154, 141, 32)
# QuestTreeScreen samples (1, 1, 282, 426) and then bleeds up to `round(3 * 282 / panel.width)`
# texels to either side when a panel is flush against an edge, clamping u at 0. Filling from u=0 to
# u=298 covers that bleed for any panel at least 30px wide.
QUEST_PANEL_UV = (0, 1, 298, 426)


def build_neon(big_base: Image.Image, small_base: Image.Image, quest_base: Image.Image,
               widget_base: Image.Image) -> tuple[dict[str, bytes], dict[str, object],
                                                  dict[str, object],
                                                  dict[str, dict[str, Image.Image]]]:
    """The NEON sheet set: one panel/header atlas per DragonMineZ page, plus per-page widgets.

    Returns the encoded sheets, their source ledger, the list of rectangles that deliberately keep
    DragonMineZ's or the shared Neon art, and the per-page widget cells for the proof sheets.
    """
    outputs: dict[str, bytes] = {}
    sources: dict[str, object] = {}
    fallbacks: list[dict[str, object]] = []
    proofs: dict[str, dict[str, Image.Image]] = {}

    for page, spec in NEON_PAGES.items():
        ledger: dict[str, object] = {"screen": spec["screen"], "bundle_group": spec["group"]}
        sheets: dict[str, Image.Image] = {}

        if "quest" in spec:
            atlas = quest_base.copy()
            ledger["panel"] = neon_slot(atlas, spec["quest"], QUEST_PANEL_UV)
            ledger["stock"] = STOCK_QUEST
            name = NEON_DIR + "quests.png"
            outputs[name] = encode(atlas)
            sources[name] = ledger
            sheets["quest panel"] = atlas.crop(scaled_box(QUEST_PANEL_UV))
            fallbacks.append({
                "page": page, "element": "header",
                "reason": "the quest_tree pack's chapter title bar carries baked example text "
                          "('Chapter Title', 'Difficulty: ???') and no untitled variant ships; "
                          "DragonMineZ's own header pixels are kept",
            })
            proofs[page] = sheets
            continue

        for side in ("left", "right"):
            atlas = big_base.copy()
            side_ledger: dict[str, object] = {"panel": neon_slot(atlas, spec[side], BIG_PANEL_UV)}
            if "header" in spec:
                side_ledger["header"] = neon_slot(atlas, spec["header"], BIG_HEADER_UV)
            else:
                fallbacks.append({
                    "page": page, "element": f"{side} header",
                    "reason": "no untitled header bar ships in this pack; DragonMineZ's own header "
                              "pixels are kept",
                })
            side_ledger["stock"] = STOCK_BIG
            name = NEON_DIR + f"{page}_{side}.png"
            outputs[name] = encode(atlas)
            sources[name] = {**ledger, **side_ledger}
            sheets[side + " panel"] = atlas.crop(scaled_box(BIG_PANEL_UV))
            sheets[side + " header"] = atlas.crop(scaled_box(BIG_HEADER_UV))

        if "small" in spec:
            atlas = small_base.copy()
            small_ledger = {
                "panel": neon_slot(atlas, spec["small"], SMALL_PANEL_UV),
                "strip": neon_slot(atlas, spec["strip"], SMALL_STRIP_UV),
                "stock": STOCK_SMALL,
            }
            name = NEON_DIR + f"{page}_top.png"
            outputs[name] = encode(atlas)
            sources[name] = {**ledger, **small_ledger}
            sheets["small panel"] = atlas.crop(scaled_box(SMALL_PANEL_UV))
            sheets["small strip"] = atlas.crop(scaled_box(SMALL_STRIP_UV))

        proofs[page] = sheets

    # Per-page widget sheets. Each starts from the shared Neon characterbuttons art, so a cell with
    # no page-specific plate still reads as Xeno's own style rather than as DragonMineZ's; only the
    # cells listed in NEON_WIDGETS differ from page to page, and the rest are recorded below.
    widget_cells: dict[str, dict[str, Image.Image]] = {}
    for page, spec in NEON_PAGES.items():
        atlas = widget_base.resize((256 * SCALE, 256 * SCALE), Image.Resampling.NEAREST)
        plates = NEON_WIDGETS.get(page, {})
        cells: dict[str, Image.Image] = {}
        cell_ledger: dict[str, object] = {}
        for cell, ((u, v), (hover_u, hover_v), (width, height)) in CHAR_BUTTON_CELLS.items():
            art = plates.get(cell)
            if art is None:
                fallbacks.append({
                    "page": page, "element": "characterbuttons " + cell,
                    "reason": "no self-contained plate for this cell in the "
                              + spec["group"] + " pack; the shared Neon widget art is kept",
                })
                cells[cell] = atlas.crop(scaled_box((u, v, width, height)))
                continue
            plate = load_art(art)
            normal, geometry = nine_slice_scaled(plate, scaled((0, 0, width, height))[2:])
            hover = brighten(normal, 1.28)
            for corner, image in (((u, v), normal), ((hover_u, hover_v), hover)):
                x, y = corner[0] * SCALE, corner[1] * SCALE
                atlas.paste((0, 0, 0, 0), (x, y, x + image.width, y + image.height))
                atlas.alpha_composite(image, (x, y))
            cell_ledger[cell] = {
                "source": art["path"], "crop": list(art["crop"]), "note": art["note"],
                "uv": [u, v], "hover_uv": [hover_u, hover_v], "sample_size": [width, height],
                **geometry,
            }
            cells[cell] = normal
        name = NEON_DIR + f"{page}_characterbuttons.png"
        outputs[name] = encode(atlas)
        sources[name] = {
            "screen": spec["screen"],
            "shared_base": "dragonminez/textures/gui/buttons/characterbuttons.png (our-style)",
            "page_cells": cell_ledger,
        }
        widget_cells[page] = cells

    return outputs, sources, {"fallbacks": fallbacks}, {"panels": proofs, "widgets": widget_cells}


def scaled_box(box: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """A DMZ rectangle as a PIL crop box in atlas pixels."""
    x, y, width, height = scaled(box)
    return (x, y, x + width, y + height)


def proof_sheet(page: str, sheets: dict[str, Image.Image],
                cells: dict[str, Image.Image]) -> Image.Image:
    """One page, drawn the way DragonMineZ will sample it.

    The panel and the header are composited at the offset DMZ uses (+17, +10), because the question
    a proof sheet has to answer is not "is the art nice" but "does anything the art has baked into
    it -- a title, a caption -- escape from under the rectangle DMZ draws on top of it".
    """
    cell = 8
    panels = []
    for name, image in sheets.items():
        preview = image.resize((image.width // SCALE, image.height // SCALE),
                               Image.Resampling.LANCZOS)
        panels.append((name, preview))

    composed = None
    if "left panel" in sheets and "left header" in sheets:
        base = sheets["left panel"].copy()
        base.alpha_composite(sheets["left header"], (17 * SCALE, 10 * SCALE))
        composed = base.resize((base.width // SCALE, base.height // SCALE),
                               Image.Resampling.LANCZOS)
        panels.insert(0, ("left as drawn", composed))

    width = sum(image.width + cell for _, image in panels) + cell
    height = max([image.height for _, image in panels] + [1]) + cell * 4
    sheet = Image.new("RGBA", (max(width, 200), height), (18, 18, 24, 255))
    x = cell
    for name, image in panels:
        sheet.alpha_composite(image, (x, cell * 3))
        x += image.width + cell

    strip_x = cell
    strip = Image.new("RGBA", (max(width, 200), cell * 6), (18, 18, 24, 255))
    for name, image in sorted(cells.items()):
        strip.alpha_composite(image.resize((image.width // SCALE, image.height // SCALE),
                                           Image.Resampling.LANCZOS), (strip_x, cell))
        strip_x += image.width // SCALE + cell
    out = Image.new("RGBA", (sheet.width, sheet.height + strip.height), (18, 18, 24, 255))
    out.alpha_composite(sheet, (0, 0))
    out.alpha_composite(strip, (0, sheet.height))
    return out.resize((out.width * 2, out.height * 2), Image.Resampling.NEAREST)


def build() -> tuple[dict[str, bytes], dict[str, object],
                     dict[str, tuple[Image.Image, Image.Image]], dict[str, object]]:
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

        # NEON's own sheets, in their own subfolder. The THEME sheets above are already encoded, so
        # nothing here can change a byte of them.
        neon_outputs, neon_sources, neon_report, neon_proofs = build_neon(
            big_base, small_base,
            upscale_stock(stock_quest, (512 * SCALE, 512 * SCALE)), char_source)
        outputs.update(neon_outputs)
        sources.update(neon_sources)

    if SMOOTH_FILTER:
        meta = json.dumps({"texture": {"blur": True, "clamp": False}}, indent=2) + chr(10)
        for name in list(outputs):
            outputs[name + ".mcmeta"] = meta.encode()

    manifest = {
        "generator": "tools/gen_dmz_menu_themes.py",
        "source_archive": BUNDLE.name,
        "dmz_jar": DMZ_JAR.name,
        # What DragonMineZ 2.1.3 actually draws, read out of the tracked decompiled source at
        # generation time. This is the table every rectangle above is aimed at; if DMZ changes a UV,
        # regenerating makes the change visible here instead of leaving the theme silently wrong.
        "dmz_contract": dmz_contract(),
        # Rectangles NEON deliberately does not replace, and why. Nothing is hidden by omission.
        "neon": neon_report,
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
    return outputs, manifest, nav_cells, neon_proofs


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
        outputs, manifest, nav_cells, neon_proofs = build()
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
    # One proof sheet per NEON page: the panel, the header, the header composited over the panel at
    # the offset DragonMineZ uses, and the page's widget cells. Written on a normal run only -- they
    # are evidence to look at, not shipped resources, so they stay out of the freshness contract.
    for page, sheets in neon_proofs["panels"].items():
        proof_sheet(page, sheets, neon_proofs["widgets"].get(page, {})).save(
            CONTACT.parent / f"dmz_neon_{page}_proof.png", "PNG")
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
