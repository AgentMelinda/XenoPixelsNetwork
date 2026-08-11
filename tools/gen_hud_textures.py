"""Generate the XenoPixels HUD chrome atlas.

Writes ``assets/xenopixelsmod/textures/gui/xeno_hud_atlas.png``.

Fills the gap named in ``client/hud/XenoHudTextures.java``: there was no HUD chrome atlas, so
the LDLib view could only draw flat tinted rectangles. Everything here is generated so the art
stays source-controlled and regenerable, matching the other ``tools/gen_*.py`` scripts.

Pure stdlib — the ``write_png`` implementation is the one already proven in
``gen_ballistic_textures.py``.

Run: ``python tools/gen_hud_textures.py``
"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

from hud_layout import (
    ATLAS, BAR_TICKS, BAR_TIP_W, CD_BADGE_CORNER, CD_CHIP_CORNER, PALETTE, PANEL_CORNER,
    REGIONS, SKEW,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_hud_atlas.png"

CLEAR = (0, 0, 0, 0)


def write_png(path: Path, pixels) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y][x]
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(
            ">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path} ({len(png)} bytes)")


def blank():
    return [[CLEAR for _ in range(ATLAS)] for _ in range(ATLAS)]


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def setp(px, x, y, c):
    if 0 <= x < ATLAS and 0 <= y < ATLAS:
        px[y][x] = c


def skew_offset(row, height):
    """Horizontal shift for a parallelogram: top rows lean right."""
    if height <= 1:
        return 0
    return round(SKEW * (1.0 - row / (height - 1)))


def body_width(w):
    """Drawn width of a skewed element, so the lean stays inside the declared region.

    A parallelogram drawn at the full region width spans ``w + SKEW`` once the top rows lean
    right, overrunning its own box by up to SKEW px. That is not merely untidy: ``stm_off``,
    ``stm_on`` and ``stm_tip`` are packed 14 px apart, so the overrun painted each segment's
    lean into the left edge of the next one. Narrowing the body by SKEW keeps the shape whole
    and self-contained, at the cost of a few transparent columns at the bottom-right.
    """
    return max(1, w - SKEW)


def bar(px, region, dark, mid, light, filled):
    """A skewed bar. Filled bars get a vertical gradient plus a gloss band and a lit tip.

    Empty tracks get quarter ticks etched in, so a player can read roughly how much is left
    without parsing the number — a flat trough gives no reference points.
    """
    x0, y0, w, h = REGIONS[region]
    bw = body_width(w)
    ticks = {round(bw * i / BAR_TICKS) for i in range(1, BAR_TICKS)}
    for row in range(h):
        t = row / max(1, h - 1)
        if filled:
            # Brightest just above centre, so it reads as a lit tube rather than a ramp.
            base = mix(light, mid, abs(t - 0.35) * 1.6)
            base = mix(base, dark, max(0.0, t - 0.7) * 2.0)
        else:
            base = mix(PALETTE["track_light"], PALETTE["track"], t)
        off = skew_offset(row, h)
        for col in range(bw):
            c = base
            if filled and row == max(1, int(h * 0.25)):
                c = mix(c, PALETTE["gloss"], 0.30)   # gloss band
            if filled and col >= bw - 3:
                c = mix(c, PALETTE["gloss"], 0.45)   # lit leading tip
            if not filled and col in ticks and 1 < row < h - 2:
                c = mix(c, PALETTE["chrome_light"], 0.55)
            if row == 0 or row == h - 1:
                c = mix(c, PALETTE["chrome_dark"], 0.55)
            setp(px, x0 + col + off, y0 + row, (*c, 255))


def bar_tip(px, region):
    """Neutral white-hot cap stamped at a partial bar's fill boundary.

    Left as an uncoloured gradient rather than one cap per bar: the view tints it to the bar's
    own accent at draw time, so HP, KI and the critical variant share a single sprite.
    """
    x0, y0, w, h = REGIONS[region]
    bw = body_width(w)
    for row in range(h):
        t = row / max(1, h - 1)
        off = skew_offset(row, h)
        for col in range(bw):
            # Ramp up toward the leading edge so the cap fades into the fill behind it.
            lead = col / max(1, bw - 1)
            c = mix(PALETTE["chrome_light"], PALETTE["gloss"], lead ** 1.5)
            a = int(40 + 215 * lead)
            if row == 0 or row == h - 1:
                c = mix(c, PALETTE["chrome_dark"], 0.55)
            elif row == max(1, int(h * 0.25)):
                c = PALETTE["gloss"]
            setp(px, x0 + col + off, y0 + row, (*c, a))


def frame(px, region):
    """Bevelled outer edge with an inner shadow, drawn skewed to match the bars."""
    x0, y0, w, h = REGIONS[region]
    bw = body_width(w)
    for row in range(h):
        off = skew_offset(row, h)
        for col in range(bw):
            edge = row == 0 or row == h - 1 or col == 0 or col == bw - 1
            inner = row == 1 or row == h - 2 or col == 1 or col == bw - 2
            if edge:
                c = PALETTE["chrome_light"] if row == 0 else PALETTE["chrome_dark"]
                setp(px, x0 + col + off, y0 + row, (*c, 255))
            elif inner:
                setp(px, x0 + col + off, y0 + row, (*PALETTE["chrome_dark"], 140))


def segment(px, region, dark, mid, light, lit):
    x0, y0, w, h = REGIONS[region]
    bw = body_width(w)
    for row in range(h):
        t = row / max(1, h - 1)
        base = mix(light, dark, t) if lit else mix(PALETTE["track_light"], PALETTE["track"], t)
        off = skew_offset(row, h)
        for col in range(bw):
            c = base
            if lit and row == 1:
                c = mix(c, PALETTE["gloss"], 0.35)
            if col == 0 or col == bw - 1 or row == 0 or row == h - 1:
                c = mix(c, PALETTE["chrome_dark"], 0.5)
            setp(px, x0 + col + off, y0 + row, (*c, 255))


def panel_nine(px, region):
    """9-sliceable backing plate.

    Authored so the outer {@code PANEL_CORNER} band holds the whole bevel and the centre is a
    flat wash — that is what lets it cover any cluster size without smearing the border.
    A single stretched tile (which this replaced) distorted both bevel and vignette.
    """
    x0, y0, w, h = REGIONS[region]
    for row in range(h):
        for col in range(w):
            # Distance into the plate from the nearest edge, capped at the corner inset.
            edge = min(col, row, w - 1 - col, h - 1 - row)
            depth = min(edge, PANEL_CORNER) / PANEL_CORNER
            c = mix(PALETTE["chrome_dark"], PALETTE["chrome_mid"], depth)
            a = 200 + int(40 * (1.0 - depth))
            if edge == 0:
                c = PALETTE["chrome_light"] if row < h / 2 else PALETTE["chrome_dark"]
                a = 255
            elif edge == 1:
                c = mix(c, PALETTE["chrome_dark"], 0.5)
                a = 240
            elif edge == 2 and row < h / 2:
                c = mix(c, PALETTE["accent"], 0.20)
            setp(px, x0 + col, y0 + row, (*c, a))


def panel(px, region, corner_accent=False):
    """Navy glass panel: translucent centre, solid bevelled border, soft vignette."""
    x0, y0, w, h = REGIONS[region]
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for row in range(h):
        for col in range(w):
            dx = (col - cx) / max(1.0, cx)
            dy = (row - cy) / max(1.0, cy)
            vignette = min(1.0, (dx * dx + dy * dy) ** 0.5)
            c = mix(PALETTE["chrome_mid"], PALETTE["chrome_dark"], vignette * 0.8)
            a = 190
            if row in (0, h - 1) or col in (0, w - 1):
                c = PALETTE["chrome_light"] if row == 0 else PALETTE["chrome_dark"]
                a = 255
            elif row in (1, h - 2) or col in (1, w - 2):
                c = mix(c, PALETTE["chrome_dark"], 0.4)
                a = 230
            setp(px, x0 + col, y0 + row, (*c, a))
    if corner_accent:
        for i in range(6):
            setp(px, x0 + i, y0, (*PALETTE["accent"], 255))
            setp(px, x0, y0 + i, (*PALETTE["accent"], 255))
            setp(px, x0 + w - 1 - i, y0 + h - 1, (*PALETTE["accent"], 255))
            setp(px, x0 + w - 1, y0 + h - 1 - i, (*PALETTE["accent"], 255))


def pip(px, region, colour, lit):
    x0, y0, w, h = REGIONS[region]
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    radius = min(cx, cy)
    for row in range(h):
        for col in range(w):
            d = ((col - cx) ** 2 + (row - cy) ** 2) ** 0.5
            if d > radius:
                continue
            c = colour if lit else PALETTE["track_light"]
            if d > radius - 1.2:
                c = mix(c, PALETTE["chrome_dark"], 0.5)
            elif lit:
                c = mix(c, PALETTE["gloss"], 0.25)
            setp(px, x0 + col, y0 + row, (*c, 255))


def cd_chip(px, region, corner, body_top, body_bottom, edge, alpha=235, sheen=True):
    """Nine-sliceable glass chip plate for the combat cooldown strip.

    The whole bevel is kept inside ``corner`` pixels of each edge and the middle is a flat
    vertical wash, which is the condition for stretching the centre to any chip size without
    the border smearing. The gloss sheen therefore has to live in the top corner band, not
    across the full width.
    """
    x0, y0, w, h = REGIONS[region]
    for row in range(h):
        t = row / max(1, h - 1)
        base = mix(body_top, body_bottom, t)
        for col in range(w):
            depth = min(min(col, row, w - 1 - col, h - 1 - row), corner) / corner
            c = base
            a = alpha
            if depth == 0.0:
                # Outer edge: lit along the top, shadowed along the bottom.
                c = edge if row < h / 2 else mix(edge, PALETTE["chrome_dark"], 0.6)
                a = 255
            elif row == 1 and sheen:
                c = mix(base, PALETTE["gloss"], 0.22)
            elif row == h - 2:
                c = mix(base, PALETTE["chrome_dark"], 0.35)
            setp(px, x0 + col, y0 + row, (*c, a))


def cd_rail(px, region):
    """Accent rail down a chip's left edge. Painted white; the view tints it per move."""
    x0, y0, w, h = REGIONS[region]
    for row in range(h):
        for col in range(w):
            # Bright core with darker shoulders so it reads as a lit strip, not a flat bar.
            shoulder = abs(col - (w - 1) / 2.0) / max(1.0, (w - 1) / 2.0)
            c = mix(PALETTE["gloss"], PALETTE["chrome_dark"], shoulder * 0.55)
            a = 255 if row not in (0, h - 1) else 170
            setp(px, x0 + col, y0 + row, (*c, a))


def cd_meter(px, region, filled):
    """Small cooldown/charge meter. The fill is white so one sprite serves every accent."""
    x0, y0, w, h = REGIONS[region]
    for row in range(h):
        t = row / max(1, h - 1)
        if filled:
            base = mix(PALETTE["gloss"], mix(PALETTE["gloss"], PALETTE["chrome_dark"], 0.45), t)
        else:
            base = mix(PALETTE["track_light"], PALETTE["track"], t)
        for col in range(w):
            c = base
            if row == 0 or row == h - 1:
                c = mix(c, PALETTE["chrome_dark"], 0.5)
            elif filled and col >= w - 2:
                # Leading edge, so a partially scaled fill still shows where it ends.
                c = PALETTE["gloss"]
            setp(px, x0 + col, y0 + row, (*c, 255))


def main():
    px = blank()
    p = PALETTE

    bar(px, "hp_empty", p["hp_dark"], p["hp_mid"], p["hp_light"], filled=False)
    bar(px, "hp_full", p["hp_dark"], p["hp_mid"], p["hp_light"], filled=True)
    bar(px, "ki_empty", p["ki_dark"], p["ki_mid"], p["ki_light"], filled=False)
    bar(px, "ki_full", p["ki_dark"], p["ki_mid"], p["ki_light"], filled=True)
    bar(px, "hp_crit", p["crit_dark"], p["crit_mid"], p["crit_light"], filled=True)
    frame(px, "bar_frame")
    bar_tip(px, "bar_tip")

    segment(px, "stm_off", p["stm_dark"], p["stm_mid"], p["stm_light"], lit=False)
    segment(px, "stm_on", p["stm_dark"], p["stm_mid"], p["stm_light"], lit=True)
    segment(px, "stm_tip", p["stm_mid"], p["stm_light"], (255, 250, 220), lit=True)

    panel(px, "portrait", corner_accent=True)
    panel_nine(px, "panel")

    pip(px, "spark_off", p["stm_light"], lit=False)
    pip(px, "spark_on", p["stm_light"], lit=True)

    # Cooldown strip: idle / active / disabled chip plates, key badge, rail and meter.
    cd_chip(px, "cd_chip", CD_CHIP_CORNER, p["chrome_mid"], p["chrome_dark"], p["chrome_light"])
    cd_chip(px, "cd_chip_hot", CD_CHIP_CORNER,
            mix(p["chrome_mid"], p["accent"], 0.35), p["chrome_mid"], p["accent"])
    cd_chip(px, "cd_chip_off", CD_CHIP_CORNER,
            p["track"], p["track"], p["track_light"], alpha=170, sheen=False)
    cd_chip(px, "cd_badge", CD_BADGE_CORNER,
            p["chrome_dark"], (0, 0, 0), p["chrome_light"], alpha=225)
    cd_rail(px, "cd_rail")
    cd_meter(px, "cd_meter_track", filled=False)
    cd_meter(px, "cd_meter_fill", filled=True)

    write_png(OUT, px)
    print(f"atlas {ATLAS}x{ATLAS}; {len(REGIONS)} regions")


if __name__ == "__main__":
    main()
