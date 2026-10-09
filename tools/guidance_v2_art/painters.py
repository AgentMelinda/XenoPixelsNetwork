"""Native 128× fork painters. Void hull + neon function colors — not a remaster of v1."""
from __future__ import annotations

import math

from .draw import (
    Pixel,
    bevel,
    blank,
    brackets,
    chevrons,
    disc,
    grain,
    hgrad,
    hline,
    iridescent,
    mix,
    radial,
    rect,
    ring,
    scanlines,
    setp,
    vgrad,
    vline,
    void_plate,
)
from .palette import PALETTE

SIZE = 128


def _p() -> dict:
    return PALETTE


def guidance_front() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 11)
    bevel(px, 4, 4, 123, 123, p["magenta"], p["magenta_deep"], 6)
    bevel(px, 12, 12, 115, 115, p["cyan"], p["cyan_deep"], 3)
    vgrad(px, 18, 18, 109, 109, p["holo"], p["holo_dark"])
    scanlines(px, 18, 18, 109, 109, p["cyan_deep"], 4, 0.32)
    cx, cy = 64, 64
    ring(px, cx, cy, 36, p["cyan"], 2)
    ring(px, cx, cy, 22, p["magenta"], 2)
    hline(px, 28, 100, cy, p["cyan"], 2)
    vline(px, cx, 28, 100, p["cyan"], 2)
    disc(px, cx, cy, 4, p["magenta"])
    for i in range(8):
        ang = i * 0.785398
        x = int(cx + 40 * math.cos(ang))
        y = int(cy + 40 * math.sin(ang))
        disc(px, x, y, 2, p["amber"] if i % 2 == 0 else p["lime"])
    brackets(px, 20, 20, 107, 107, p["magenta"], 16, 3)
    return px


def guidance_side() -> list[list[Pixel]]:
    """The flank, painted to tile.

    Every feature runs the full width of the texture. The side face is what meets the next block,
    so anything that stops short of the border draws a line down the join -- and the console is
    built two blocks wide, which made that join the most visible thing on it. Two of these placed
    together used to read as two separate framed boxes rather than one machine.

    The framing belongs to ``guidance_front`` alone: that face is the console's front and is meant
    to look like a self-contained panel. This one is its flank.
    """
    p = _p()
    px = void_plate(SIZE, 21)
    # Edge to edge. These used to be inset ten pixels, leaving a gap at every block boundary.
    for y in range(16, 112, 10):
        hline(px, 0, 127, y, p["cyan_deep"], 3)
        hline(px, 0, 127, y + 1, p["cyan"], 1)
    rect(px, 0, 52, 127, 76, p["magenta_deep"])
    hgrad(px, 0, 56, 127, 72, p["magenta"], p["cyan"])
    # A full-width holo strip instead of the small bevelled screen that used to sit in the upper
    # right. That screen carried its own frame and repeated once per block, which is what made a
    # row of them look like a row of separate consoles.
    vgrad(px, 0, 20, 127, 44, p["holo"], p["holo_dark"])
    scanlines(px, 0, 20, 127, 44, p["cyan_deep"], 3, 0.35)
    return px


def guidance_top() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 31)
    ring(px, 64, 64, 50, p["cyan_deep"], 4)
    ring(px, 64, 64, 36, p["magenta"], 3)
    ring(px, 64, 64, 20, p["cyan"], 2)
    disc(px, 64, 64, 8, p["magenta"])
    disc(px, 64, 64, 3, p["cyan"])
    hline(px, 14, 114, 64, p["cyan_mid"], 2)
    vline(px, 64, 14, 114, p["cyan_mid"], 2)
    return px


def _thruster_housing(on: bool) -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 41 if not on else 42)
    for i in range(8):
        y0 = 12 + i * 14
        rect(px, 8, y0, 28, y0 + 7, p["cyan_deep"])
        hline(px, 8, 28, y0, p["cyan"], 1)
        rect(px, 99, y0, 119, y0 + 7, p["cyan_deep"])
        hline(px, 99, 119, y0, p["cyan"], 1)
    ring(px, 64, 64, 40, p["amber"], 6)
    ring(px, 64, 64, 32, p["amber_hot"] if on else p["magenta_deep"], 3)
    if on:
        radial(px, 64, 64, 28, p["heat_core"], p["heat_rim"])
        disc(px, 64, 64, 8, p["heat_core"])
    else:
        disc(px, 64, 64, 26, p["void_deep"])
        ring(px, 64, 64, 18, p["cyan_deep"], 2)
    return px


def thruster_front() -> list[list[Pixel]]:
    return _thruster_housing(False)


def thruster_front_on() -> list[list[Pixel]]:
    return _thruster_housing(True)


def thruster_back() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 51)
    for i in range(5):
        inset = 10 + i * 10
        rect(px, inset, inset, 127 - inset, 127 - inset, p["void_mid"] if i % 2 == 0 else p["cyan_deep"])
    for y in range(20, 108, 8):
        hline(px, 20, 107, y, p["cyan"], 1)
    bevel(px, 16, 16, 111, 111, p["cyan"], p["void_deep"], 3)
    return px


def thruster_side() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 61)
    vgrad(px, 8, 24, 96, 103, p["void_mid"], p["void_deep"])
    for y in range(28, 100, 10):
        hline(px, 12, 90, y, p["cyan_deep"], 2)
        hline(px, 12, 90, y, p["cyan"], 1)
    rect(px, 96, 18, 124, 109, p["amber"])
    vgrad(px, 100, 22, 120, 105, p["amber_hot"], p["heat_rim"])
    grain(px, 96, 18, 124, 109, 62, 6)
    return px


def tube_top() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 71)
    chevrons(px, 8, 8, 119, 119, p["amber"], p["magenta_deep"], 18)
    rect(px, 0, 54, 127, 73, p["magenta"])
    hline(px, 0, 127, 56, p["cyan"], 2)
    hline(px, 0, 127, 70, p["cyan"], 2)
    disc(px, 64, 64, 22, p["void_deep"])
    ring(px, 64, 64, 22, p["cyan"], 3)
    ring(px, 64, 64, 12, p["magenta"], 2)
    return px


def tube_side() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 81)
    chevrons(px, 0, 20, 127, 107, p["amber"], p["magenta_deep"], 16)
    rect(px, 0, 52, 127, 76, p["magenta"])
    hline(px, 0, 127, 54, p["cyan"], 2)
    hline(px, 0, 127, 73, p["cyan"], 2)
    return px


def tube_bottom() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 91)
    chevrons(px, 12, 12, 115, 115, p["amber"], p["void_mid"], 20)
    ring(px, 64, 64, 40, p["magenta"], 4)
    disc(px, 64, 64, 18, p["void_deep"])
    ring(px, 64, 64, 18, p["cyan"], 2)
    return px


def loader_front() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 101)
    bevel(px, 8, 8, 119, 119, p["lime"], p["lime_deep"], 4)
    bevel(px, 16, 16, 111, 111, p["cyan"], p["cyan_deep"], 2)
    ring(px, 64, 64, 34, p["lime"], 3)
    ring(px, 64, 64, 22, p["cyan"], 2)
    disc(px, 64, 64, 12, p["lime"])
    disc(px, 64, 64, 4, p["cyan"])
    brackets(px, 22, 22, 105, 105, p["lime"], 12, 2)
    return px


def loader_side() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 111)
    for x in range(16, 112, 12):
        vline(px, x, 16, 111, p["cyan_deep"], 3)
    rect(px, 0, 48, 127, 80, p["lime_deep"])
    hgrad(px, 0, 52, 127, 76, p["lime"], p["cyan"])
    return px


def loader_top() -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 121)
    disc(px, 64, 64, 28, p["lime_deep"])
    ring(px, 64, 64, 28, p["lime"], 4)
    disc(px, 64, 64, 10, p["lime"])
    hline(px, 20, 107, 64, p["cyan"], 2)
    vline(px, 64, 20, 107, p["cyan"], 2)
    return px


def _wing(hinge: str) -> list[list[Pixel]]:
    p = _p()
    px = blank(SIZE, (*p["void_deep"], 255))
    for y in range(SIZE):
        for x in range(SIZE):
            setp(px, x, y, iridescent(x, y, SIZE, SIZE))
    grain(px, 0, 0, SIZE - 1, SIZE - 1, 201, 6)
    bevel(px, 0, 0, SIZE - 1, SIZE - 1, p["cyan"], p["magenta_deep"], 3)
    if hinge == "h":
        rect(px, 8, 54, 119, 73, p["magenta_deep"])
        hgrad(px, 8, 58, 119, 69, p["magenta"], p["cyan"])
    elif hinge == "v":
        rect(px, 54, 8, 73, 119, p["magenta_deep"])
        vgrad(px, 58, 8, 69, 119, p["magenta"], p["cyan"])
    else:
        rect(px, 8, 96, 119, 112, p["magenta_deep"])
        hgrad(px, 8, 98, 119, 110, p["magenta"], p["cyan_mid"])
    return px


def wing_panel() -> list[list[Pixel]]:
    return _wing("skin")


def wing_flap_h() -> list[list[Pixel]]:
    return _wing("h")


def wing_flap_v() -> list[list[Pixel]]:
    return _wing("v")


def _copycat(lit: bool) -> list[list[Pixel]]:
    p = _p()
    px = void_plate(SIZE, 211 if not lit else 212)
    vgrad(px, 0, 0, SIZE - 1, SIZE - 1, p["void_hull"], p["void_deep"])
    grain(px, 0, 0, SIZE - 1, SIZE - 1, 213, 5)
    accent_a = p["cyan"] if not lit else p["lime"]
    accent_b = p["magenta"]
    # Dual neon cross
    for t in range(-3, 4):
        for i in range(18, 110):
            setp(px, i + t, i, accent_a)
            setp(px, 127 - i + t, i, accent_b)
    if lit:
        disc(px, 64, 64, 14, p["lime"])
        ring(px, 64, 64, 20, p["cyan"], 2)
    else:
        disc(px, 64, 64, 6, p["void_rim"])
    bevel(px, 0, 0, SIZE - 1, SIZE - 1, accent_a, p["magenta_deep"], 3)
    return px


def copycat() -> list[list[Pixel]]:
    return _copycat(False)


def copycat_lit() -> list[list[Pixel]]:
    return _copycat(True)


def seat_frame() -> list[list[Pixel]]:
    p = _p()
    px = blank(SIZE, (*p["chrome_dark"], 255))
    vgrad(px, 0, 0, SIZE - 1, SIZE - 1, p["chrome"], p["chrome_dark"])
    grain(px, 0, 0, SIZE - 1, SIZE - 1, 221, 9)
    bevel(px, 0, 0, SIZE - 1, SIZE - 1, p["chrome"], p["void_deep"], 3)
    for x, y in ((20, 20), (108, 20), (20, 108), (108, 108)):
        disc(px, x, y, 6, p["void_hull"])
        ring(px, x, y, 6, p["cyan"], 2)
    hline(px, 8, 119, 8, p["cyan"], 2)
    hline(px, 8, 119, 118, p["magenta"], 2)
    return px


def seat_cushion() -> list[list[Pixel]]:
    p = _p()
    px = blank(SIZE, (*p["cyan_deep"], 255))
    for y in range(SIZE):
        for x in range(SIZE):
            t = x / (SIZE - 1)
            setp(px, x, y, mix(p["cyan"], p["magenta"], t))
    grain(px, 0, 0, SIZE - 1, SIZE - 1, 222, 6)
    for y in range(16, 112, 12):
        hline(px, 10, 117, y, p["void_deep"], 1)
    bevel(px, 0, 0, SIZE - 1, SIZE - 1, p["cyan"], p["magenta_deep"], 3)
    return px


def target_tool() -> list[list[Pixel]]:
    p = _p()
    px = blank(SIZE)
    vgrad(px, 52, 16, 75, 111, p["void_mid"], p["void_deep"])
    bevel(px, 52, 16, 75, 111, p["void_rim"], p["void_deep"], 2)
    disc(px, 64, 36, 22, p["void_hull"])
    ring(px, 64, 36, 20, p["cyan"], 3)
    hline(px, 44, 84, 36, p["cyan"], 2)
    vline(px, 64, 16, 56, p["cyan"], 2)
    disc(px, 64, 36, 3, p["magenta"])
    rect(px, 48, 100, 79, 116, p["amber"])
    return px


def panel_configurator() -> list[list[Pixel]]:
    p = _p()
    px = blank(SIZE)
    vgrad(px, 40, 20, 87, 107, p["void_mid"], p["void_deep"])
    bevel(px, 40, 20, 87, 107, p["void_rim"], p["void_deep"], 2)
    for i in range(28, 100):
        setp(px, i, i, p["cyan"])
        setp(px, i + 1, i, p["cyan"])
        setp(px, 127 - i, i, p["magenta"])
        setp(px, 126 - i, i, p["magenta"])
    rect(px, 48, 48, 79, 79, p["holo_dark"])
    scanlines(px, 48, 48, 79, 79, p["cyan"], 3, 0.4)
    bevel(px, 48, 48, 79, 79, p["magenta"], p["cyan_deep"], 2)
    return px


PAINTERS: dict[str, callable] = {
    "guidance_front": guidance_front,
    "guidance_side": guidance_side,
    "guidance_top": guidance_top,
    "guidance": guidance_front,
    "thruster_front": thruster_front,
    "thruster_front_on": thruster_front_on,
    "thruster_back": thruster_back,
    "thruster_side": thruster_side,
    "thruster": thruster_front,
    "thruster_on": thruster_front_on,
    "tube_top": tube_top,
    "tube_side": tube_side,
    "tube_bottom": tube_bottom,
    "tube": tube_top,
    "loader_front": loader_front,
    "loader_side": loader_side,
    "loader_top": loader_top,
    "loader": loader_front,
    "wing_panel": wing_panel,
    "wing_flap_h": wing_flap_h,
    "wing_flap_v": wing_flap_v,
    "copycat": copycat,
    "copycat_lit": copycat_lit,
    "seat_frame": seat_frame,
    "seat_cushion": seat_cushion,
    "target_tool": target_tool,
    "panel_configurator": panel_configurator,
}


def paint(key: str) -> list[list[Pixel]]:
    painter = PAINTERS.get(key)
    if painter is None:
        return wing_panel()
    return painter()
