"""Create-propulsion style 16x16 block textures for VS2 ship blocks (no Pillow)."""
from __future__ import annotations

import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block"

# Classic Create-ish industrial brass / iron palette
BRASS_LIGHT = (232, 196, 118, 255)
BRASS = (201, 161, 74, 255)
BRASS_MID = (168, 128, 52, 255)
BRASS_DARK = (110, 78, 32, 255)
BRASS_OUT = (52, 36, 18, 255)
IRON = (88, 90, 98, 255)
IRON_LIGHT = (120, 122, 132, 255)
IRON_DARK = (48, 50, 58, 255)
IRON_OUT = (22, 22, 28, 255)
RIVET = (200, 190, 160, 255)
VOID = (18, 16, 20, 255)
HEAT = (255, 140, 40, 255)
HEAT_HOT = (255, 220, 90, 255)
HEAT_CORE = (255, 255, 230, 255)
CYAN = (70, 200, 230, 255)
CYAN_DIM = (30, 100, 140, 255)
AMBER = (255, 190, 60, 255)
AMBER_DIM = (160, 100, 30, 255)


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
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
            ">I", zlib.crc32(tag + data) & 0xFFFFFFFF
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.name} ({len(png)} bytes)")


def blank(c=VOID):
    return [[c for _ in range(16)] for _ in range(16)]


def setp(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[y][x] = c


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            setp(px, x, y, c)


def outline(px, c=BRASS_OUT):
    for i in range(16):
        setp(px, i, 0, c)
        setp(px, i, 15, c)
        setp(px, 0, i, c)
        setp(px, 15, i, c)


def noise_fill(px, seed, dark, light, step=4):
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            base = dark if (x + y * 3) % step < step // 2 else light
            j = rng.randint(-8, 8)
            setp(px, x, y, (
                max(0, min(255, base[0] + j)),
                max(0, min(255, base[1] + j)),
                max(0, min(255, base[2] + j)),
                255,
            ))


def brass_plate(seed=1):
    px = blank()
    noise_fill(px, seed, BRASS_DARK, BRASS_MID, step=5)
    # panel seams like Create casing
    for i in range(16):
        setp(px, i, 0, BRASS_OUT)
        setp(px, i, 15, BRASS_OUT)
        setp(px, 0, i, BRASS_OUT)
        setp(px, 15, i, BRASS_OUT)
        setp(px, i, 7, BRASS_DARK)
        setp(px, 7, i, BRASS_DARK)
    # rivets
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13), (2, 7), (13, 7), (7, 2), (7, 13)]:
        setp(px, x, y, RIVET)
        setp(px, x + 1 if x < 8 else x - 1, y, BRASS_LIGHT)
    # highlight edge
    for i in range(1, 15):
        setp(px, i, 1, BRASS_LIGHT)
        setp(px, 1, i, BRASS)
    return px


def iron_plate(seed=2):
    px = blank()
    noise_fill(px, seed, IRON_DARK, IRON, step=4)
    outline(px, IRON_OUT)
    for i in range(16):
        setp(px, i, 5, IRON_DARK)
        setp(px, i, 10, IRON_DARK)
    for x, y in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        setp(px, x, y, RIVET)
    return px


# ---------- Thruster (Create encased-fan / propulsion vibe) ----------
def thruster_side():
    px = brass_plate(11)
    # horizontal vent slots
    for y in (4, 6, 8, 10, 12):
        for x in range(3, 13):
            setp(px, x, y, IRON_DARK if (x + y) % 2 == 0 else VOID)
    # brass bands
    for x in range(16):
        setp(px, x, 2, BRASS_LIGHT)
        setp(px, x, 13, BRASS)
    outline(px, BRASS_OUT)
    return px


def thruster_front(active=False):
    px = brass_plate(3)
    # dark nozzle well
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            r2 = dx * dx + dy * dy
            if r2 <= 28:
                setp(px, x, y, IRON_DARK)
            if 22 <= r2 <= 28:
                setp(px, x, y, BRASS_MID)
            if r2 <= 18:
                setp(px, x, y, VOID)
    # blade spokes (Create fan hint)
    for t in range(-2, 3):
        for r in range(2, 7):
            setp(px, 7 + t, 7 - r, IRON_LIGHT)
            setp(px, 7 + t, 7 + r, IRON_LIGHT)
            setp(px, 7 - r, 7 + t, IRON_LIGHT)
            setp(px, 7 + r, 7 + t, IRON_LIGHT)
    # heat core
    for y in range(5, 11):
        for x in range(5, 11):
            dx, dy = x - 7.5, y - 7.5
            r2 = dx * dx + dy * dy
            if r2 <= 10:
                setp(px, x, y, HEAT if active else (80, 40, 20, 255))
            if r2 <= 5:
                setp(px, x, y, HEAT_HOT if active else (120, 60, 30, 255))
            if r2 <= 1.5 and active:
                setp(px, x, y, HEAT_CORE)
    outline(px, BRASS_OUT)
    return px


def thruster_back():
    px = iron_plate(9)
    rect(px, 4, 4, 11, 11, IRON_DARK)
    rect(px, 6, 6, 9, 9, VOID)
    # bolt ring
    for x, y in [(5, 5), (10, 5), (5, 10), (10, 10)]:
        setp(px, x, y, BRASS)
    outline(px, BRASS_OUT)
    return px


# ---------- Missile tube (silo / bore) ----------
def tube_side():
    px = iron_plate(21)
    # vertical bore lines
    for x in (4, 5, 10, 11):
        for y in range(1, 15):
            setp(px, x, y, IRON_DARK if y % 3 else VOID)
    # brass hoops
    for y in (2, 7, 12):
        for x in range(2, 14):
            setp(px, x, y, BRASS_MID if x % 2 == 0 else BRASS)
    # hazard stripe
    for x in range(1, 15):
        c = AMBER if (x // 2) % 2 == 0 else BRASS_OUT
        setp(px, x, 14, c)
    outline(px, IRON_OUT)
    return px


def tube_top():
    px = brass_plate(33)
    for y in range(2, 14):
        for x in range(2, 14):
            dx, dy = x - 7.5, y - 7.5
            r2 = dx * dx + dy * dy
            if r2 <= 36:
                setp(px, x, y, IRON)
            if 28 <= r2 <= 36:
                setp(px, x, y, BRASS)
            if r2 <= 20:
                setp(px, x, y, IRON_DARK)
            if r2 <= 10:
                setp(px, x, y, VOID)
    # hatch teeth
    for a in range(0, 8):
        ang = a * 0.785
        import math
        x = int(7.5 + 5.5 * math.cos(ang))
        y = int(7.5 + 5.5 * math.sin(ang))
        setp(px, x, y, BRASS_LIGHT)
    outline(px, BRASS_OUT)
    return px


def tube_bottom():
    px = iron_plate(44)
    rect(px, 3, 3, 12, 12, IRON_DARK)
    rect(px, 5, 5, 10, 10, VOID)
    for x in range(6, 10):
        setp(px, x, 7, BRASS)
        setp(px, 7, x, BRASS)
    outline(px, IRON_OUT)
    return px


# ---------- Guidance computer ----------
def guidance_side():
    px = brass_plate(55)
    # side vents
    for y in range(3, 13):
        for x in (3, 4, 11, 12):
            setp(px, x, y, IRON_DARK if y % 2 == 0 else IRON)
    outline(px, BRASS_OUT)
    return px


def guidance_front():
    px = brass_plate(56)
    # screen bezel
    rect(px, 2, 2, 13, 11, IRON_OUT)
    rect(px, 3, 3, 12, 10, (12, 28, 40, 255))
    # scanlines / radar
    for y in range(4, 10):
        for x in range(4, 12):
            c = CYAN if y % 2 == 0 else CYAN_DIM
            setp(px, x, y, c)
    # crosshair
    setp(px, 7, 6, (255, 255, 255, 255))
    setp(px, 8, 6, (255, 255, 255, 255))
    for dx, dy in [(-1, 0), (2, 0), (0, -1), (1, -1), (0, 1), (1, 1)]:
        setp(px, 7 + dx, 6 + dy, (180, 240, 255, 255))
    # status LEDs
    setp(px, 3, 13, (40, 220, 90, 255))
    setp(px, 5, 13, AMBER)
    setp(px, 7, 13, CYAN)
    setp(px, 9, 13, (220, 60, 60, 255))
    outline(px, BRASS_OUT)
    return px


def guidance_top():
    px = brass_plate(57)
    # antenna / dish hint
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 7.5
            if 12 <= dx * dx + dy * dy <= 18:
                setp(px, x, y, CYAN_DIM)
            if dx * dx + dy * dy < 4:
                setp(px, x, y, CYAN)
    setp(px, 7, 7, (255, 255, 255, 255))
    outline(px, BRASS_OUT)
    return px


# ---------- Chunk loader ----------
def chunk_side():
    px = iron_plate(70)
    # amber glow strips
    for y in (4, 7, 10):
        for x in range(2, 14):
            setp(px, x, y, AMBER_DIM if x % 3 else AMBER)
    outline(px, IRON_OUT)
    return px


def chunk_front():
    px = brass_plate(71)
    rect(px, 2, 2, 13, 13, IRON_OUT)
    rect(px, 3, 3, 12, 12, (30, 24, 16, 255))
    # radar rings
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 7.5
            r2 = dx * dx + dy * dy
            if 9 <= r2 <= 14:
                setp(px, x, y, AMBER)
            if r2 < 4:
                setp(px, x, y, AMBER)
            if r2 < 1.2:
                setp(px, x, y, HEAT_CORE)
    for p in [(7, 3), (7, 12), (3, 7), (12, 7)]:
        setp(px, p[0], p[1], AMBER)
    outline(px, BRASS_OUT)
    return px


def chunk_top():
    px = iron_plate(72)
    # compass rose
    for i in range(3, 13):
        setp(px, i, 7, AMBER_DIM)
        setp(px, 7, i, AMBER_DIM)
    setp(px, 7, 7, AMBER)
    outline(px, IRON_OUT)
    return px


def main():
    # Thruster (legacy names still used by simple models + multi-face)
    write_png(OUT / "ship_thruster_side.png", thruster_side())
    write_png(OUT / "ship_thruster_front.png", thruster_front(False))
    write_png(OUT / "ship_thruster_front_on.png", thruster_front(True))
    write_png(OUT / "ship_thruster_back.png", thruster_back())
    # keep single-texture aliases for item models / fallbacks
    write_png(OUT / "ship_thruster.png", thruster_front(False))
    write_png(OUT / "ship_thruster_on.png", thruster_front(True))

    write_png(OUT / "missile_tube_side.png", tube_side())
    write_png(OUT / "missile_tube_top.png", tube_top())
    write_png(OUT / "missile_tube_bottom.png", tube_bottom())
    write_png(OUT / "missile_tube.png", tube_top())

    write_png(OUT / "ship_vls_guidance_side.png", guidance_side())
    write_png(OUT / "ship_vls_guidance_front.png", guidance_front())
    write_png(OUT / "ship_vls_guidance_top.png", guidance_top())
    write_png(OUT / "ship_vls_guidance.png", guidance_front())

    write_png(OUT / "missile_chunk_loader_side.png", chunk_side())
    write_png(OUT / "missile_chunk_loader_front.png", chunk_front())
    write_png(OUT / "missile_chunk_loader_top.png", chunk_top())
    write_png(OUT / "missile_chunk_loader.png", chunk_front())

    print("done — Create propulsion style textures")


if __name__ == "__main__":
    main()
