"""Optional polish on already-HD buffers. Bevel / gloss only — no stock regrade."""
from __future__ import annotations

from .palette import ALPHA, EMISSIVE, GLASS, HEAT, HULL

Pixel = tuple[int, int, int, int]


def clamp(v: int) -> int:
    return max(0, min(255, int(v)))


def mix(a: tuple[int, ...], b: tuple[int, ...], t: float) -> tuple[int, int, int]:
    return (
        clamp(a[0] + (b[0] - a[0]) * t),
        clamp(a[1] + (b[1] - a[1]) * t),
        clamp(a[2] + (b[2] - a[2]) * t),
    )


def classify(px: Pixel) -> str:
    r, g, b, a = px
    if a < 8:
        return ALPHA
    if r > 200 and g > 80 and b < 80:
        return HEAT
    if b > r + 30 and g > r and r < 80:
        return GLASS
    if max(r, g, b) > 180 and min(r, g, b) < 80:
        return EMISSIVE
    return HULL


def _neighbor(src: list[list[Pixel]], x: int, y: int, dx: int, dy: int) -> Pixel:
    h = len(src)
    w = len(src[0])
    nx = min(w - 1, max(0, x + dx))
    ny = min(h - 1, max(0, y + dy))
    return src[ny][nx]


def _luma(px: Pixel) -> float:
    return (px[0] * 0.3 + px[1] * 0.59 + px[2] * 0.11) / 255.0


def _streak(x: int, y: int) -> int:
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177
    return ((n >> 16) & 15) - 7


def remaster(src: list[list[Pixel]], scale: int = 1) -> list[list[Pixel]]:
    """Nearest-neighbor expand (scale>=1) then bevel / gloss. Colors stay as painted."""
    if scale < 1:
        raise ValueError("scale must be >= 1")
    h = len(src)
    w = len(src[0])
    out_h = h * scale
    out_w = w * scale
    out: list[list[Pixel]] = [[(0, 0, 0, 0) for _ in range(out_w)] for _ in range(out_h)]
    for y in range(h):
        for x in range(w):
            src_px = src[y][x]
            kind = classify(src_px)
            up = _luma(_neighbor(src, x, y, 0, -1))
            down = _luma(_neighbor(src, x, y, 0, 1))
            left = _luma(_neighbor(src, x, y, -1, 0))
            right = _luma(_neighbor(src, x, y, 1, 0))
            bevel = (up - down) * 16 + (left - right) * 10
            r0, g0, b0, a = src_px
            for sy in range(scale):
                for sx in range(scale):
                    streak = _streak(x * scale + sx, y * scale + sy) // 2
                    edge = 0
                    if scale > 1:
                        if sx == 0 or sy == 0:
                            edge = 10
                        if sx == scale - 1 or sy == scale - 1:
                            edge = -10
                    r, g, b = r0, g0, b0
                    if kind == GLASS and (y * scale + sy) % 4 == 0:
                        r, g, b = mix((r, g, b), (0, 40, 48), 0.12)
                    r = clamp(r + bevel + streak + edge)
                    g = clamp(g + bevel + streak + edge)
                    b = clamp(b + bevel + streak + edge)
                    out[y * scale + sy][x * scale + sx] = (r, g, b, a)
    return out
