"""Generate the copycat glowstone's unbound (no material applied) texture.

Writes ``assets/xenopixelsmod/textures/block/copycat_glowstone.png``.

This texture is only ever seen in one state: a copycat that has **no** material applied yet, or
one whose material a Create wrench just unbound. Once a block item is applied the block renders
the copied material instead (see ``client/model/CopycatGlowstoneModel``), so this art's whole
job is to read as *empty and waiting for input* rather than as a finished decorative block.

That is why it deliberately does not imitate vanilla glowstone. A copycat that looks like
glowstone is indistinguishable from actual glowstone in a wall, which matters because applying a
material is one-shot — a player needs to be able to see at a glance which blocks are still
unbound before committing an item to them.

Palette is shared with the flight controller rig (``flight_controller_layout.PALETTE``) so the
mod's tech blocks read as one set: gunmetal plate, cyan emissive core.

Pure stdlib — ``write_png`` is the implementation already proven in ``gen_hud_textures.py`` and
``gen_ballistic_textures.py``.

Run: ``python tools/gen_copycat_textures.py``
"""
from __future__ import annotations

import random
import struct
import zlib
from pathlib import Path

from flight_controller_layout import PALETTE

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block/copycat_glowstone.png"

SIZE = 16


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


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    p = PALETTE
    rng = random.Random(0xC0DEC)
    px = [[(0, 0, 0, 255) for _ in range(SIZE)] for _ in range(SIZE)]

    for y in range(SIZE):
        for x in range(SIZE):
            # Distance from the nearest edge decides plate vs. bevel vs. emissive core.
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)

            if edge == 0:
                # Outer bevel: lit on the top/left, shadowed on the bottom/right, so adjacent
                # copycats in a wall still read as separate blocks.
                lit = y == 0 or x == 0
                c = p["housing_light"] if lit else p["housing_dark"]
            elif edge == 1:
                c = p["housing_mid"]
            elif edge == 2:
                # Recessed gutter around the core.
                c = mix(p["housing_dark"], p["vent_dark"], 0.5)
            else:
                # Emissive core: brightest just above centre so it reads as a lit panel rather
                # than a flat fill, matching the bar shading in the HUD atlas.
                t = (y - 3) / (SIZE - 7)
                c = mix(p["screen_light"], p["screen_mid"], abs(t - 0.35) * 1.5)
                c = mix(c, p["screen_dark"], max(0.0, t - 0.75) * 2.0)
                # Faint scanlines: cheap texture that survives being viewed at a distance.
                if (y % 3) == 0:
                    c = mix(c, p["screen_dark"], 0.22)

            # Machined grain. Kept subtle; a 16x16 face goes to mush with heavy noise.
            n = rng.randint(-6, 6)
            px[y][x] = (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), 255)

    # Corner screws, so the plate reads as a panel that was fastened on rather than a sticker.
    for (sx, sy) in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        px[sy][sx] = (*p["housing_light"], 255)

    # Cyan crosshair in the core: the "no material bound" marker. Dim enough not to fight the
    # copied material's silhouette in the item form.
    mid = SIZE // 2
    for i in range(5, SIZE - 5):
        px[mid][i] = (*mix(p["screen_light"], (255, 255, 255), 0.45), 255)
        px[i][mid] = (*mix(p["screen_light"], (255, 255, 255), 0.30), 255)

    write_png(OUT, px)


if __name__ == "__main__":
    main()
