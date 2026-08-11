"""Generate 16x16 block textures as PNG without Pillow.

SUPERSEDED by ``gen_vs2_block_textures.py``, which draws the whole ship set on the shared
XenoPixels palette. This script still writes some of the same filenames in the older
brass-and-gold style, so running it reverts that art. Kept for reference only — its
``write_png`` is the implementation the newer generators reuse.
"""
from __future__ import annotations

import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block"


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
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


def blank(c=(0, 0, 0, 255)):
    return [[c for _ in range(16)] for _ in range(16)]


def setp(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[y][x] = c


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            setp(px, x, y, c)


def outline(px, c):
    for i in range(16):
        setp(px, i, 0, c)
        setp(px, i, 15, c)
        setp(px, 0, i, c)
        setp(px, 15, i, c)


def metal(seed, dark, light):
    rng = random.Random(seed)
    px = blank()
    for y in range(16):
        for x in range(16):
            base = dark if (x + y) % 5 < 3 else light
            r = max(0, min(255, base[0] + rng.randint(-10, 10)))
            g = max(0, min(255, base[1] + rng.randint(-10, 10)))
            b = max(0, min(255, base[2] + rng.randint(-10, 10)))
            px[y][x] = (r, g, b, 255)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13), (8, 8)]:
        setp(px, x, y, (180, 190, 200, 255))
        setp(px, x + 1, y, (160, 170, 180, 255))
    return px


# Guidance computer — cyan screen + crosshair
px = metal(42, (28, 36, 48), (55, 70, 90))
rect(px, 3, 3, 12, 11, (10, 25, 40, 255))
rect(px, 4, 4, 11, 10, (20, 80, 120, 255))
for y in range(5, 10):
    col = (60, 200, 255, 255) if y % 2 == 0 else (30, 120, 180, 255)
    for x in range(5, 11):
        setp(px, x, y, col)
setp(px, 7, 7, (255, 255, 255, 255))
for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
    setp(px, 7 + dx, 7 + dy, (120, 230, 255, 255))
setp(px, 4, 13, (0, 255, 120, 255))
setp(px, 6, 13, (255, 200, 40, 255))
setp(px, 8, 13, (80, 180, 255, 255))
outline(px, (40, 160, 220, 255))
write_png(OUT / "ship_vls_guidance.png", px)

# Chunk loader — amber radar
px = metal(7, (50, 42, 30), (90, 75, 45))
rect(px, 2, 2, 13, 13, (30, 25, 18, 255))
for y in range(4, 12):
    for x in range(4, 12):
        dx, dy = x - 7.5, y - 7.5
        if dx * dx + dy * dy <= 16:
            px[y][x] = (60, 45, 20, 255)
        if 9 <= dx * dx + dy * dy <= 14:
            px[y][x] = (255, 180, 40, 255)
for y in range(6, 10):
    for x in range(6, 10):
        px[y][x] = (255, 210, 80, 255)
for p in [(7, 3), (7, 12), (3, 7), (12, 7)]:
    setp(px, p[0], p[1], (255, 220, 100, 255))
outline(px, (180, 140, 40, 255))
write_png(OUT / "missile_chunk_loader.png", px)

# Missile tube
px = blank((20, 20, 25, 255))
rng = random.Random(99)
for y in range(16):
    for x in range(16):
        if x < 3 or x > 12:
            c = 70 + rng.randint(-15, 15)
            px[y][x] = (c, c + 5, c + 10, 255)
        else:
            c = 25 + rng.randint(0, 12)
            px[y][x] = (c, c, c + 5, 255)
rect(px, 3, 0, 12, 2, (120, 125, 135, 255))
rect(px, 3, 13, 12, 15, (90, 95, 105, 255))
for y in range(5, 11):
    for x in range(5, 11):
        dx, dy = x - 7.5, y - 7.5
        if 6 <= dx * dx + dy * dy <= 10:
            px[y][x] = (90, 100, 120, 255)
        if dx * dx + dy * dy < 4:
            px[y][x] = (15, 15, 20, 255)
setp(px, 0, 7, (220, 160, 20, 255))
setp(px, 1, 7, (220, 160, 20, 255))
setp(px, 14, 7, (220, 160, 20, 255))
setp(px, 15, 7, (220, 160, 20, 255))
write_png(OUT / "missile_tube.png", px)

# Thruster
px = metal(3, (45, 40, 40), (80, 70, 65))
for y in range(3, 13):
    for x in range(3, 13):
        dx, dy = x - 7.5, y - 7.5
        r2 = dx * dx + dy * dy
        if r2 <= 25:
            px[y][x] = (20, 15, 15, 255)
        if 16 <= r2 <= 22:
            px[y][x] = (120, 80, 40, 255)
        if r2 <= 12:
            px[y][x] = (255, 120, 30, 255)
        if r2 <= 6:
            px[y][x] = (255, 220, 80, 255)
        if r2 <= 1.5:
            px[y][x] = (255, 255, 220, 255)
outline(px, (100, 60, 40, 255))
write_png(OUT / "ship_thruster.png", px)

# Thruster on — hotter
px2 = [row[:] for row in px]
for y in range(5, 11):
    for x in range(5, 11):
        dx, dy = x - 7.5, y - 7.5
        r2 = dx * dx + dy * dy
        if r2 <= 10:
            px2[y][x] = (255, 160, 40, 255)
        if r2 <= 4:
            px2[y][x] = (255, 255, 120, 255)
        if r2 <= 1:
            px2[y][x] = (255, 255, 255, 255)
write_png(OUT / "ship_thruster_on.png", px2)

print("done")
