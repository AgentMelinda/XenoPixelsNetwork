"""Shared atlas layout and palette for the flight controller rig.

Imported by both ``gen_flight_controller_model.py`` (which writes UVs into the geometry)
and ``gen_flight_controller_textures.py`` (which paints those same regions). Keeping one
copy is the whole point: a duplicated table drifts the moment either script is edited, and
the symptom is a model whose faces sample the wrong part of the atlas.
"""
from __future__ import annotations

# Atlas size. Drop to 64 if 128 reads too crisp beside the pack's 16x16 blocks.
SIZE = 128

# Named atlas regions as (x, y, w, h) in texture pixels.
REGIONS: dict[str, tuple[int, int, int, int]] = {
    "housing":   (0, 0, 48, 32),
    "deck":      (48, 0, 48, 32),
    "console":   (0, 32, 48, 32),
    "screen":    (48, 32, 48, 32),
    "lever":     (96, 0, 16, 32),
    "btn_mode":  (96, 32, 16, 16),
    "btn_engage": (112, 32, 16, 16),
    "btn_map":   (96, 48, 16, 16),
    "vent":      (0, 64, 48, 16),
    "conduit":   (48, 64, 48, 16),
    "trim":      (96, 64, 32, 16),
}

# Which regions are emissive, and at what colour, on the glowmask.
# Anything absent here must be fully transparent or the whole block glows.
GLOW: dict[str, tuple[int, int, int]] = {
    "screen": (120, 236, 255),
    "btn_mode": (255, 190, 90),
    "btn_engage": (130, 255, 165),
    "btn_map": (120, 220, 255),
    "trim": (90, 200, 235),
}

# Palette: cool gunmetal hull, slate deck, cyan avionics.
PALETTE = {
    "housing_dark": (28, 32, 40),
    "housing_mid": (46, 52, 62),
    "housing_light": (68, 76, 88),
    "deck_dark": (52, 58, 68),
    "deck_mid": (74, 82, 94),
    "deck_light": (98, 108, 122),
    "console_dark": (38, 44, 54),
    "console_mid": (58, 66, 78),
    "console_light": (84, 94, 108),
    "screen_dark": (10, 40, 52),
    "screen_mid": (24, 96, 122),
    "screen_light": (86, 200, 226),
    "lever_dark": (96, 30, 28),
    "lever_mid": (150, 52, 46),
    "lever_light": (196, 88, 78),
    "amber_dark": (120, 78, 20),
    "amber_mid": (186, 130, 38),
    "amber_light": (238, 182, 78),
    "green_dark": (26, 92, 46),
    "green_mid": (52, 148, 78),
    "green_light": (104, 208, 132),
    "cyan_dark": (22, 86, 106),
    "cyan_mid": (46, 138, 164),
    "cyan_light": (104, 202, 230),
    "vent_dark": (18, 21, 26),
    "trim_mid": (46, 138, 164),
}


def uv(region: str, w: float, h: float, ox: float = 0.0, oy: float = 0.0) -> dict:
    """A per-face UV entry sampling ``region``.

    Bedrock per-face UV is in texture pixels. ``w``/``h`` are the face's footprint and
    ``ox``/``oy`` nudge the sample origin, which lets several faces of one cube read from
    different parts of the same region instead of all sampling the identical corner.
    """
    x, y, rw, rh = REGIONS[region]
    return {
        "uv": [x + min(ox, max(0, rw - 1)), y + min(oy, max(0, rh - 1))],
        "uv_size": [min(w, rw), min(h, rh)],
    }


def box_uv(region: str, size: tuple[float, float, float]) -> dict:
    """Per-face UV map for a cube of ``size`` sampling one region on every face."""
    sx, sy, sz = size
    return {
        "north": uv(region, sx, sy),
        "south": uv(region, sx, sy, 1, 1),
        "east": uv(region, sz, sy, 2, 0),
        "west": uv(region, sz, sy, 3, 1),
        "up": uv(region, sx, sz, 0, 2),
        "down": uv(region, sx, sz, 1, 3),
    }
