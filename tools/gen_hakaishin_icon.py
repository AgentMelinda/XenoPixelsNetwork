"""Generate the Hakaishin form-group icon (DMZ formType ``xenopixels_destroyer``).

DMZ looks the icon up by formType name in two places, the transformation radial and the skills
screen, so the same image is written to both::

    assets/dragonminez/textures/gui/radial/xenopixels_destroyer.png
    assets/dragonminez/textures/gui/icons/xenopixels_destroyer.png

The style follows the bundled Xeno group icons (xenopixels_divinity, xenopixels_dark_frieza): a
glossy orb in the lower half, a symbol rising out of it, and a soft outer glow on a transparent
64x64 canvas. Here it is a Hakai orb - violet, with a white-hot core - with destruction energy
curling up from it. Drawn at 4x and downsampled for clean edges.

    python tools/gen_hakaishin_icon.py [--preview out.png]
"""
import argparse
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 64
SS = 4
W = SIZE * SS
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets',
                    'dragonminez', 'textures', 'gui')

DEEP = (40, 6, 70)
BODY = (106, 27, 154)      # #6A1B9A, the form's aura colour
BRIGHT = (224, 64, 251)    # #E040FB, its eyes and outline
PALE = (234, 128, 252)     # #EA80FC, its extra aura / lightning
WHITE = (255, 240, 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def orb(layer, cx, cy, r):
    """A glossy sphere: dark rim, violet body, a hot core low-centre, a highlight top-left."""
    px = layer.load()
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = (x - cx) / r, (y - cy) / r
            d = math.hypot(dx, dy)
            if d > 1.0:
                continue
            shade = mix(BODY, DEEP, d ** 1.6)
            core = max(0.0, 1.0 - math.hypot(dx, dy - 0.1) / 0.75)
            shade = mix(shade, BRIGHT, core ** 1.5 * 0.9)
            shade = mix(shade, WHITE, core ** 4 * 0.9)
            swirl = 0.5 + 0.5 * math.sin(6.0 * math.atan2(dy, dx) + 9.0 * d)
            shade = mix(shade, PALE, swirl * 0.18 * (1.0 - d))
            hl = max(0.0, 1.0 - math.hypot(dx + 0.38, dy + 0.42) / 0.32)
            shade = mix(shade, WHITE, hl ** 2 * 0.85)
            edge = min(1.0, (1.0 - d) * r / 2.0)
            px[x, y] = shade + (int(255 * edge),)


def tongue(draw, base, height, lean, width, colour, alpha):
    """A curling flame tongue of Hakai energy rising from the orb."""
    pts_l, pts_r = [], []
    steps = 24
    for i in range(steps + 1):
        t = i / steps
        x = base[0] + lean * math.sin(t * math.pi * 0.9) * height * 0.45 + lean * t * t * height * 0.25
        y = base[1] - t * height
        half = width * (1.0 - t) ** 1.3 * (0.6 + 0.4 * math.sin(t * math.pi))
        pts_l.append((x - half, y))
        pts_r.append((x + half, y))
    draw.polygon(pts_l + pts_r[::-1], fill=colour + (alpha,))


def build(seed=7):
    random.seed(seed)
    canvas = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    cx, cy, r = W * 0.5, W * 0.68, W * 0.2

    flames = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(flames)
    # Back to front: a wide dark-violet blaze, then brighter tongues, then a white-hot heart.
    for lean, height, width, colour, alpha in [
        (-0.9, W * 0.46, W * 0.17, BODY, 235), (0.9, W * 0.42, W * 0.16, BODY, 235),
        (0.0, W * 0.58, W * 0.2, BODY, 240),
        (-0.6, W * 0.5, W * 0.12, BRIGHT, 235), (0.6, W * 0.46, W * 0.11, BRIGHT, 235),
        (0.15, W * 0.6, W * 0.12, BRIGHT, 240),
        (-0.2, W * 0.5, W * 0.07, PALE, 245), (0.05, W * 0.4, W * 0.04, WHITE, 240),
    ]:
        tongue(d, (cx + lean * W * 0.06, cy - r * 0.35), height, lean, width, colour, alpha)
    flames = flames.filter(ImageFilter.GaussianBlur(SS * 1.1))

    ball = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    orb(ball, cx, cy, r)

    # A thin bright ring around the orb, like the other icons' rims.
    ring = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(ring).ellipse((cx - r - SS, cy - r - SS, cx + r + SS, cy + r + SS),
                                 outline=PALE + (200,), width=SS)
    ring = ring.filter(ImageFilter.GaussianBlur(SS * 0.5))

    # Sparks drifting off the energy.
    sparks = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sparks)
    for _ in range(10):
        a = random.uniform(-math.pi * 0.95, -math.pi * 0.05)
        dist = random.uniform(r * 1.3, r * 2.2)
        x, y = cx + math.cos(a) * dist, cy - r * 0.5 + math.sin(a) * dist
        s = random.uniform(0.6, 1.3) * SS
        sd.ellipse((x - s, y - s, x + s, y + s), fill=random.choice([PALE, BRIGHT, WHITE]) + (220,))

    body = Image.alpha_composite(Image.alpha_composite(flames, ring), ball)
    body = Image.alpha_composite(body, sparks)

    # Soft violet glow behind everything, from the silhouette.
    glow_mask = body.split()[3].filter(ImageFilter.GaussianBlur(SS * 3))
    glow = Image.new('RGBA', (W, W), BRIGHT + (0,))
    glow.putalpha(glow_mask.point(lambda v: int(v * 0.55)))
    canvas = Image.alpha_composite(canvas, glow)
    canvas = Image.alpha_composite(canvas, body)
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write an 8x nearest-neighbour preview here')
    args = ap.parse_args()
    icon = build()
    for sub in ('radial', 'icons'):
        out = os.path.normpath(os.path.join(ROOT, sub, 'xenopixels_destroyer.png'))
        icon.save(out)
        print('wrote', out)
    if args.preview:
        bg = Image.new('RGBA', (SIZE * 8, SIZE * 8), (40, 40, 40, 255))
        bg.alpha_composite(icon.resize((SIZE * 8, SIZE * 8), Image.NEAREST))
        bg.save(args.preview)


if __name__ == '__main__':
    main()
