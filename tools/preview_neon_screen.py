#!/usr/bin/env python3
"""Composites the neon character screen the way XenoNeonStatsScreen lays it out.

This is a layout proof, not a renderer. It reads the generated `XenoNeonAtlas.java` -- the same
sprite rectangles, row bands and palette the screen reads -- and places them with the same
arithmetic `init()` and the render methods use, on a canvas the size DragonMineZ's `ScaledScreen`
guarantees. A panel pushed off the canvas, a row band that misses its shell, a navigation row that
overruns, or a readout colliding with its neighbour is visible here, at no cost, before anyone
launches Minecraft.

It cannot prove what the live screen shows: the real font is Minecraft's, the character in the ring
is drawn by DragonMineZ, and the numbers come from the player. Treat a clean preview as "the layout
arithmetic agrees with the art", nothing more.

Usage:  python tools/preview_neon_screen.py [--width 320] [--height 240]
"""

from __future__ import annotations

import argparse
import pathlib
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
ATLAS = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_neon_character.png"
JAVA = ROOT / "src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoNeonAtlas.java"
OUT = ROOT / "tools/generated/xeno_neon_screen_preview.png"

# Kept in step with XenoNeonStatsScreen's own constants.
PANEL_GAP = 90
NAV_GAP = 3
ORB_GAP = 4
TEXT_INSET = 1
ZOOM = 3

# Sample readouts as raw numbers, then abbreviated by the same rule StatText.format uses, so the
# preview shows the strings the screen will really draw rather than the reference render's long
# ungrouped ones.
def fmt(value: float) -> str:
    a = abs(value)
    if a >= 1e9:
        return f"{value / 1e9:.2f}B"
    if a >= 1e6:
        return f"{value / 1e6:.2f}M"
    if a >= 1e4:
        return f"{value / 1e3:.1f}K"
    return str(int(value)) if value == int(value) else f"{value:.1f}"


INFO_ROWS = [("Level", fmt(5018513)), ("TPs", fmt(1.166e11)), ("Form", "Kaioken x20"),
             ("Class", "Warrior"), ("Points", "12")]
STAT_ROWS = [("STR", fmt(126727), "x3.9", "RED"), ("SKP", fmt(130361), "x3.9", "RED"),
             ("RES", fmt(32203590), "x3.2", "RED"), ("VIT", fmt(10006999), "x1", "GREEN"),
             ("PWR", fmt(86939), "x3.9", "MAGENTA"), ("ENE", fmt(10011999), "x1", "CYAN"),
             ("TPC", fmt(37638882), "x1", "VALUE")]
STATISTICS = [("Melee DMG", fmt(208698.1), "GOLD"), ("Strike DMG", fmt(205281.9), "GOLD"),
              ("Stamina", fmt(14479783), "VALUE"), ("Defense", fmt(11083806), "GOLD"),
              ("Health", fmt(18012618), "VALUE"), ("Ki DMG", fmt(52188.9), "GOLD"),
              ("Max Ki", fmt(15018018), "VALUE")]
SUMMARY = [("Power Level", fmt(4.02e9), "VALUE"), ("Gravity", "x9.5", "ORANGE"),
           ("TP Multiplier", "x1.25", "GOLD")]


def parse_java() -> tuple[dict, dict, dict]:
    text = JAVA.read_text(encoding="utf-8")
    sprites = {m[1]: tuple(int(v.strip()) for v in m[2].split(","))
               for m in re.finditer(r"Sprite (\w+) = new Sprite\(([^)]+)\)", text)}
    bands = {}
    for name in ("INFO_ROWS", "STAT_ROWS", "STATISTIC_ROWS", "SUMMARY_ROWS"):
        block = re.search(name + r" = \{(.*?)\};", text, re.S)
        bands[name] = [tuple(int(v) for v in pair.split(","))
                       for pair in re.findall(r"\{(\d+, *\d+)\}", block.group(1))]
    ints = {m[1]: int(m[2]) for m in re.finditer(r"static final int (\w+) = (\d+);", text)}
    colours = {m[1]: int(m[2], 16) for m in re.finditer(r"static final int (\w+) = 0xFF([0-9A-F]{6});", text)}
    ints.update({k: v for k, v in ints.items()})
    return sprites, bands, {"ints": ints, "colours": colours}


def rgb(value: int) -> tuple[int, int, int]:
    return (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--width", type=int, default=320,
                        help="the ScaledScreen canvas width (its guaranteed minimum is 320)")
    parser.add_argument("--height", type=int, default=240,
                        help="the ScaledScreen canvas height (its guaranteed minimum is 240)")
    args = parser.parse_args()

    sprites, bands, extra = parse_java()
    ints, colours = extra["ints"], extra["colours"]
    atlas = Image.open(ATLAS).convert("RGBA")

    def sprite(name: str) -> Image.Image:
        u, v, sw, sh, w, h = sprites[name]
        return atlas.crop((u, v, u + sw, v + sh)).resize((w, h), Image.Resampling.LANCZOS)

    def size(name: str) -> tuple[int, int]:
        return sprites[name][4], sprites[name][5]

    info_w, info_h = size("INFO_PANEL")
    stats_w, stats_h = size("STATS_PANEL")
    plate_w, plate_h = size("NAMEPLATE")
    ring_w, ring_h = size("SCAN_RING")

    # XenoNeonStatsScreen.init(), verbatim.
    total = info_w + PANEL_GAP + stats_w
    info_x = (args.width - total) // 2
    stats_x = info_x + info_w + PANEL_GAP
    panels_y = max(plate_h + 4, (args.height - info_h) // 2 + 6)
    plate_x = (args.width - plate_w) // 2
    plate_y = max(2, panels_y - plate_h - 2)
    nav_names = ["NAV_CHARACTER", "NAV_SKILLS", "NAV_QUESTS", "NAV_ITEMS", "NAV_PARTY",
                 "NAV_SETTINGS"]
    nav_w = sum(size(n)[0] for n in nav_names) + NAV_GAP * (len(nav_names) - 1)
    nav_x = (args.width - nav_w) // 2
    nav_y = panels_y + info_h + NAV_GAP
    ring_x = info_x + info_w + (PANEL_GAP - ring_w) // 2
    ring_y = panels_y + 30
    orb_names = ["ORB_BLUE", "ORB_GOLD", "ORB_RED"]
    orbs_w = sum(size(n)[0] for n in orb_names) + ORB_GAP * (len(orb_names) - 1)
    orbs_x = stats_x + stats_w - orbs_w
    orbs_y = max(2, plate_y + 2)

    canvas = Image.new("RGBA", (args.width, args.height), (26, 30, 40, 255))
    for name, x, y in (("NAMEPLATE", plate_x, plate_y), ("INFO_PANEL", info_x, panels_y),
                       ("STATS_PANEL", stats_x, panels_y), ("SCAN_RING", ring_x, ring_y)):
        canvas.alpha_composite(sprite(name), (x, y))
    x = orbs_x
    for name in orb_names:
        canvas.alpha_composite(sprite(name), (x, orbs_y))
        x += size(name)[0] + ORB_GAP
    x = nav_x
    for name in nav_names:
        canvas.alpha_composite(sprite(name), (x, nav_y))
        x += size(name)[0] + NAV_GAP

    canvas = canvas.resize((args.width * ZOOM, args.height * ZOOM), Image.Resampling.NEAREST)
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype("consola.ttf", 7 * ZOOM)
    except OSError:
        font = ImageFont.load_default()

    def at(text: str, x: int, y: int, colour: str, right=False):
        if right:
            x -= draw.textlength(text, font=font) / ZOOM
        draw.text((x * ZOOM, y * ZOOM), text, font=font, fill=rgb(colours[colour]))

    for (label, value), (top, _) in zip(INFO_ROWS, bands["INFO_ROWS"]):
        y = panels_y + top + TEXT_INSET
        at(label, info_x + ints["INFO_LABEL_X"], y, "LABEL")
        at(value, info_x + ints["INFO_VALUE_X"], y, "RED" if label == "Class" else "VALUE")
    for (label, value, mult, colour), (top, _) in zip(STAT_ROWS, bands["STAT_ROWS"]):
        y = panels_y + top + TEXT_INSET
        at(label, info_x + ints["STAT_LABEL_X"], y, colour)
        right = info_x + ints["STAT_RIGHT_X"]
        at(mult, right, y, "GOLD", right=True)
        at(value, right - len(mult) * 4 - 4, y, "VALUE", right=True)
        draw.rectangle([(info_x + ints["PLUS_X"]) * ZOOM, (panels_y + top + ints["PLUS_Y"]) * ZOOM,
                        (info_x + ints["PLUS_X"] + ints["PLUS_WIDTH"]) * ZOOM,
                        (panels_y + top + ints["PLUS_Y"] + ints["PLUS_HEIGHT"]) * ZOOM],
                       outline=(255, 80, 80, 200))
    for (label, value, colour), (top, _) in zip(STATISTICS, bands["STATISTIC_ROWS"]):
        y = panels_y + top + TEXT_INSET
        at(label, stats_x + ints["STATISTIC_LABEL_X"], y, "LABEL")
        at(value, stats_x + ints["STATISTIC_RIGHT_X"], y, colour, right=True)
    for (label, value, colour), (top, _) in zip(SUMMARY, bands["SUMMARY_ROWS"]):
        y = panels_y + top + TEXT_INSET
        at(label, stats_x + ints["SUMMARY_LABEL_X"], y, "ORANGE" if label == "Gravity" else "LABEL")
        at(value, stats_x + ints["SUMMARY_RIGHT_X"], y, colour, right=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT)

    overflow = []
    if info_x < 0:
        overflow.append(f"panels overflow the canvas: left edge at {info_x}")
    if nav_y + size("NAV_CHARACTER")[1] > args.height:
        overflow.append(f"navigation row runs past the bottom: "
                        f"{nav_y + size('NAV_CHARACTER')[1]} > {args.height}")
    if plate_y < 0:
        overflow.append("nameplate runs off the top")
    if ring_x < info_x + info_w or ring_x + ring_w > stats_x:
        overflow.append("scan ring overlaps a panel")
    print(f"wrote {OUT.relative_to(ROOT)} for a {args.width}x{args.height} canvas")
    print(f"      panels {info_x},{panels_y}  nav {nav_x},{nav_y}  ring {ring_x},{ring_y}")
    for problem in overflow:
        print("WARN  " + problem)
    return 1 if overflow else 0


if __name__ == "__main__":
    raise SystemExit(main())
