"""Greyscale copy of the BT3 HUD atlas, for tinting bar fills any colour.

The HP and ki fills in ``xeno_bt3_hud_atlas.png`` are painted yellow and cyan, and the game's
tint multiplies, so a purple tint over them comes out muddy. This writes
``xeno_bt3_hud_atlas_neutral.png`` with the same layout: each pixel's luminance, stretched so
the brightest part of every sprite is white. Tinted, it gives a clean bar in any colour with the
original shading. It is used for form tints (Hakaishin's purple bars, ``FormHudTint``).

    python tools/gen_hud_neutral_atlas.py
"""
import os

from PIL import Image

HERE = os.path.dirname(__file__)
GUI = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'xenopixelsmod', 'textures', 'gui')
SRC = os.path.join(GUI, 'xeno_bt3_hud_atlas.png')
OUT = os.path.join(GUI, 'xeno_bt3_hud_atlas_neutral.png')


def main():
    im = Image.open(SRC).convert('RGBA')
    px = im.load()
    w, h = im.size
    lum = [[0.0] * w for _ in range(h)]
    peak = 1.0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            v = 0.2126 * r + 0.7152 * g + 0.0722 * b
            # A saturated fill (pure cyan, pure yellow) reads dark in luminance alone; take the
            # brightest channel into account so a vivid fill becomes a bright neutral.
            v = max(v, 0.85 * max(r, g, b))
            lum[y][x] = v
            if a > 0:
                peak = max(peak, v)
    out = Image.new('RGBA', (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            a = px[x, y][3]
            v = min(255, int(round(lum[y][x] * 255.0 / peak)))
            op[x, y] = (v, v, v, a)
    out.save(os.path.normpath(OUT))
    print('wrote', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
