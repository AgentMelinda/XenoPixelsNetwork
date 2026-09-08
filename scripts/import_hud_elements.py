#!/usr/bin/env python3
"""Import Xenoverse blank HUD shells from the asset pack into runtime textures.

The packs in `new/` ship 1536x1024 masters at ~1.2MB each, almost all of which is transparent
padding and glow falloff. Shipping those in the jar would be wasteful and would make UV maths
guesswork. This crops each shell to its real content and downscales it to a size with sensible
headroom for GUI scale, so the code can blit it directly.

Reads straight out of the zip so the import is reproducible and the archive stays the source of
truth. Run from the XenoPixelsNetwork directory:

    python scripts/import_hud_elements.py
"""

import io
import zipfile
from PIL import Image

ZIP = "new/example_blank_no_text_elements.zip"
OUT_DIR = "src/main/resources/assets/xenopixelsmod/textures/gui/xenoverse/combat"

# Style 01 has the flattest centre panel of the three, which matters because a meter fill and two
# text runs are drawn on top of it. Styles 02 and 03 carry a diagonal highlight sweep across the
# centre that fights anything overlaid there.
SOURCE = "example_blank_no_text_elements/buttons_blank/example_blank_button_style_01.png"
DEST = "button_shell.png"

# Alpha threshold for the content crop. The raw alpha bounding box is useless here: the glow
# falloff reaches almost the full canvas, so getbbox() returns ~1499x950 while the actual button is
# ~1494x373. 16 reproduces the figures the asset guide quotes.
ALPHA_FLOOR = 16

# Target width. The guide renders these at 90-140px; 512 keeps roughly 4x headroom for GUI scale
# without carrying a 1500px master into the jar.
TARGET_WIDTH = 512


# The menu comes from a second pack. The original `menus_blank` shell baked the selected state into
# row 3 of the artwork, which pinned the highlight to a slot the player never actually selected.
# This pack splits the two: a neutral four-row panel plus a standalone highlight bar the code can
# place on whichever row is live.
MENU_ZIP = "new/menu_highlight_separate_bundle.zip"
MENU_SOURCE = "menu_highlight_separate_bundle/example_menu_panel_no_slot_highlight.png"
MENU_DEST = "ki_menu_panel.png"
MENU_TARGET_WIDTH = 512

HIGHLIGHT_SOURCE = "menu_highlight_separate_bundle/example_separate_slot_highlight.png"
HIGHLIGHT_DEST = "ki_menu_row_highlight.png"
# The highlight is drawn across ~0.86 of the panel width, so 512 gives it the same headroom per
# rendered pixel that the panel itself gets.
HIGHLIGHT_TARGET_WIDTH = 512


def crop_and_scale(archive, source, target_width):
    image = Image.open(io.BytesIO(archive.read(source))).convert("RGBA")
    alpha = image.split()[3]
    box = alpha.point(lambda p: 255 if p > ALPHA_FLOOR else 0).getbbox()
    cropped = image.crop(box)
    cw, ch = cropped.size
    height = max(1, round(target_width * ch / cw))
    return image, box, cropped, cropped.resize((target_width, height), Image.LANCZOS)






def menu_row_fractions(cropped):
    """Row centres, as fractions of panel height.

    The menu shell bakes four rows into the artwork, so unlike the button it cannot be
    nine-sliced to an arbitrary size -- the code has to place its rows where the texture already
    drew them.

    The landmark is the dark key well at the left of each row, not the green status square: the
    square carries an inner texture that breaks into two blobs on this artwork and yields centres
    a couple of pixels high. The well is a solid block, and the header band's well starts further
    left (~0.065 vs ~0.128), which is what separates it from the four rows.
    """
    w, h = cropped.size
    px = cropped.load()
    x0, x1 = int(w * 0.14), int(w * 0.20)

    def well_row(y):
        # Counted rather than all-or-nothing: every well carries a diagonal gloss stripe, and
        # requiring the whole window to be dark splits a row in two where that stripe crosses it.
        n = sum(1 for x in range(x0, x1)
                if (lambda p: p[3] > 200 and p[0] < 32 and p[1] < 36 and p[2] < 48)(px[x, y]))
        return n >= (x1 - x0) * 0.6

    def well_left(y):
        """Left edge of the dark run through the well, as a fraction of width."""
        x = x0
        while x > 0:
            r, g, b, a = px[x - 1, y]
            if not (a > 200 and r < 40 and g < 46 and b < 60):
                break
            x -= 1
        return x / w

    spans, start = [], None
    for y in range(h + 1):
        hit = y < h and well_row(y)
        if hit and start is None:
            start = y
        elif not hit and start is not None:
            if y - start > 20:
                spans.append((start, y - 1))
            start = None

    return [((a + b) / 2) / h for a, b in spans
            if well_left((a + b) // 2) > 0.11]


def main():
    with zipfile.ZipFile(ZIP) as archive:
        image, box, cropped, scaled = crop_and_scale(archive, SOURCE, TARGET_WIDTH)
        scaled.save(f"{OUT_DIR}/{DEST}")

    with zipfile.ZipFile(MENU_ZIP) as archive:
        menu_img, menu_box, menu_cropped, menu_scaled = crop_and_scale(
            archive, MENU_SOURCE, MENU_TARGET_WIDTH)
        menu_scaled.save(f"{OUT_DIR}/{MENU_DEST}")
        row_fracs = menu_row_fractions(menu_cropped)
        hl_img, hl_box, hl_cropped, hl_scaled = crop_and_scale(
            archive, HIGHLIGHT_SOURCE, HIGHLIGHT_TARGET_WIDTH)
        hl_scaled.save(f"{OUT_DIR}/{HIGHLIGHT_DEST}")

    cw, ch = cropped.size
    height = scaled.size[1]

    # Cap boundaries, measured off the chrome rather than eyeballed. The shell is
    # [key pod][stretchable centre][chevron end cap], and the two caps are NOT the same width --
    # which is why the drawing code needs a horizontal three-slice and not a symmetric nine-slice.
    left_cap = 437 / 1494
    right_cap = 233 / 1494

    print(f"source   {SOURCE.split('/')[-1]}  canvas={image.size}")
    print(f"cropped  box={box}  size={cw}x{ch}  aspect={cw / ch:.2f}")
    print(f"written  {OUT_DIR}/{DEST}  {TARGET_WIDTH}x{height}")
    print()
    print("three-slice insets (fractions of width):")
    print(f"  left  cap {left_cap:.4f}  -> {round(TARGET_WIDTH * left_cap)}px at this size")
    print(f"  right cap {right_cap:.4f}  -> {round(TARGET_WIDTH * right_cap)}px at this size")
    print()

    mw, mh = menu_cropped.size
    print(f"menu     {MENU_SOURCE.split('/')[-1]}")
    print(f"cropped  box={menu_box}  size={mw}x{mh}  aspect={mw / mh:.3f}")
    print(f"written  {OUT_DIR}/{MENU_DEST}  {menu_scaled.size[0]}x{menu_scaled.size[1]}")
    print("  Four rows are baked into this artwork, so the panel must be drawn at the aspect above")
    print("  and its rows placed at these height fractions rather than nine-sliced:")
    for i, f in enumerate(row_fracs):
        print(f"    row {i} centre {f:.4f}")
    if len(row_fracs) > 1:
        print(f"    pitch        {row_fracs[1] - row_fracs[0]:.4f}")
    print("  and its wells horizontally (fractions of panel width):")
    print("    key block    0.1277 .. 0.2079")
    print("    name plate   0.2146 .. 0.8215")
    print("    status sq.   0.8290 .. 0.8636")
    print("    header band  above 0.2451")
    print()

    hw, hh = hl_cropped.size
    print(f"highlight {HIGHLIGHT_SOURCE.split('/')[-1]}")
    print(f"cropped  box={hl_box}  size={hw}x{hh}  aspect={hw / hh:.3f}")
    print(f"written  {OUT_DIR}/{HIGHLIGHT_DEST}  {hl_scaled.size[0]}x{hl_scaled.size[1]}")
    print("  This is a standalone bar, not a slice of the panel canvas, so it is placed by fitting")
    print("  its crop to the live row rather than by blitting it at panel coordinates. The fit that")
    print("  lands its chrome on the row chrome (checked by compositing against the panel):")
    print("    x    0.048 .. 0.912 of panel width")
    print("    h    0.175 of panel height, centred on the row centre")


if __name__ == "__main__":
    main()
