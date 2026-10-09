"""Generate the icons of the two Super Saiyan 3 god forms and of the Ikari stack (2026-10-02 owner).

Super Saiyan Blue 3 and Super Saiyan Rose 3 live in the ``xenopixels_gods_forms`` group, whose
other forms all share the group icon. These two get their own, so they can be told apart in the
transformation radial. The same image is written for the radial and for the skills screen::

    assets/xenopixelsmod/textures/gui/radial/xenopixels_gods_forms_ssb3.png
    assets/xenopixelsmod/textures/gui/icons/xenopixels_gods_forms_ssb3.png
    (and ..._ssrose3.png)

``BundledFormIcons`` looks them up by ``<group>_<form>``. The style follows the bundled group icons
(see gen_hakaishin_icon.py): a glossy orb low in a transparent 64x64 canvas with a soft glow, and
here the Super Saiyan 3 mane rising from it - a crown of spikes and the long lock down the back.
Drawn at 4x and downsampled for clean edges.

The Ikari stack is a skill of its own, so its icon is looked up by DragonMineZ under the skill's
name: ``assets/dragonminez/textures/gui/{radial,icons}/xenopixels_ikari.png``. It is the same orb
in Ikari's green with a short raging crown and no long lock.

    python tools/gen_god_form_icons.py [--preview out.png]
"""
import argparse
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 64
SS = 4
W = SIZE * SS
ASSETS = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets')

# name -> (namespace, long lock, (deep, body, bright, pale)): the form's aura, hair and extra-aura colours.
FORMS = {
    'xenopixels_gods_forms_ssb3': ('xenopixelsmod', True,
                                   ((8, 48, 110), (41, 182, 246), (129, 212, 250), (225, 245, 254))),
    'xenopixels_gods_forms_ssrose3': ('xenopixelsmod', True,
                                      ((110, 10, 66), (255, 105, 180), (255, 150, 205), (255, 222, 238))),
    'xenopixels_ikari': ('dragonminez', False,
                         ((14, 80, 6), (64, 255, 0), (127, 255, 125), (244, 255, 125))),
}
WHITE = (255, 255, 255)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def orb(layer, cx, cy, r, deep, body, bright):
    """A glossy sphere: dark rim, coloured body, a hot core low-centre, a highlight top-left."""
    px = layer.load()
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = (x - cx) / r, (y - cy) / r
            d = math.hypot(dx, dy)
            if d > 1.0 or not (0 <= x < W and 0 <= y < W):
                continue
            shade = mix(body, deep, d ** 1.7)
            core = max(0.0, 1.0 - math.hypot(dx, dy - 0.15) / 0.7)
            shade = mix(shade, bright, core ** 1.5 * 0.8)
            shade = mix(shade, WHITE, core ** 4 * 0.8)
            hl = max(0.0, 1.0 - math.hypot(dx + 0.38, dy + 0.42) / 0.3)
            shade = mix(shade, WHITE, hl ** 2 * 0.85)
            edge = min(1.0, (1.0 - d) * r / 2.0)
            px[x, y] = shade + (int(255 * edge),)


def spike(cx, cy, angle_deg, length, width):
    """A hair spike: a narrow triangle from a base centred on (cx, cy), pointing along angle."""
    a = math.radians(angle_deg)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    return [(cx - nx * width, cy - ny * width), (cx + dx * length, cy + dy * length),
            (cx + nx * width, cy + ny * width)]


def mane(long_lock=True):
    """The hair silhouette as polygons: a crown of spikes and, for Super Saiyan 3, the long back lock."""
    u = W / 64.0
    cx, cy = 30 * u, 36 * u
    polys = []
    for angle, length, width in ((150, 20, 6), (125, 25, 6.5), (100, 29, 7), (78, 27, 6.5), (55, 22, 6)):
        polys.append(spike(cx, cy, angle, length * u, width * u))
    if not long_lock:
        polys.append(spike(cx - 6 * u, cy + 2 * u, 200, 15 * u, 5 * u))
        polys.append(spike(cx + 6 * u, cy + 2 * u, -20, 15 * u, 5 * u))
        polys.append(spike(cx + 8 * u, cy - 2 * u, 25, 17 * u, 5 * u))
        return polys
    # The long lock: sweeps out to the right and down past the orb, ending in a point.
    lock = []
    for i in range(0, 21):
        t = i / 20.0
        x = cx + (6 + 20 * math.sin(t * math.pi * 0.62)) * u
        y = cy + (-6 + 30 * t) * u
        lock.append((x + (7.5 * (1.0 - t) ** 0.8) * u, y))
    back = []
    for i in range(20, -1, -1):
        t = i / 20.0
        x = cx + (6 + 20 * math.sin(t * math.pi * 0.62)) * u
        y = cy + (-6 + 30 * t) * u
        back.append((x - (7.5 * (1.0 - t) ** 0.8) * u, y))
    polys.append(lock + back)
    # Two short side locks so the crown reads as hair, not a flame.
    polys.append(spike(cx - 6 * u, cy + 2 * u, 205, 13 * u, 4.5 * u))
    polys.append(spike(cx + 8 * u, cy - 2 * u, 20, 15 * u, 4.5 * u))
    return polys


def build(long_lock, deep, body, bright, pale):
    polys = mane(long_lock)
    shape = Image.new('L', (W, W), 0)
    draw = ImageDraw.Draw(shape)
    for poly in polys:
        draw.polygon(poly, fill=255)

    # Hair: lit from the top, darker toward the roots, with a pale streak along each spike.
    hair = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    px = hair.load()
    mask = shape.load()
    for y in range(W):
        for x in range(W):
            if not mask[x, y]:
                continue
            t = y / float(W)
            shade = mix(pale, body, t * 1.25)
            px[x, y] = shade + (255,)
    streaks = Image.new('L', (W, W), 0)
    sdraw = ImageDraw.Draw(streaks)
    for poly in polys:
        tip = poly[1] if len(poly) == 3 else poly[20]
        base = ((poly[0][0] + poly[-1][0]) / 2.0, (poly[0][1] + poly[-1][1]) / 2.0)
        sdraw.line([base, tip], fill=150, width=int(W / 64.0 * 2))
    streaks = ImageChops.multiply(streaks.filter(ImageFilter.GaussianBlur(W / 64.0)), shape)
    hair = Image.composite(Image.new('RGBA', (W, W), WHITE + (255,)), hair, streaks.point(lambda v: int(v * 0.6)))
    hair.putalpha(shape)

    outline = shape.filter(ImageFilter.MaxFilter(int(W / 64.0 * 2) * 2 + 1))
    rim = Image.new('RGBA', (W, W), deep + (0,))
    rim.putalpha(outline)

    glow = shape.filter(ImageFilter.GaussianBlur(W / 64.0 * 5)).point(lambda v: int(v * 0.75))
    aura = Image.new('RGBA', (W, W), bright + (0,))
    aura.putalpha(glow)

    sphere = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    orb(sphere, W * 0.47, W * 0.68, W * 0.2, deep, body, bright)
    halo = sphere.getchannel('A').filter(ImageFilter.GaussianBlur(W / 64.0 * 4)).point(lambda v: int(v * 0.6))
    orb_glow = Image.new('RGBA', (W, W), body + (0,))
    orb_glow.putalpha(halo)

    canvas = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    for layer in (aura, orb_glow, rim, hair, sphere):
        canvas = Image.alpha_composite(canvas, layer)
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--preview', help='also write both icons side by side, enlarged, on a dark background')
    args = parser.parse_args()
    icons = {}
    for name, (namespace, long_lock, colours) in FORMS.items():
        icon = build(long_lock, *colours)
        icons[name] = icon
        for folder in ('radial', 'icons'):
            path = os.path.join(ASSETS, namespace, 'textures', 'gui', folder, name + '.png')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            icon.save(path)
            print('wrote', os.path.normpath(path))
    if args.preview:
        sheet = Image.new('RGBA', (len(icons) * 272, 272), (28, 28, 34, 255))
        for i, icon in enumerate(icons.values()):
            big = icon.resize((256, 256), Image.NEAREST)
            sheet.alpha_composite(big, (i * 272 + 8, 8))
        sheet.save(args.preview)
        print('preview', args.preview)


if __name__ == '__main__':
    main()
