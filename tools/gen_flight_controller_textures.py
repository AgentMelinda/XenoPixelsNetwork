"""Generate the flight controller atlas and its emissive glowmask.

Writes ``textures/block/flight_controller.png`` and ``flight_controller_glowmask.png``.

Pure stdlib, no Pillow — matching ``gen_ballistic_textures.py``, whose ``write_png`` is reused
here. Minecraft has no normal maps without shaders, so depth comes entirely from baked
shading: gradients, 1px bevels and directional noise.

Run: ``python tools/gen_flight_controller_textures.py``
"""
from __future__ import annotations

import random
import struct
import zlib
from pathlib import Path

from flight_controller_layout import GLOW, PALETTE, REGIONS, SIZE

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block"

TRANSPARENT = (0, 0, 0, 0)


def write_png(path: Path, pixels) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter None
        for x in range(w):
            r, g, b, a = pixels[y][x]
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(
            ">I", zlib.crc32(tag + data) & 0xFFFFFFFF
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path} ({len(png)} bytes)")


def blank(c=TRANSPARENT):
    return [[c for _ in range(SIZE)] for _ in range(SIZE)]


def setp(px, x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[y][x] = c


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vgrad(px, region, top, bottom):
    """Vertical gradient — the cheapest way to stop a surface reading flat."""
    x0, y0, w, h = region
    for y in range(h):
        t = y / max(1, h - 1)
        c = mix(top, bottom, t)
        for x in range(w):
            setp(px, x0 + x, y0 + y, (*c, 255))


def brushed(px, region, seed, amount=10):
    """Directional streak noise, for machined metal."""
    x0, y0, w, h = region
    rng = random.Random(seed)
    for y in range(h):
        streak = rng.randint(-amount, amount)
        for x in range(w):
            if rng.random() < 0.35:
                streak = rng.randint(-amount, amount)
            r, g, b, a = px[y0 + y][x0 + x]
            setp(px, x0 + x, y0 + y, (clamp(r + streak), clamp(g + streak), clamp(b + streak), a))


def bevel(px, region, light, dark):
    """1px highlight top/left, shadow bottom/right. This is what sells depth."""
    x0, y0, w, h = region
    for x in range(w):
        setp(px, x0 + x, y0, (*light, 255))
        setp(px, x0 + x, y0 + h - 1, (*dark, 255))
    for y in range(h):
        setp(px, x0, y0 + y, (*light, 255))
        setp(px, x0 + w - 1, y0 + y, (*dark, 255))


def panel_lines(px, region, spacing, colour):
    x0, y0, w, h = region
    for x in range(spacing, w - 1, spacing):
        for y in range(2, h - 2):
            setp(px, x0 + x, y0 + y, (*colour, 255))


def scanlines(px, region, colour, step=3):
    x0, y0, w, h = region
    for y in range(0, h, step):
        for x in range(w):
            r, g, b, a = px[y0 + y][x0 + x]
            setp(px, x0 + x, y0 + y, (*mix((r, g, b), colour, 0.35), a))


def fill(px, region, colour):
    x0, y0, w, h = region
    for y in range(h):
        for x in range(w):
            setp(px, x0 + x, y0 + y, (*colour, 255))


def build_base():
    px = blank()
    p = PALETTE

    vgrad(px, REGIONS["housing"], p["housing_light"], p["housing_dark"])
    brushed(px, REGIONS["housing"], 11)
    panel_lines(px, REGIONS["housing"], 12, p["housing_dark"])
    bevel(px, REGIONS["housing"], p["housing_light"], p["housing_dark"])

    vgrad(px, REGIONS["deck"], p["deck_light"], p["deck_dark"])
    brushed(px, REGIONS["deck"], 22)
    panel_lines(px, REGIONS["deck"], 10, p["deck_dark"])
    bevel(px, REGIONS["deck"], p["deck_light"], p["deck_dark"])

    vgrad(px, REGIONS["console"], p["console_light"], p["console_dark"])
    brushed(px, REGIONS["console"], 33, 7)
    bevel(px, REGIONS["console"], p["console_light"], p["console_dark"])

    vgrad(px, REGIONS["screen"], p["screen_light"], p["screen_dark"])
    scanlines(px, REGIONS["screen"], p["screen_dark"])
    bevel(px, REGIONS["screen"], p["screen_light"], p["screen_dark"])

    vgrad(px, REGIONS["lever"], p["lever_light"], p["lever_dark"])
    bevel(px, REGIONS["lever"], p["lever_light"], p["lever_dark"])

    for name, base in (("btn_mode", "amber"), ("btn_engage", "green"), ("btn_map", "cyan")):
        vgrad(px, REGIONS[name], p[f"{base}_light"], p[f"{base}_dark"])
        bevel(px, REGIONS[name], p[f"{base}_light"], p[f"{base}_dark"])

    # Vents read as dark slots with a lit upper lip.
    fill(px, REGIONS["vent"], p["vent_dark"])
    bevel(px, REGIONS["vent"], p["housing_mid"], p["vent_dark"])

    vgrad(px, REGIONS["conduit"], p["housing_mid"], p["housing_dark"])
    panel_lines(px, REGIONS["conduit"], 6, p["vent_dark"])

    vgrad(px, REGIONS["trim"], p["cyan_light"], p["trim_mid"])
    bevel(px, REGIONS["trim"], p["cyan_light"], p["cyan_dark"])

    return px


def build_glowmask():
    """Transparent everywhere except the emissive regions.

    Anything opaque here glows at full brightness, so the housing must stay clear — a
    fully-painted mask makes the entire block self-lit.
    """
    px = blank()
    for name, colour in GLOW.items():
        x0, y0, w, h = REGIONS[name]
        for y in range(h):
            t = y / max(1, h - 1)
            c = mix(colour, tuple(int(v * 0.55) for v in colour), t)
            for x in range(w):
                setp(px, x0 + x, y0 + y, (*c, 255))
    # Scanlines on the screen so the glow is textured rather than a flat slab of light.
    scanlines(px, REGIONS["screen"], (10, 30, 40), step=3)
    return px


def main():
    write_png(OUT / "flight_controller.png", build_base())
    write_png(OUT / "flight_controller_glowmask.png", build_glowmask())
    print(f"atlas {SIZE}x{SIZE}; emissive regions: {sorted(GLOW)}")


if __name__ == "__main__":
    main()
