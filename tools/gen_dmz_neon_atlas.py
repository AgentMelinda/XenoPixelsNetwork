#!/usr/bin/env python3
"""Builds the atlas for the XenoPixels "neon" DragonMineZ menus (`/xenohud menus neon`).

Source is `dragonminez_our_style_clean_example_dimensions_2.zip`, vendored under
`tools/source/dmz_our_style_v2/`. That bundle's design direction differs from the
`05_dragonmine_hd_ui_v4` kit `gen_dmz_hd_atlas.py` builds from, so this is a second atlas and a
second screen rather than a change to the first one. Both ship; `/xenohud menus` switches between
them, which is the only way to settle which reads better in play.

Two things make this bundle different to work with:

  * **The panels are whole slabs.** `clean_latest_005.png` is the entire INFORMATION panel -- frame,
    kanji circle, header, the basic-info rules, the STATS subheader and seven plus-button row shells
    -- in one 507x788 piece, and `clean_latest_006.png` is the same for STATISTICS. There are no
    separate row sprites to position, so what the screen needs from this script is not just sprite
    rectangles but the *interiors*: where each row sits inside its slab. Those are measured here, off
    the art, and emitted as constants. Nobody eyeballs a row offset.
  * **Text colour is part of the design.** The reference render colours STR/SKP/RES red, VIT green,
    PUR magenta, ENE cyan, multipliers gold, and the Gravity line orange. Those are sampled out of
    `example/current_example_full_hud.png` at recorded coordinates rather than picked to look close.

Logical sizes are derived as `round(native / 5)`, and each sprite is stored at 5x that -- so the art
ships at essentially its native resolution and Minecraft samples it rather than magnifying a
pre-shrunk copy. The divisor is 5 rather than the HD atlas's 4 because DragonMineZ's `ScaledScreen`
guarantees only a 320x240 canvas (`getMinGuiWidth`/`getMinGuiHeight`): at 4 the two slabs alone are
363 logical pixels wide and 271 tall with the nameplate and navigation row, which overflows that
canvas. At 5 the whole screen fits inside it with room to spare.

Three guards exist because of bugs that shipped from the first bundle:

  * every source is checked for actual content before use, since a whole row of navigation buttons
    once reached a play session empty;
  * the row-rule detection asserts how many rules it found, so a re-cut source that shifts the rows
    fails the build instead of quietly moving every readout;
  * a contact sheet *and* an anchor overlay are written, the latter drawing every emitted row band
    and button rectangle back onto the art, so a wrong anchor is visible without launching the game.

Usage:  python tools/gen_dmz_neon_atlas.py [--check]
"""

from __future__ import annotations

import argparse
import colorsys
import hashlib
import io
import json
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/source/dmz_our_style_v2"
SPLIT = SOURCE / "clean/latest_split"
EXAMPLE = SOURCE / "example/current_example_full_hud.png"

ATLAS_PNG = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_neon_character.png"
JAVA_OUT = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoNeonAtlas.java"
GEN_DIR = ROOT / "tools/generated"
MANIFEST = GEN_DIR / "xeno_neon_character.json"
CONTACT = GEN_DIR / "xeno_neon_character_contact.png"
OVERLAY = GEN_DIR / "xeno_neon_character_anchors.png"

SCALE = 5
ATLAS_W = 2048
ATLAS_H = 2048
PADDING = SCALE

# name -> (file below tools/source/dmz_our_style_v2, crop box or None)
#
# The two crops exist because the splitter cut these pieces out of one asset sheet by alpha bounds,
# and the neon glow bleeds each element into its neighbour. `001` carries the top edge of the panels
# that sat below it on the sheet; `006` carries a second rounded strip below the panel's own frame.
SPRITES = {
    "NAMEPLATE": ("clean/latest_split/clean_latest_001.png", (0, 0, 619, 148)),
    "ORB_BLUE": ("clean/latest_split/clean_latest_002.png", None),
    "ORB_GOLD": ("clean/latest_split/clean_latest_003.png", None),
    "ORB_RED": ("clean/latest_split/clean_latest_004.png", None),
    "INFO_PANEL": ("clean/latest_split/clean_latest_005.png", None),
    "STATS_PANEL": ("clean/latest_split/clean_latest_006.png", (0, 0, 514, 662)),
    # The ring itself ends at 368; below a blank strip the piece carries the middle column's STATS
    # bar and a row shell, which belong to no part of this screen.
    "SCAN_RING": ("clean/latest_split/clean_latest_007.png", (0, 0, 410, 380)),
    "NAV_CHARACTER": ("clean/latest_split/clean_latest_013.png", None),
    "NAV_SKILLS": ("clean/latest_split/clean_latest_014.png", None),
    "NAV_QUESTS": ("clean/latest_split/clean_latest_015.png", None),
    "NAV_ITEMS": ("clean/latest_split/clean_latest_016.png", None),
    "NAV_PARTY": ("clean/latest_split/clean_latest_017.png", None),
    "NAV_SETTINGS": ("clean/latest_split/clean_latest_018.png", None),
}

# Text insets inside each slab, in the slab's own native pixels. Measured off the art; every one of
# them is drawn back onto the art by the anchor overlay, so a wrong value shows up there.
INFO_LABEL_X = 48
INFO_VALUE_X = 210
INFO_RIGHT_X = 465
STAT_LABEL_X = 130
STAT_RIGHT_X = 470
# The plus button, relative to the top-left of its row band. Its strokes measure x 70..112 and,
# within the first band (top 391), y 396..425.
PLUS_BUTTON = (70, 5, 43, 30)
STATISTIC_LABEL_X = 62
STATISTIC_RIGHT_X = 470
SUMMARY_LABEL_X = 75
SUMMARY_RIGHT_X = 455

# Colours sampled from the reference render, by taking the most saturated pixel in a 32x32 box
# around each coordinate whose lightness is between 0.35 and 0.72 -- which skips both the glow
# highlight and the dark outline and lands on the glyph's own colour.
COLOUR_SAMPLES = {
    "LABEL": (1250, 254),
    "VALUE": (1520, 332),
    "GOLD": (424, 476),
    "RED": (172, 476),
    "GREEN": (175, 596),
    "MAGENTA": (175, 636),
    "CYAN": (172, 677),
    "ORANGE": (1240, 665),
    "HEADER": (833, 131),
}
# LABEL and VALUE are readouts rather than accents, and the saturation rule would pull them toward
# the panel glow behind them. They are sampled for the palest bright pixel instead.
COLOUR_PALE = {"LABEL", "VALUE"}


class BuildError(RuntimeError):
    pass


def load(relative: str, crop=None) -> Image.Image:
    path = SOURCE / relative
    if not path.exists():
        raise BuildError("missing from the vendored bundle: " + relative)
    image = Image.open(path).convert("RGBA")
    if image.getbbox() is None:
        raise BuildError(relative + " is fully transparent")
    if crop is not None:
        if crop[2] > image.width or crop[3] > image.height:
            raise BuildError(relative + " is smaller than its declared crop " + str(crop)
                             + "; the source was re-cut and the crop needs re-measuring")
        image = image.crop(crop)
        if image.getbbox() is None:
            raise BuildError(relative + " is empty after its crop " + str(crop))
    return image


def resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return image.resize(size, Image.Resampling.LANCZOS)


def logical(native: int) -> int:
    return max(1, round(native / SCALE))


def rules(image: Image.Image, x0: int, x1: int, coverage: float,
          y0: int, y1: int) -> list[tuple[int, int]]:
    """The near-full-width bright rules between y0 and y1, as (top, bottom) native pixel runs.

    This is how the row geometry is recovered: the slabs draw a rule under every readout, and those
    rules are the only feature in this art crisp enough to find reliably. The panel frame's own glow
    covers the full slab at any useful alpha threshold, so the window is kept inside the frame.
    """
    px = image.load()
    found: list[tuple[int, int]] = []
    start = 0
    inside = False
    for y in range(y0, y1):
        hits = sum(1 for x in range(x0, x1)
                   if px[x, y][3] > 50 and sum(px[x, y][:3]) > 150)
        hit = hits > (x1 - x0) * coverage
        if hit and not inside:
            start = y
        if not hit and inside:
            found.append((start, y - 1))
        inside = hit
    if inside:
        found.append((start, y1 - 1))
    return found


def bands(found: list[tuple[int, int]]) -> list[tuple[int, int]]:
    """The gaps between consecutive rules -- one band per readout row."""
    return [(found[i][1] + 1, found[i + 1][0]) for i in range(len(found) - 1)]


def expect(found: list, count: int, what: str) -> list:
    if len(found) != count:
        raise BuildError(f"{what}: expected {count} rules, found {len(found)} at "
                         f"{[f[0] for f in found]}. The source art was re-cut; re-measure the "
                         f"detection window before trusting any row position.")
    return found


def measure_info_panel(image: Image.Image) -> dict:
    """INFORMATION slab: five basic-info rows above the STATS rule, seven stat rows below it."""
    # Narrow window: the hexagon watermark on the panel's right half breaks a full-width test.
    # The last rule is the STATS subheader bar's own top edge, not part of the basic block.
    basic = expect(rules(image, 120, 300, 0.5, 95, 340), 8, "info panel basic rules")
    # bands: [top margin, Level, TPs, Form, Class, points, subheader gap].
    basic_rows = bands(basic)[1:6]

    # From 385, so the STATS subheader's own underline at 375..379 stays out of the pairing.
    pills = expect(rules(image, 140, 460, 0.6, 385, 730), 14, "info panel stat-row rules")
    stat_rows = [(pills[i][0], pills[i + 1][1]) for i in range(0, 14, 2)]
    return {"basic": basic_rows, "stats": stat_rows}


def measure_stats_panel(image: Image.Image) -> dict:
    """STATISTICS slab: seven statistic rows, then a three-row summary box lower in the frame."""
    found = rules(image, 150, 460, 0.6, 95, 640)
    rows = expect([r for r in found if 100 <= r[0] <= 420], 9, "stats panel row rules")
    # bands: [top margin, then the seven readouts].
    stat_rows = bands(rows)[1:]

    box = expect([r for r in found if 495 <= r[0] <= 630], 4, "stats panel summary rules")
    return {"rows": stat_rows, "summary": bands(box)}


def sample_colour(px, cx: int, cy: int, pale: bool) -> int:
    best = None
    for y in range(cy - 16, cy + 16):
        for x in range(cx - 16, cx + 16):
            r, g, b = px[x, y][:3]
            _, lightness, saturation = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if pale:
                if lightness < 0.85:
                    continue
                score = lightness
            else:
                if not 0.35 <= lightness <= 0.72:
                    continue
                score = saturation
            if best is None or score > best[0]:
                best = (score, (r << 16) | (g << 8) | b)
    if best is None:
        raise BuildError(f"no colour found around {cx},{cy}; the reference render changed")
    return best[1]


def sample_colours() -> dict[str, int]:
    if not EXAMPLE.exists():
        raise BuildError("missing reference render: " + str(EXAMPLE))
    image = Image.open(EXAMPLE).convert("RGB")
    px = image.load()
    return {name: sample_colour(px, x, y, name in COLOUR_PALE)
            for name, (x, y) in COLOUR_SAMPLES.items()}


def load_sprites() -> tuple[dict[str, Image.Image], dict[str, Image.Image]]:
    """The stored atlas sprites, and the native-resolution panels the anchors were measured on."""
    if not SPLIT.is_dir():
        raise BuildError("source bundle missing: " + str(SPLIT))
    scaled: dict[str, Image.Image] = {}
    native: dict[str, Image.Image] = {}
    for name, (relative, crop) in SPRITES.items():
        image = load(relative, crop)
        native[name] = image
        scaled[name] = resize(image, (logical(image.width) * SCALE,
                                      logical(image.height) * SCALE))
    return scaled, native


def pack(sprites: dict[str, Image.Image]) -> dict[str, tuple[int, int, int, int]]:
    """Shelf packing, tallest first: small and fixed, so the simplest stable packer is right."""
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


def validate(sprites, placed) -> None:
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


def contact_sheet(sprites) -> Image.Image:
    """Every sprite at the size the game samples it, on a dark ground, boxed and labelled."""
    cell = 8
    columns = 4
    entries = sorted(sprites)
    widths = [logical(sprites[n].width // SCALE * SCALE) for n in entries]
    box_w = max(sprites[n].width // SCALE for n in entries) + cell * 2
    box_h = max(sprites[n].height // SCALE for n in entries) + cell * 3
    rows = (len(entries) + columns - 1) // columns
    sheet = Image.new("RGBA", (box_w * columns, box_h * rows), (16, 18, 26, 255))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(entries):
        image = sprites[name]
        small = resize(image, (image.width // SCALE, image.height // SCALE))
        cx = (index % columns) * box_w + cell
        cy = (index // columns) * box_h + cell * 2
        sheet.alpha_composite(small, (cx, cy))
        draw.rectangle([cx - 1, cy - 1, cx + small.width, cy + small.height],
                       outline=(80, 90, 120, 255))
        draw.text((cx, cy - cell * 2 + 2), f"{name} {small.width}x{small.height}",
                  fill=(210, 220, 240, 255))
    return sheet


def anchor_overlay(native: dict[str, Image.Image], info: dict, stats: dict) -> Image.Image:
    """The two slabs at native size with every emitted anchor drawn on top of them.

    This is the check that the measured rows really are the rows. A band in the wrong place, or a
    plus button off its shell, is obvious here and costs nothing to look at.
    """
    left = native["INFO_PANEL"]
    right = native["STATS_PANEL"]
    gap = 24
    sheet = Image.new("RGBA", (left.width + gap + right.width,
                               max(left.height, right.height)), (16, 18, 26, 255))
    sheet.alpha_composite(left, (0, 0))
    sheet.alpha_composite(right, (left.width + gap, 0))
    draw = ImageDraw.Draw(sheet)

    for top, bottom in info["basic"]:
        draw.rectangle([INFO_LABEL_X, top, INFO_RIGHT_X, bottom], outline=(0, 255, 120, 255))
        draw.line([INFO_VALUE_X, top, INFO_VALUE_X, bottom], fill=(0, 255, 120, 160))
    for top, bottom in info["stats"]:
        draw.rectangle([STAT_LABEL_X, top, STAT_RIGHT_X, bottom], outline=(255, 220, 0, 255))
        bx, by, bw, bh = PLUS_BUTTON
        draw.rectangle([bx, top + by, bx + bw, top + by + bh], outline=(255, 80, 80, 255))

    offset = left.width + gap
    for top, bottom in stats["rows"]:
        draw.rectangle([offset + STATISTIC_LABEL_X, top, offset + STATISTIC_RIGHT_X, bottom],
                       outline=(0, 200, 255, 255))
    for top, bottom in stats["summary"]:
        draw.rectangle([offset + SUMMARY_LABEL_X, top, offset + SUMMARY_RIGHT_X, bottom],
                       outline=(255, 120, 255, 255))
    return sheet


def java_source(placed, sprites, info, stats, colours) -> str:
    def sprite_line(name: str) -> str:
        x, y, w, h = placed[name]
        return (f"    public static final Sprite {name} = new Sprite("
                f"{x}, {y}, {w}, {h}, {w // SCALE}, {h // SCALE});")

    def band_array(name: str, rows, comment: str) -> str:
        entries = ",\n            ".join(
            f"{{{logical(top)}, {logical(bottom - top)}}}" for top, bottom in rows)
        return (f"    /** {comment} */\n"
                f"    public static final int[][] {name} = {{\n"
                f"            {entries}}};\n")

    lines = [
        "package net.bullettrain.xenopixelsmod.client.hud;",
        "",
        "import net.minecraft.resources.ResourceLocation;",
        "",
        "/**",
        " * Sprite rectangles, row anchors and palette for the neon DragonMineZ character screen.",
        " *",
        " * <p><b>Generated by {@code tools/gen_dmz_neon_atlas.py} -- do not edit.</b> Sprites are",
        " * stored at 5x logical density; draw size and source size stay separate so Minecraft",
        " * samples the supplied artwork instead of magnifying a pre-shrunk copy.",
        " *",
        " * <p>The panels are single slabs with their rows drawn into the art, so the row bands below",
        " * are the interiors: {@code {y, height}} in logical pixels, relative to the slab's own",
        " * top-left corner. They were measured off the art by the generator, not chosen.",
        " */",
        "public final class XenoNeonAtlas {",
        "",
        "    public static final ResourceLocation TEXTURE =",
        "            ResourceLocation.fromNamespaceAndPath(",
        "                    \"xenopixelsmod\", \"textures/gui/xeno_neon_character.png\");",
        "",
        f"    public static final int ATLAS_WIDTH = {ATLAS_W};",
        f"    public static final int ATLAS_HEIGHT = {ATLAS_H};",
        "",
        "    /** One sprite's atlas rectangle and logical on-screen size. */",
        "    public record Sprite(int u, int v, int sourceWidth, int sourceHeight,",
        "                         int width, int height) {}",
        "",
    ]
    lines += [sprite_line(name) for name in sorted(placed)]
    lines += ["", band_array("INFO_ROWS", info["basic"],
                             "Level, TPs, Form, Class and the assignable-points row.").rstrip()]
    lines += ["", band_array("STAT_ROWS", info["stats"],
                             "The seven spendable-stat shells, top to bottom.").rstrip()]
    lines += ["", band_array("STATISTIC_ROWS", stats["rows"],
                             "The seven derived-statistic rows on the right slab.").rstrip()]
    lines += ["", band_array("SUMMARY_ROWS", stats["summary"],
                             "The three-row summary box lower in the right slab.").rstrip()]
    lines += [
        "",
        "    /** Text insets inside the left slab, logical pixels from its left edge. */",
        f"    public static final int INFO_LABEL_X = {logical(INFO_LABEL_X)};",
        f"    public static final int INFO_VALUE_X = {logical(INFO_VALUE_X)};",
        f"    public static final int INFO_RIGHT_X = {logical(INFO_RIGHT_X)};",
        f"    public static final int STAT_LABEL_X = {logical(STAT_LABEL_X)};",
        f"    public static final int STAT_RIGHT_X = {logical(STAT_RIGHT_X)};",
        "",
        "    /** The plus button's rectangle, relative to the top-left of its stat row band. */",
        f"    public static final int PLUS_X = {logical(PLUS_BUTTON[0])};",
        f"    public static final int PLUS_Y = {logical(PLUS_BUTTON[1])};",
        f"    public static final int PLUS_WIDTH = {logical(PLUS_BUTTON[2])};",
        f"    public static final int PLUS_HEIGHT = {logical(PLUS_BUTTON[3])};",
        "",
        "    /** Text insets inside the right slab. */",
        f"    public static final int STATISTIC_LABEL_X = {logical(STATISTIC_LABEL_X)};",
        f"    public static final int STATISTIC_RIGHT_X = {logical(STATISTIC_RIGHT_X)};",
        f"    public static final int SUMMARY_LABEL_X = {logical(SUMMARY_LABEL_X)};",
        f"    public static final int SUMMARY_RIGHT_X = {logical(SUMMARY_RIGHT_X)};",
        "",
        "    /**",
        "     * The palette, sampled from {@code example/current_example_full_hud.png} at the",
        "     * coordinates recorded in the generator. These are the reference render's own colours,",
        "     * not an approximation of them.",
        "     */",
    ]
    for name in sorted(colours):
        lines.append(f"    public static final int {name} = 0xFF{colours[name]:06X};")
    lines += [
        "",
        "    private XenoNeonAtlas() {",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def build() -> dict:
    sprites, native = load_sprites()
    info = measure_info_panel(native["INFO_PANEL"])
    stats = measure_stats_panel(native["STATS_PANEL"])
    colours = sample_colours()
    placed = pack(sprites)
    validate(sprites, placed)

    atlas = png_bytes(render(sprites, placed))
    java = java_source(placed, sprites, info, stats, colours)
    contact = png_bytes(contact_sheet(sprites))
    overlay = png_bytes(anchor_overlay(native, info, stats))
    return {
        "atlas": atlas,
        "java": java,
        "contact": contact,
        "overlay": overlay,
        "manifest": {
            "atlas": {"width": ATLAS_W, "height": ATLAS_H, "sha256": digest(atlas)},
            "java_sha256": digest(java.encode("utf-8")),
            # Lists, not tuples: --check compares a fresh build against the JSON on disk, and JSON
            # has no tuple to read back.
            "sprites": {name: list(rect) for name, rect in sorted(placed.items())},
            "info_rows": [list(row) for row in info["basic"]],
            "stat_rows": [list(row) for row in info["stats"]],
            "statistic_rows": [list(row) for row in stats["rows"]],
            "summary_rows": [list(row) for row in stats["summary"]],
            "colours": {name: f"#{value:06X}" for name, value in sorted(colours.items())},
        },
    }


def write(result: dict) -> None:
    GEN_DIR.mkdir(parents=True, exist_ok=True)
    ATLAS_PNG.parent.mkdir(parents=True, exist_ok=True)
    ATLAS_PNG.write_bytes(result["atlas"])
    JAVA_OUT.write_text(result["java"], encoding="utf-8")
    CONTACT.write_bytes(result["contact"])
    OVERLAY.write_bytes(result["overlay"])
    MANIFEST.write_text(json.dumps(result["manifest"], indent=2) + "\n", encoding="utf-8")


def check(result: dict) -> int:
    problems = []
    if not ATLAS_PNG.exists() or digest(ATLAS_PNG.read_bytes()) != result["manifest"]["atlas"]["sha256"]:
        problems.append("atlas PNG is missing or stale: " + str(ATLAS_PNG))
    if not JAVA_OUT.exists() or JAVA_OUT.read_text(encoding="utf-8") != result["java"]:
        problems.append("generated Java is missing or stale: " + str(JAVA_OUT))
    if not MANIFEST.exists():
        problems.append("manifest is missing: " + str(MANIFEST))
    else:
        stored = json.loads(MANIFEST.read_text(encoding="utf-8"))
        if stored != result["manifest"]:
            problems.append("manifest does not match a fresh build: " + str(MANIFEST))
    for problem in problems:
        print("FAIL " + problem, file=sys.stderr)
    if problems:
        print("Run: python tools/gen_dmz_neon_atlas.py", file=sys.stderr)
        return 1
    print("OK  xeno_neon_character atlas, Java and manifest are current")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the committed output matches a fresh build; write nothing")
    args = parser.parse_args()
    try:
        result = build()
    except BuildError as error:
        print("FAIL " + str(error), file=sys.stderr)
        return 1
    if args.check:
        return check(result)
    write(result)
    manifest = result["manifest"]
    print(f"wrote {ATLAS_PNG.relative_to(ROOT)} ({len(result['atlas'])} bytes, "
          f"sha256 {manifest['atlas']['sha256'][:16]}...)")
    print(f"wrote {JAVA_OUT.relative_to(ROOT)}")
    print(f"wrote {CONTACT.relative_to(ROOT)} and {OVERLAY.relative_to(ROOT)}")
    print(f"      {len(manifest['sprites'])} sprites, "
          f"{len(manifest['info_rows'])} info rows, {len(manifest['stat_rows'])} stat rows, "
          f"{len(manifest['statistic_rows'])} statistic rows, "
          f"{len(manifest['summary_rows'])} summary rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
