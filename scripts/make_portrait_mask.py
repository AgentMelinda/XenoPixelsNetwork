#!/usr/bin/env python3
"""Generate the Xeno HUD portrait mask from the HUD artwork.

The player portrait is a square image sitting in a round well, so its corners show against the dark
circle. Rather than approximate the well in code, this cuts the mask out of the artwork itself: the
inside of the well becomes transparent and everything outside it stays pixel-identical to the
source. Drawing the portrait and then blitting this over it clips the portrait to the circle, and
the chrome around it IS the original art rather than a guess at it.

Regenerate whenever xeno_hud_xv.png changes:

    python scripts/make_portrait_mask.py
"""

from PIL import Image

SRC = "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_hud_xv.png"
DST = "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_portrait_mask.png"

# The well, measured off the artwork (see measure_hud_lanes.py for the same technique): the dark
# interior spans x 148..448 and y 314..609, giving a centre of (298, 462) and a radius of ~149.
WELL_CX, WELL_CY, WELL_R = 298, 462, 149

# Region of the source to carry into the mask. Generous enough to include the full ring and its
# outer chrome, so the mask can be blitted as a straight replacement for that patch of the panel.
REGION_X, REGION_Y, REGION_SIZE = 98, 262, 400

# Alpha ramp width in source pixels. The mask is downscaled ~3.7x horizontally at blit time, so a
# hard edge would alias badly; a couple of pixels of falloff resolves to a clean curve.
FEATHER = 2.5


def main():
    src = Image.open(SRC).convert("RGBA")
    region = src.crop(
        (REGION_X, REGION_Y, REGION_X + REGION_SIZE, REGION_Y + REGION_SIZE)
    ).copy()
    px = region.load()

    cx = WELL_CX - REGION_X
    cy = WELL_CY - REGION_Y

    cleared = 0
    for y in range(REGION_SIZE):
        dy = y - cy
        for x in range(REGION_SIZE):
            dx = x - cx
            dist = (dx * dx + dy * dy) ** 0.5
            if dist >= WELL_R:
                continue
            r, g, b, a = px[x, y]
            if dist <= WELL_R - FEATHER:
                px[x, y] = (r, g, b, 0)
                cleared += 1
            else:
                # Feathered rim: fade the source back in over the last couple of pixels.
                t = (dist - (WELL_R - FEATHER)) / FEATHER
                px[x, y] = (r, g, b, int(round(a * t)))

    region.save(DST)
    print(f"wrote {DST}  ({REGION_SIZE}x{REGION_SIZE}, {cleared} px cleared)")
    print(
        f"blit region in source texture: x={REGION_X} y={REGION_Y} "
        f"size={REGION_SIZE}  well centre=({cx},{cy}) r={WELL_R}"
    )


if __name__ == "__main__":
    main()
