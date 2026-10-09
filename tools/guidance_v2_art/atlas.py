"""V2 computer / HUD atlas layout. Mirrored in GuidanceV2ArtLayout.java."""
from __future__ import annotations

from .draw import clamp, mix
from .palette import PALETTE

ATLAS = 1024
PANEL_CORNER = 16

# name -> (u, v, w, h)  — keep in lockstep with GuidanceV2ArtLayout.java
REGIONS: dict[str, tuple[int, int, int, int]] = {
    "panel": (0, 0, 64, 64),
    "tab": (64, 0, 96, 20),
    "tab_hot": (64, 20, 96, 20),
    "bar_empty": (0, 64, 300, 14),
    "bar_full": (0, 78, 300, 14),
    "bar_flap": (0, 92, 300, 14),
    "mode_chip": (320, 0, 48, 20),
    "warn": (320, 24, 80, 16),
    "edge": (400, 0, 16, 16),
}


def _set(px, x, y, c):
    if 0 <= y < ATLAS and 0 <= x < ATLAS:
        if len(c) == 3:
            px[y][x] = (c[0], c[1], c[2], 255)
        else:
            px[y][x] = c


def _fill(px, u, v, w, h, c):
    for y in range(v, v + h):
        for x in range(u, u + w):
            _set(px, x, y, c)


def _bevel(px, u, v, w, h, light, dark):
    for x in range(u, u + w):
        _set(px, x, v, light)
        _set(px, x, v + h - 1, dark)
    for y in range(v, v + h):
        _set(px, u, y, light)
        _set(px, u + w - 1, y, dark)


def _scan(px, u, v, w, h, colour, step=3):
    for y in range(v, v + h, step):
        for x in range(u, u + w):
            r, g, b, a = px[y][x]
            _set(px, x, y, (*mix((r, g, b), colour, 0.22), a))


def paint_atlas() -> list[list[tuple[int, int, int, int]]]:
    px = [[(0, 0, 0, 0) for _ in range(ATLAS)] for _ in range(ATLAS)]
    p = PALETTE

    u, v, w, h = REGIONS["panel"]
    for y in range(h):
        t = y / max(1, h - 1)
        for x in range(w):
            tx = x / max(1, w - 1)
            base = mix(p["void_mid"], p["void_deep"], t)
            sheen = mix(p["cyan_deep"], p["magenta_deep"], tx)
            _set(px, u + x, v + y, (*mix(base, sheen, 0.35), 235))
    _scan(px, u, v, w, h, p["cyan_deep"], 4)
    _bevel(px, u, v, w, h, p["cyan"], p["magenta_deep"])
    _bevel(px, u + 2, v + 2, w - 4, h - 4, p["magenta"], p["cyan_deep"])

    for name, accent in (("tab", p["cyan"]), ("tab_hot", p["magenta"])):
        u, v, w, h = REGIONS[name]
        for y in range(h):
            t = y / max(1, h - 1)
            base = mix(p["void_hull"], accent, 0.22 + t * 0.35)
            for x in range(w):
                _set(px, u + x, v + y, (*base, 245))
        _bevel(px, u, v, w, h, accent, p["void_deep"])

    for name, fill in (("bar_empty", p["void_deep"]),
                       ("bar_full", p["cyan"]),
                       ("bar_flap", p["lime"])):
        u, v, w, h = REGIONS[name]
        for y in range(h):
            t = y / max(1, h - 1)
            c = mix(fill, p["void_hull"], t * 0.4)
            for x in range(w):
                gloss = 28 if y < 3 else 0
                _set(px, u + x, v + y, (clamp(c[0] + gloss), clamp(c[1] + gloss), clamp(c[2] + gloss), 255))
        _bevel(px, u, v, w, h, p["cyan"] if name != "bar_flap" else p["lime"], p["void_deep"])

    u, v, w, h = REGIONS["mode_chip"]
    _fill(px, u, v, w, h, (*p["void_hull"], 240))
    _bevel(px, u, v, w, h, p["amber"], p["void_deep"])

    u, v, w, h = REGIONS["warn"]
    _fill(px, u, v, w, h, (*p["magenta_deep"], 235))
    _bevel(px, u, v, w, h, p["warn"], p["void_deep"])

    u, v, w, h = REGIONS["edge"]
    _fill(px, u, v, w, h, (*p["cyan"], 255))
    _bevel(px, u, v, w, h, (255, 255, 255), p["cyan_deep"])
    return px
