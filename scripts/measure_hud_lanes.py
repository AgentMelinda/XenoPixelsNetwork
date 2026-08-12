#!/usr/bin/env python3
"""Measure the Xeno HUD artwork's bar lanes and print them as Java constants.

The panel in XenoModernHudView is a blit of xeno_hud_xv.png, and the live bars are drawn on top of
it. Those overlays used to be hand-tuned magic numbers in panel space, which drifted from the art --
partly because the blit is NON-UNIFORMLY scaled (x by 420/1536, y by 180/720), so a lane that looks
square in the source is not square on screen.

This reads the geometry back out of the artwork instead. Each lane in the source has a baked
"demonstration" fill in a distinctive colour; classifying those pixels gives exact left edges, tops,
heights and the parallelogram slant. Right edges come from walking the lane's dark track until the
plate's bright rim.

Run from the XenoPixelsNetwork directory:

    python scripts/measure_hud_lanes.py
"""

from PIL import Image

ART = "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_hud_xv.png"

# Must match XenoModernHudView.
ART_W, ART_H = 1536, 1024
CROP_Y, CROP_H = 100, 720
PANEL_W, PANEL_H = 420, 180

SX = PANEL_W / ART_W
SY = PANEL_H / CROP_H

# Left of this is the portrait ring, whose blue glow otherwise reads as KI.
PLATE_X0 = 520

# Approximate vertical centre of each lane, used to seed the search.
LANES = [
    ("RELEASE", "WARM", 431),
    ("HP", "WARM", 491),
    ("KI", "KI", 556),
    ("STM", "STM", 614),
]


def classify(p):
    r, g, b = p
    if r > 230 and g > 140 and b < 50:
        return "WARM"
    if r < 90 and b > 200 and b - g > 20:
        return "KI"
    if r < 110 and g > 200 and abs(b - g) <= 20:
        return "STM"
    return None


def main():
    im = Image.open(ART).convert("RGB")
    w, h = im.size
    px = im.load()

    def is_dark(x, y):
        r, g, b = px[x, y]
        return r + g + b < 200

    def left_edge(y, cls):
        for x in range(PLATE_X0, w):
            if classify(px[x, y]) == cls:
                return x
        return None

    def right_edge(y, cls, start):
        """Walk right through fill and dark track; stop at the plate's bright rim."""
        last = start
        for x in range(start, w):
            if classify(px[x, y]) == cls or is_dark(x, y):
                last = x
            elif x - last > 6:  # a real bright rim, not a highlight speck
                break
        return last

    def vertical_extent(cls, seed_y, seed_x):
        top = bottom = seed_y
        while top > 0 and any(
            classify(px[x, top - 1]) == cls for x in range(seed_x - 40, seed_x + 200)
        ):
            top -= 1
        while bottom < h - 1 and any(
            classify(px[x, bottom + 1]) == cls for x in range(seed_x - 40, seed_x + 200)
        ):
            bottom += 1
        return top, bottom

    print(f"scale: x {SX:.6f}  y {SY:.6f}   (non-uniform -- this is the trap)\n")

    # HudDraw.fillPara(x, y, w, h, skew) puts the TOP row at x+skew and the BOTTOM row at x, and
    # treats w as the full footprint (bottom-left to top-right). So the anchor to report is the
    # bottom-left corner, and the width has to reach the top-right one.
    for name, cls, mid_y in LANES:
        seed = left_edge(mid_y, cls)
        if seed is None:
            print(f"{name}: no {cls} pixels found on row {mid_y}")
            continue
        top, bottom = vertical_extent(cls, mid_y, seed)

        # Everything is measured on the lane's centre row, where the fill is solid and the track is
        # unambiguous, then projected out to the corners using the slant. Sampling the extreme rows
        # directly is unreliable: the parallelogram's tips are only a pixel or two tall and the
        # right-edge walk trips over highlight specks there.
        left_top = left_edge(top + 2, cls)
        left_bottom = left_edge(bottom - 2, cls)
        if None in (left_top, left_bottom):
            print(f"{name}: could not bracket the lane")
            continue
        slant_tex = left_top - left_bottom  # positive: top sits right of bottom

        left_mid = seed
        right_mid = right_edge(mid_y, cls, left_mid)
        half = slant_tex // 2
        left_bottom = left_mid - half
        right_top = right_mid + half
        width_tex = right_top - left_bottom

        px_x = round(left_bottom * SX)
        px_y = round((top - CROP_Y) * SY)
        px_w = round(width_tex * SX)
        px_h = round((bottom - top) * SY)
        px_skew = round(slant_tex * SX)

        print(f"{name:8s} TEX x={left_bottom}..{right_top} y={top}..{bottom} slant={slant_tex:+d}")
        print(
            f"{'':8s} -> private static final Lane {name} = "
            f"tex({left_bottom}, {top}, {width_tex}, {bottom - top});"
        )
        print(
            f"{'':8s}    panel x={px_x} y={px_y} w={px_w} h={px_h} skew={px_skew}"
        )
        print()


if __name__ == "__main__":
    main()
