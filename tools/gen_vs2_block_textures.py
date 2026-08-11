"""Generate every Sable/VS2 block texture on the shared XenoPixels palette.

Writes the full face set for the four ship blocks:

* ``ship_vls_guidance`` — the ballistic computer (front / side / top)
* ``ship_thruster``     — (front / front_on / back / side)
* ``missile_tube``      — (top / side / bottom)
* ``missile_chunk_loader`` — (front / side / top)

**Why this exists.** The ship blocks were previously drawn in a brass-and-gold Create-style
palette, while the flight controller rig (``gen_flight_controller_textures.py``) and the copycat
glowstone (``gen_copycat_textures.py``) use a gunmetal hull with cyan avionics. Two families in
one mod read as two mods. Everything here shares
``flight_controller_layout.PALETTE`` so the whole ship set matches the controller a player
installs it next to.

**Supersedes** ``gen_ballistic_textures.py`` and ``gen_create_propulsion_textures.py``, both of
which write these same filenames. Running either of those after this one silently reverts the
art — they are kept only for reference.

Colour is used to carry function rather than decoration:

* cyan   — avionics, data, anything the controller talks to
* amber  — heat and thrust, on the thruster only; a cyan exhaust reads wrong
* green  — the chunk loader's activity node, matching its "keeping this loaded" role
* red    — hazard banding on the missile tube

Pure stdlib, no Pillow — ``write_png`` is the implementation shared by the other generators.

Run: ``python tools/gen_vs2_block_textures.py``
"""
from __future__ import annotations

import random
import struct
import zlib
from pathlib import Path

from flight_controller_layout import PALETTE

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/block"

SIZE = 16

# Functional accents, kept out of the shared palette because they mean something here.
HAZARD = (176, 58, 46)
HEAT_DARK = (92, 38, 12)
HEAT_MID = (206, 104, 26)
HEAT_HOT = (255, 196, 96)
HEAT_CORE = (255, 246, 214)


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
    print(f"wrote {path.name}")


def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def setp(px, x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[y][x] = (c[0], c[1], c[2], 255) if len(c) == 3 else c


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            setp(px, x, y, c)


def plate(seed, top=None, bottom=None, grain=5):
    """Base hull panel: vertical gradient plus fine machined grain.

    Every face starts here, which is most of what makes the set look like one material.
    """
    top = top or PALETTE["housing_mid"]
    bottom = bottom or PALETTE["housing_dark"]
    rng = random.Random(seed)
    px = [[(0, 0, 0, 255)] * SIZE for _ in range(SIZE)]
    px = [[(0, 0, 0, 255) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        base = mix(top, bottom, y / (SIZE - 1))
        for x in range(SIZE):
            n = rng.randint(-grain, grain)
            px[y][x] = (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n), 255)
    return px


def bevel(px):
    """1px lit top/left, shadowed bottom/right. Keeps blocks separable when tiled in a hull."""
    for i in range(SIZE):
        setp(px, i, 0, PALETTE["housing_light"])
        setp(px, 0, i, mix(PALETTE["housing_light"], PALETTE["housing_mid"], 0.4))
        setp(px, i, SIZE - 1, PALETTE["vent_dark"])
        setp(px, SIZE - 1, i, mix(PALETTE["housing_dark"], PALETTE["vent_dark"], 0.5))


def rivets(px, inset=2, c=None):
    c = c or PALETTE["housing_light"]
    for (x, y) in ((inset, inset), (SIZE - 1 - inset, inset),
                   (inset, SIZE - 1 - inset), (SIZE - 1 - inset, SIZE - 1 - inset)):
        setp(px, x, y, c)
        setp(px, x, y + 1, mix(PALETTE["housing_dark"], PALETTE["vent_dark"], 0.4))


def louvres(px, x0, y0, x1, y1, step=2):
    """Horizontal cooling slots: a dark slot with a lit lip above it."""
    for y in range(y0, y1 + 1, step):
        rect(px, x0, y, x1, y, PALETTE["vent_dark"])
        rect(px, x0, y - 1, x1, y - 1, mix(PALETTE["housing_light"], PALETTE["housing_mid"], 0.5))


def ribs(px, y0, y1, xs, c=None):
    """Vertical reinforcement ribs."""
    c = c or PALETTE["housing_light"]
    for x in xs:
        for y in range(y0, y1 + 1):
            setp(px, x, y, mix(c, PALETTE["housing_mid"], 0.35))
            setp(px, x + 1, y, PALETTE["vent_dark"])


def disc(px, cx, cy, radius, inner, outer, ring=None):
    """Filled circle with an optional 1px ring, used for nozzles and hatches."""
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d > radius:
                continue
            if ring is not None and d > radius - 1.2:
                setp(px, x, y, ring)
            else:
                setp(px, x, y, mix(inner, outer, min(1.0, d / max(0.001, radius))))


def screen(px, x0, y0, x1, y1):
    """Recessed cyan display with scanlines and a dark bezel."""
    rect(px, x0 - 1, y0 - 1, x1 + 1, y1 + 1, PALETTE["vent_dark"])
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        base = mix(PALETTE["screen_light"], PALETTE["screen_mid"], abs(t - 0.3) * 1.5)
        for x in range(x0, x1 + 1):
            c = base if (y - y0) % 2 else mix(base, PALETTE["screen_dark"], 0.28)
            setp(px, x, y, c)


def leds(px, y, xs, colours):
    for x, c in zip(xs, colours):
        setp(px, x, y, c)
        setp(px, x, y + 1, mix(c, PALETTE["vent_dark"], 0.55))


# --- ballistic computer (ship_vls_guidance) ------------------------------------------------

def guidance_front():
    """Screen showing a targeting reticle, with a status LED strip beneath it."""
    px = plate(101, PALETTE["console_mid"], PALETTE["console_dark"])
    bevel(px)
    screen(px, 3, 3, 12, 10)
    # Reticle: the block's whole job is aiming, so the front face says so.
    for x in range(4, 12):
        setp(px, x, 7, mix(PALETTE["screen_light"], (255, 255, 255), 0.5))
    for y in range(4, 10):
        setp(px, 8, y, mix(PALETTE["screen_light"], (255, 255, 255), 0.5))
    for (x, y) in ((5, 5), (11, 5), (5, 9), (11, 9)):
        setp(px, x, y, PALETTE["screen_dark"])
    leds(px, 12, (3, 5, 7), (PALETTE["green_light"], PALETTE["amber_light"], PALETTE["cyan_light"]))
    rect(px, 9, 12, 12, 12, PALETTE["vent_dark"])
    return px


def guidance_side():
    """Vent stack plus the cyan avionics conduit that runs the length of the ship set."""
    px = plate(102)
    bevel(px)
    louvres(px, 3, 4, 8, 11)
    rect(px, 11, 2, 12, 13, PALETTE["cyan_dark"])
    rect(px, 11, 3, 11, 12, PALETTE["cyan_mid"])
    setp(px, 11, 5, PALETTE["cyan_light"])
    setp(px, 11, 9, PALETTE["cyan_light"])
    rivets(px)
    return px


def guidance_top():
    """Antenna node — the sensor half of the computer."""
    px = plate(103, PALETTE["deck_mid"], PALETTE["deck_dark"])
    bevel(px)
    disc(px, 7.5, 7.5, 4.2, PALETTE["cyan_light"], PALETTE["cyan_dark"], PALETTE["housing_light"])
    disc(px, 7.5, 7.5, 1.6, (255, 255, 255), PALETTE["cyan_light"])
    for x in range(2, 14):
        setp(px, x, 2, mix(PALETTE["housing_light"], PALETTE["housing_mid"], 0.4))
    rivets(px, 2)
    return px


# --- thruster -------------------------------------------------------------------------------

def thruster_front(active):
    """Nozzle throat. Amber is deliberate: exhaust must not read as avionics."""
    px = plate(111, PALETTE["housing_dark"], PALETTE["vent_dark"])
    bevel(px)
    disc(px, 7.5, 7.5, 7.0, PALETTE["housing_mid"], PALETTE["housing_dark"],
         PALETTE["housing_light"])
    if active:
        disc(px, 7.5, 7.5, 5.2, HEAT_CORE, HEAT_MID, HEAT_HOT)
        disc(px, 7.5, 7.5, 2.4, (255, 255, 255), HEAT_CORE)
    else:
        disc(px, 7.5, 7.5, 5.2, HEAT_DARK, PALETTE["vent_dark"], PALETTE["housing_dark"])
        disc(px, 7.5, 7.5, 2.2, PALETTE["vent_dark"], (0, 0, 0))
    # Nozzle vanes, so the throat has depth rather than reading as a flat hole.
    for angle in range(0, 360, 45):
        import math
        dx, dy = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        for r in (5.6, 6.4):
            setp(px, round(7.5 + dx * r), round(7.5 + dy * r), PALETTE["housing_light"])
    return px


def thruster_back():
    """Mount plate: bolt ring and the cyan feed the controller drives it through."""
    px = plate(112)
    bevel(px)
    rect(px, 4, 4, 11, 11, mix(PALETTE["housing_dark"], PALETTE["housing_mid"], 0.5))
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 3), (8, 12)):
        setp(px, x, y, PALETTE["housing_light"])
    rect(px, 6, 6, 9, 9, PALETTE["cyan_dark"])
    rect(px, 7, 7, 8, 8, PALETTE["cyan_mid"])
    rivets(px)
    return px


def thruster_side():
    """Cooling fins with a heat band low on the casing, near the throat."""
    px = plate(113)
    bevel(px)
    louvres(px, 2, 3, 13, 9)
    rect(px, 2, 11, 13, 12, HEAT_DARK)
    rect(px, 2, 11, 13, 11, mix(HEAT_MID, HEAT_DARK, 0.4))
    for x in range(3, 13, 3):
        setp(px, x, 11, HEAT_HOT)
    return px


# --- missile tube ---------------------------------------------------------------------------

def tube_top():
    """Open silo hatch: dark throat, cyan launch ring, hazard corners."""
    px = plate(121, PALETTE["deck_mid"], PALETTE["deck_dark"])
    bevel(px)
    disc(px, 7.5, 7.5, 6.4, PALETTE["housing_dark"], PALETTE["vent_dark"],
         PALETTE["cyan_mid"])
    disc(px, 7.5, 7.5, 4.4, PALETTE["vent_dark"], (0, 0, 0))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        setp(px, x, y, HAZARD)
    setp(px, 7, 1, PALETTE["cyan_light"])
    setp(px, 8, 14, PALETTE["cyan_light"])
    return px


def tube_side():
    """Reinforcement ribs and a hazard band — this is the block that holds a warhead."""
    px = plate(122)
    bevel(px)
    ribs(px, 2, 13, (3, 7, 11))
    rect(px, 1, 6, 14, 8, mix(HAZARD, PALETTE["housing_dark"], 0.35))
    # Chevrons, drawn as a diagonal so the band reads as hazard tape at a glance.
    for i in range(1, 15):
        if (i // 2) % 2 == 0:
            setp(px, i, 7, mix(HAZARD, (255, 220, 140), 0.55))
    return px


def tube_bottom():
    px = plate(123, PALETTE["housing_dark"], PALETTE["vent_dark"])
    bevel(px)
    rect(px, 3, 3, 12, 12, mix(PALETTE["housing_dark"], PALETTE["housing_mid"], 0.4))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        setp(px, x, y, PALETTE["housing_light"])
    rect(px, 6, 6, 9, 9, PALETTE["vent_dark"])
    return px


# --- chunk loader ---------------------------------------------------------------------------

def chunk_front():
    """Green activity node in a chunk-grid frame: 'this area stays loaded'."""
    px = plate(131, PALETTE["console_mid"], PALETTE["console_dark"])
    bevel(px)
    rect(px, 2, 2, 13, 13, PALETTE["vent_dark"])
    rect(px, 3, 3, 12, 12, mix(PALETTE["housing_dark"], PALETTE["housing_mid"], 0.35))
    # Chunk grid.
    for i in range(3, 13):
        setp(px, i, 8, mix(PALETTE["green_dark"], PALETTE["housing_mid"], 0.5))
        setp(px, 8, i, mix(PALETTE["green_dark"], PALETTE["housing_mid"], 0.5))
    disc(px, 7.5, 7.5, 3.0, PALETTE["green_light"], PALETTE["green_dark"], PALETTE["green_mid"])
    disc(px, 7.5, 7.5, 1.2, (235, 255, 240), PALETTE["green_light"])
    return px


def chunk_side():
    px = plate(132)
    bevel(px)
    louvres(px, 3, 4, 12, 11)
    rect(px, 1, 13, 14, 13, PALETTE["green_dark"])
    for x in range(2, 14, 4):
        setp(px, x, 13, PALETTE["green_mid"])
    rivets(px)
    return px


def chunk_top():
    px = plate(133, PALETTE["deck_mid"], PALETTE["deck_dark"])
    bevel(px)
    for i in range(3, 13):
        setp(px, i, 7, PALETTE["green_dark"])
        setp(px, 7, i, PALETTE["green_dark"])
    disc(px, 7.5, 7.5, 2.4, PALETTE["green_light"], PALETTE["green_dark"], PALETTE["housing_light"])
    rivets(px, 2)
    return px


def main():
    faces = {
        "ship_vls_guidance_front": guidance_front(),
        "ship_vls_guidance_side": guidance_side(),
        "ship_vls_guidance_top": guidance_top(),
        "ship_thruster_front": thruster_front(False),
        "ship_thruster_front_on": thruster_front(True),
        "ship_thruster_back": thruster_back(),
        "ship_thruster_side": thruster_side(),
        "missile_tube_top": tube_top(),
        "missile_tube_side": tube_side(),
        "missile_tube_bottom": tube_bottom(),
        "missile_chunk_loader_front": chunk_front(),
        "missile_chunk_loader_side": chunk_side(),
        "missile_chunk_loader_top": chunk_top(),
    }
    for name, px in faces.items():
        write_png(OUT / f"{name}.png", px)

    # Single-texture fallbacks. These filenames are still referenced by older models and by
    # the item forms, so they have to stay in step with the face set above.
    for base, source in (("ship_vls_guidance", "ship_vls_guidance_front"),
                         ("ship_thruster", "ship_thruster_front"),
                         ("ship_thruster_on", "ship_thruster_front_on"),
                         ("missile_tube", "missile_tube_top"),
                         ("missile_chunk_loader", "missile_chunk_loader_front")):
        write_png(OUT / f"{base}.png", faces[source])

    print(f"{len(faces) + 5} textures on the shared palette")


if __name__ == "__main__":
    main()
