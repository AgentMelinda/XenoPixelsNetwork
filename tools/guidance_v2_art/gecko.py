"""Fork GeckoLib computer atlas. Same region tuples as flight_controller_layout; new paint."""
from __future__ import annotations

from .draw import Pixel, bevel, blank, grain, mix, ring, scanlines, setp, vgrad
from .palette import PALETTE

# Copied from tools/flight_controller_layout.py — do not edit that file for fork art.
SIZE = 128
REGIONS: dict[str, tuple[int, int, int, int]] = {
    "housing": (0, 0, 48, 32),
    "deck": (48, 0, 48, 32),
    "console": (0, 32, 48, 32),
    "screen": (48, 32, 48, 32),
    "lever": (96, 0, 16, 32),
    "btn_mode": (96, 32, 16, 16),
    "btn_engage": (112, 32, 16, 16),
    "btn_map": (96, 48, 16, 16),
    "vent": (0, 64, 48, 16),
    "conduit": (48, 64, 48, 16),
    "trim": (96, 64, 32, 16),
}

GLOW: dict[str, tuple[int, int, int]] = {
    "screen": (0, 236, 255),
    "btn_mode": (255, 176, 24),
    "btn_engage": (88, 255, 108),
    "btn_map": (255, 40, 196),
    "trim": (0, 236, 255),
}


def _fill(px: list[list[Pixel]], region: tuple[int, int, int, int], colour: tuple[int, int, int]) -> None:
    x0, y0, w, h = region
    for y in range(h):
        for x in range(w):
            setp(px, x0 + x, y0 + y, colour)


def _vgrad(px: list[list[Pixel]], region: tuple[int, int, int, int],
           top: tuple[int, int, int], bottom: tuple[int, int, int]) -> None:
    x0, y0, w, h = region
    vgrad(px, x0, y0, x0 + w - 1, y0 + h - 1, top, bottom)


def _bevel(px: list[list[Pixel]], region: tuple[int, int, int, int],
           light: tuple[int, int, int], dark: tuple[int, int, int]) -> None:
    x0, y0, w, h = region
    bevel(px, x0, y0, x0 + w - 1, y0 + h - 1, light, dark, 1)


def _scan(px: list[list[Pixel]], region: tuple[int, int, int, int],
          colour: tuple[int, int, int], step: int = 3) -> None:
    x0, y0, w, h = region
    scanlines(px, x0, y0, x0 + w - 1, y0 + h - 1, colour, step, 0.32)


def _reticle(px: list[list[Pixel]], region: tuple[int, int, int, int]) -> None:
    x0, y0, w, h = region
    cx, cy = x0 + w // 2, y0 + h // 2
    ring(px, cx, cy, min(w, h) // 3, PALETTE["magenta"], 1)
    for x in range(x0 + 4, x0 + w - 4):
        setp(px, x, cy, PALETTE["cyan"])
    for y in range(y0 + 4, y0 + h - 4):
        setp(px, cx, y, PALETTE["cyan"])


def paint_controller() -> list[list[Pixel]]:
    px = blank(SIZE)
    p = PALETTE

    _vgrad(px, REGIONS["housing"], p["void_mid"], p["void_deep"])
    grain(px, * _region_box(REGIONS["housing"]), 11, 6)
    _bevel(px, REGIONS["housing"], p["magenta"], p["void_deep"])

    _vgrad(px, REGIONS["deck"], p["void_edge"], p["void_hull"])
    grain(px, *_region_box(REGIONS["deck"]), 22, 5)
    x0, y0, w, h = REGIONS["deck"]
    for x in range(8, w - 1, 10):
        for y in range(2, h - 2):
            setp(px, x0 + x, y0 + y, p["cyan_deep"])
    _bevel(px, REGIONS["deck"], p["cyan"], p["magenta_deep"])

    _vgrad(px, REGIONS["console"], p["void_mid"], p["void_deep"])
    _bevel(px, REGIONS["console"], p["magenta"], p["cyan_deep"])

    _vgrad(px, REGIONS["screen"], p["holo"], p["holo_dark"])
    _scan(px, REGIONS["screen"], p["cyan_deep"])
    _reticle(px, REGIONS["screen"])
    _bevel(px, REGIONS["screen"], p["cyan"], p["magenta_deep"])

    _vgrad(px, REGIONS["lever"], p["magenta"], p["magenta_deep"])
    _bevel(px, REGIONS["lever"], p["magenta"], p["void_deep"])

    for name, light, dark in (
        ("btn_mode", p["amber_hot"], p["amber"]),
        ("btn_engage", p["lime"], p["lime_deep"]),
        ("btn_map", p["cyan"], p["cyan_deep"]),
    ):
        _vgrad(px, REGIONS[name], light, dark)
        _bevel(px, REGIONS[name], light, dark)

    _fill(px, REGIONS["vent"], p["void_deep"])
    _bevel(px, REGIONS["vent"], p["cyan"], p["void_deep"])

    _vgrad(px, REGIONS["conduit"], p["cyan_mid"], p["cyan_deep"])
    x0, y0, w, h = REGIONS["conduit"]
    for x in range(4, w - 1, 6):
        for y in range(2, h - 2):
            setp(px, x0 + x, y0 + y, p["void_deep"])

    _vgrad(px, REGIONS["trim"], p["cyan"], p["magenta"])
    _bevel(px, REGIONS["trim"], p["cyan"], p["magenta_deep"])
    return px


def paint_glowmask() -> list[list[Pixel]]:
    px = blank(SIZE)
    for name, colour in GLOW.items():
        x0, y0, w, h = REGIONS[name]
        for y in range(h):
            t = y / max(1, h - 1)
            c = mix(colour, tuple(int(v * 0.5) for v in colour), t)
            for x in range(w):
                setp(px, x0 + x, y0 + y, (*c, 255))
    _scan(px, REGIONS["screen"], (8, 24, 36), 3)
    return px


def _region_box(region: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    x0, y0, w, h = region
    return x0, y0, x0 + w - 1, y0 + h - 1
