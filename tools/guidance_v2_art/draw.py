"""Deterministic drawing primitives for 128× fork faces. Stdlib only — no Pillow."""
from __future__ import annotations

from .palette import PALETTE

Pixel = tuple[int, int, int, int]


def clamp(v: int) -> int:
    return max(0, min(255, int(v)))


def mix(a: tuple[int, ...], b: tuple[int, ...], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    return (
        clamp(a[0] + (b[0] - a[0]) * t),
        clamp(a[1] + (b[1] - a[1]) * t),
        clamp(a[2] + (b[2] - a[2]) * t),
    )


def hash2(x: int, y: int, seed: int = 0) -> int:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177
    return n & 0xFFFFFFFF


def blank(size: int = 128, color: Pixel = (0, 0, 0, 0)) -> list[list[Pixel]]:
    return [[color for _ in range(size)] for _ in range(size)]


def setp(px: list[list[Pixel]], x: int, y: int, c: tuple[int, ...]) -> None:
    h = len(px)
    w = len(px[0])
    if 0 <= y < h and 0 <= x < w:
        px[y][x] = (c[0], c[1], c[2], 255) if len(c) == 3 else c  # type: ignore[assignment]


def rect(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int, c: tuple[int, ...]) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            setp(px, x, y, c)


def vgrad(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
          top: tuple[int, int, int], bottom: tuple[int, int, int]) -> None:
    h = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        c = mix(top, bottom, (y - y0) / h)
        for x in range(x0, x1 + 1):
            setp(px, x, y, c)


def hgrad(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
          left: tuple[int, int, int], right: tuple[int, int, int]) -> None:
    w = max(1, x1 - x0)
    for x in range(x0, x1 + 1):
        c = mix(left, right, (x - x0) / w)
        for y in range(y0, y1 + 1):
            setp(px, x, y, c)


def grain(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int, seed: int, amount: int = 7) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r, g, b, a = px[y][x]
            if a < 8:
                continue
            n = ((hash2(x, y, seed) >> 8) % (amount * 2 + 1)) - amount
            setp(px, x, y, (clamp(r + n), clamp(g + n), clamp(b + n), a))


def bevel(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
          light: tuple[int, int, int], dark: tuple[int, int, int], width: int = 1) -> None:
    for i in range(width):
        for x in range(x0 + i, x1 + 1 - i):
            setp(px, x, y0 + i, light)
            setp(px, x, y1 - i, dark)
        for y in range(y0 + i, y1 + 1 - i):
            setp(px, x0 + i, y, mix(light, dark, 0.25))
            setp(px, x1 - i, y, dark)


def scanlines(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
              colour: tuple[int, int, int], step: int = 4, strength: float = 0.28) -> None:
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1):
            r, g, b, a = px[y][x]
            setp(px, x, y, (*mix((r, g, b), colour, strength), a))


def ring(px: list[list[Pixel]], cx: int, cy: int, radius: int, colour: tuple[int, int, int],
         thickness: int = 2) -> None:
    r0 = max(0, radius - thickness)
    r1 = radius
    r0sq, r1sq = r0 * r0, r1 * r1
    for y in range(cy - r1 - 1, cy + r1 + 2):
        for x in range(cx - r1 - 1, cx + r1 + 2):
            d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if r0sq <= d <= r1sq:
                setp(px, x, y, colour)


def disc(px: list[list[Pixel]], cx: int, cy: int, radius: int, colour: tuple[int, int, int]) -> None:
    rsq = radius * radius
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            if (x - cx) * (x - cx) + (y - cy) * (y - cy) <= rsq:
                setp(px, x, y, colour)


def radial(px: list[list[Pixel]], cx: int, cy: int, radius: int,
           inner: tuple[int, int, int], outer: tuple[int, int, int]) -> None:
    r = max(1, radius)
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            d = ((x - cx) * (x - cx) + (y - cy) * (y - cy)) ** 0.5
            if d <= r:
                setp(px, x, y, mix(inner, outer, d / r))


def hline(px: list[list[Pixel]], x0: int, x1: int, y: int, c: tuple[int, int, int],
          thickness: int = 1) -> None:
    for t in range(thickness):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            setp(px, x, y + t, c)


def vline(px: list[list[Pixel]], x: int, y0: int, y1: int, c: tuple[int, int, int],
          thickness: int = 1) -> None:
    for t in range(thickness):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            setp(px, x + t, y, c)


def brackets(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
             c: tuple[int, int, int], arm: int = 14, thick: int = 3) -> None:
    for t in range(thick):
        hline(px, x0, x0 + arm, y0 + t, c)
        hline(px, x1 - arm, x1, y0 + t, c)
        hline(px, x0, x0 + arm, y1 - t, c)
        hline(px, x1 - arm, x1, y1 - t, c)
        vline(px, x0 + t, y0, y0 + arm, c)
        vline(px, x1 - t, y0, y0 + arm, c)
        vline(px, x0 + t, y1 - arm, y1, c)
        vline(px, x1 - t, y1 - arm, y1, c)


def chevrons(px: list[list[Pixel]], x0: int, y0: int, x1: int, y1: int,
             a: tuple[int, int, int], b: tuple[int, int, int], step: int = 16) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            band = ((x - y) // step) & 1
            setp(px, x, y, a if band == 0 else b)


def iridescent(x: int, y: int, w: int, h: int) -> tuple[int, int, int]:
    p = PALETTE
    tx = x / max(1, w - 1)
    ty = y / max(1, h - 1)
    if tx < 0.45:
        base = mix(p["cyan_deep"], p["cyan"], tx / 0.45)
    elif tx < 0.7:
        base = mix(p["cyan"], p["lime"], (tx - 0.45) / 0.25)
    else:
        base = mix(p["lime"], p["magenta"], (tx - 0.7) / 0.3)
    return mix(base, p["void_mid"], 0.18 + ty * 0.12)


def void_plate(size: int, seed: int) -> list[list[Pixel]]:
    p = PALETTE
    px = blank(size, (*p["void_hull"], 255))
    vgrad(px, 0, 0, size - 1, size - 1, p["void_mid"], p["void_deep"])
    grain(px, 0, 0, size - 1, size - 1, seed, 8)
    bevel(px, 0, 0, size - 1, size - 1, p["void_rim"], p["void_deep"], 2)
    return px
