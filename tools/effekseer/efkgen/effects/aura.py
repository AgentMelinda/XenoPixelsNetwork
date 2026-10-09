"""HD ki auras, switchable in game (/xenoaura dmz|hd|both).

Built from scratch (2026-09-29 owner; references: Jiren's aura and Goku Black's Super Saiyan Rose).
The aura is a tall rounded column of billowing fire: hundreds of large overlapping fire puffs
(textures.fire_billow), brightest and hottest near the body, the form's colour further out and
deep, ragged edges at the rim, with flame fibres racing up through it and a hot glow behind the
body. When the aura is off but a form is active, a thin flickering flame outline hugs the body.

Three effects per colour:
  aura_out_<hex>  the outer column: big outer puffs, mid puffs, rising flame fibres, embers.
  aura_in_<hex>   the inner shell round the body, the hot core glow and star sparkles.
  aura_rim_<hex>  the thin flickering flame outline (aura off, form on).
The game plays aura_in in the form's main colour and aura_out in its extra colour when it has one.

Smooth by construction: the game re-sends an effect every 10 ticks (30 frames at AAA's 60 fps).
Each copy emits for exactly EMIT = 30 frames (a finite count per emitter) and then lives on until
its last puff dies, so the density is constant (no pulsing) and every puff stays bound to the
player while it lives.

Colours: every aura colour in DragonMineZ's form and race data and the XenoPixels form packs, plus
a hue wheel so a custom colour snaps to a near one; palette.txt lists them. Very dark colours are
drawn with normal blending (added light cannot make something darker).

Authored upright at the feet for a 1.8-block body; the game scales by body height and the
client's /xenoaura size.
"""
import colorsys
import glob
import math
import re
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, fixed_color, node, pva_location, rng,
                       scale_ease, scale_single, scale_xyz_ease, span, spin, sprite, turbulence)

NAME = 'aura'
FOLDER = 'aura'

# Every aura colour in DragonMineZ 2.1.3's form and race data and the XenoPixels form packs
# (auraColor, defaultAuraColor, extraAuraColor), scanned 2026-09-29.
DMZ_COLOURS = [
    '01579B', '05030A', '060505', '1AA700', '1E88E5', '212121', '29B6F6', '2C0A4A', '40FF00', '570B0B',
    '5F00FF', '7B1FA2', '7FFF00', '7FFFFF', '80DEEA', '81D4FA', '8B0000', '9B0A5D', '9B5DE5', 'A10000',
    'B0BEC5', 'B3E5FC', 'B71C1C', 'C71585', 'C77DFF', 'DB182C', 'E0C8FF', 'E0F7FA', 'E1BEE7', 'E1F5FE',
    'EA80FC', 'FF1493', 'FF3030', 'FF5252', 'FF69B4', 'FF6DFF', 'FFCDD2', 'FFD633', 'FFD700', 'FFECB3',
    'FFFD99', 'FFFF69', 'FFFFFF',
]
REPO = Path(__file__).resolve().parents[4]
HEX = re.compile(r'"[A-Za-z_]*[Aa]ura[A-Za-z_]*[Cc]olou?r[A-Za-z_]*"\s*:\s*"#([0-9A-Fa-f]{6})"')

EMIT = 30          # frames each copy emits: exactly one re-send period
# Brightness levels baked in (AAA cannot tint an effect at run time): the game picks the nearest
# to /xenoaura brightness. The 100% level has no suffix. 10% and 25% were added on 2026-10-02
# (owner: "let aura brightness go from 0 to 100"); below 5% the game plays no aura at all.
LEVELS = (0.1, 0.25, 0.5, 0.75, 1.0, 1.3)
K = 1.0            # the level being built


def a(value):
    """An alpha at the brightness level being built."""
    return max(0, min(255, round(value * K)))


def suffix(level):
    return '' if level == 1.0 else f'_b{round(level * 100)}'
WHITE = (255, 255, 255)
DUST = (196, 176, 140)


def scanned_colours():
    """Aura colours in the run folder's live DragonMineZ config, if there is one (custom forms)."""
    found = set()
    for f in glob.glob(str(REPO / 'run' / 'config' / 'dragonminez' / '**' / '*.json'), recursive=True):
        if 'oldBackup' in f:
            continue
        try:
            found.update(h.upper() for h in HEX.findall(open(f, encoding='utf-8').read()))
        except OSError:
            pass
    return found


def hue_wheel():
    """24 hues, vivid and pastel, plus greys: something close to any custom aura colour."""
    out = []
    for i in range(24):
        for s, v in ((1.0, 1.0), (0.45, 1.0)):
            r, g, b = colorsys.hsv_to_rgb(i / 24.0, s, v)
            out.append('%02X%02X%02X' % (round(r * 255), round(g * 255), round(b * 255)))
    out += ['C0C0C0', '808080', '404040', '101010']
    return out


def palette():
    """DMZ colours first (kept exactly), then wheel colours not already close to one of them."""
    chosen = []
    for h in DMZ_COLOURS + sorted(scanned_colours()) + hue_wheel():
        rgb = rgb_of(h)
        if h not in chosen and all(distance(rgb, rgb_of(c)) > (0 if h in DMZ_COLOURS else 28) for c in chosen):
            chosen.append(h)
    return chosen


def rgb_of(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def distance(a, b):
    # Weighted RGB ("redmean"), the same the game uses to pick the nearest colour.
    rm = (a[0] + b[0]) / 2.0
    dr, dg, db = a[0] - b[0], a[1] - b[1], a[2] - b[2]
    return ((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) ** 0.5


def luma(c):
    return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0


def mix(c, d, t):
    return tuple(round(c[i] + (d[i] - c[i]) * t) for i in range(3))


def hsv(c):
    return colorsys.rgb_to_hsv(*(x / 255.0 for x in c))


def from_hsv(h, s, v):
    return tuple(round(x * 255) for x in colorsys.hsv_to_rgb(h % 1.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v))))


def vivid(c):
    h, s, v = hsv(c)
    return from_hsv(h, s * 1.1, max(v, 0.9))


def deep(c):
    h, s, v = hsv(c)
    return from_hsv(h, min(1.0, s * 1.15 + 0.1), v * 0.55)


def hot(c):
    """The hot core colour: lighter, and warm hues pushed toward yellow as fire is (red -> yellow)."""
    h, s, v = hsv(c)
    if s > 0.3 and (h < 0.14 or h > 0.9):
        h = (h + (0.14 - h if h < 0.14 else 1.14 - h) * 0.75) % 1.0
    return mix(from_hsv(h, s * 0.85, 1.0), WHITE, 0.15)


def count(every):
    return max(1, math.ceil(EMIT / every))


def emitter(life_frames, every):
    """A finite emitter: EMIT frames' worth of particles, each living life_frames."""
    return common(life_frames, count(every), every)


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.fire_billow(tex / 'aura_billow_a.png', 401)
    tx.fire_billow(tex / 'aura_billow_b.png', 411)
    tx.fire_billow(tex / 'aura_billow_c.png', 421)
    tx.flame_tongue(tex / 'aura_tongue.png', 331)
    tx.star(tex / 'aura_star.png')
    tx.mote(tex / 'aura_ember.png', core_width=0.14)
    tx.glow(tex / 'aura_glow.png')
    tx.dust_ring(tex / 'aura_dust_ring.png', 341)


def puffs(name, texture, colours, blend, radius, height, size, every, life=(36, 54), rise=(0.008, 0.02),
          alpha=(135, 0)):
    alpha = (a(alpha[0]), alpha[1])
    """Billowing fire puffs filling a ring-shaped slice of the column."""
    start, end = colours
    r, d = sprite(texture, blend, fade_in=8, fade_out=18, color=easing_color(
        color_range(start, alpha[0], 14), color_range(end, alpha[1], 12)))
    return node(name, CommonValues=emitter(span(*life), every),
                LocationValues=pva_location(location={'Y': span(*height)},
                                            velocity={'Y': span(*rise)}),
                RotationValues=spin((-180, 180), (-1.2, 1.2)),
                ScalingValues=scale_ease(span(size[0], size[1]), span(size[1] * 1.35, size[1] * 1.7)),
                LocationAbsValues=turbulence(0.015, 5, 361),
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=r, DrawingValues=d)


def tongues(name, colours, blend, radius, height, size_start, size_end, every, life=(16, 26), rise=(0.03, 0.05)):
    """Pointed flame licks (aura_tongue, standing upright to the camera) rising round the column."""
    start, end = colours
    r, d = sprite(T + 'aura_tongue.png', blend, billboard=1, fade_in=3, fade_out=8, color=easing_color(
        color_range(start, a(200), 12), color_range(end, 0, 10)))
    (x0, y0), (x1, y1) = size_start, size_end
    return node(name, CommonValues=emitter(span(*life), every),
                LocationValues=pva_location(location={'Y': span(*height)}, velocity={'Y': span(*rise)}),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-12, 12)}}},
                ScalingValues=scale_xyz_ease({'X': span(*x0), 'Y': span(*y0), 'Z': rng(1)},
                                             {'X': span(*x1), 'Y': span(*y1), 'Z': rng(1)}),
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=r, DrawingValues=d)


def outer(hexcode):
    c = rgb_of(hexcode)
    dark = luma(c) < 0.18
    blend = 1 if dark else 2
    rim = mix(c, WHITE, 0.4) if dark else deep(c)
    body = mix(c, WHITE, 0.15) if dark else vivid(c)
    parts = [
        # The outer column: the form's colour, darkening to ragged deep edges as the puffs age.
        puffs('OuterA', T + 'aura_billow_a.png', (body, rim), blend, (0.95, 1.35), (0.0, 3.3), (1.15, 1.6), 0.45),
        puffs('OuterB', T + 'aura_billow_b.png', (body, rim), blend, (0.9, 1.45), (0.2, 3.6), (1.05, 1.5), 0.5),
        # The ragged rim: deep-coloured puffs round the outside.
        puffs('Edge', T + 'aura_billow_c.png', (deep(c) if not dark else rim, rim), blend, (1.3, 1.6), (0.1, 3.4),
              (0.8, 1.1), 0.7, alpha=(140, 0)),
        # Mid layer, hotter toward the body, kept off it so the fighter stays visible.
        puffs('Mid', T + 'aura_billow_c.png', (mix(body, hot(c), 0.6), body), blend, (0.7, 1.1), (0.0, 3.0),
              (0.9, 1.3), 0.5),
        # Flame points (Jiren's aura): pointed licks along the whole outline, taller ones crowning it.
        tongues('EdgePoints', (body, rim), blend, (1.15, 1.55), (0.2, 3.0),
                ((0.6, 0.9), (0.9, 1.3)), ((0.4, 0.6), (1.3, 1.8)), 0.45, life=(14, 24), rise=(0.03, 0.05)),
        tongues('CrownPoints', (mix(body, hot(c), 0.3), rim), blend, (0.2, 1.1), (2.4, 3.3),
                ((0.9, 1.3), (1.4, 2.0)), ((0.6, 0.9), (2.0, 2.7)), 0.6, life=(18, 30), rise=(0.03, 0.05)),
    ]
    fr, fd = sprite(T + 'aura_tongue.png', blend, billboard=1, fade_in=2, fade_out=8, color=easing_color(
        color_range(hot(c), a(230), 10), color_range(body, 0, 10)))
    parts.append(node('Fibres', CommonValues=emitter(span(14, 24), 1.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 2.6)},
                                                  velocity={'Y': span(0.05, 0.09)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.12, 0.22), 'Y': span(0.8, 1.2), 'Z': rng(1)},
                                                   {'X': span(0.05, 0.1), 'Y': span(1.4, 2.0), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.65, 1.2),
                      RendererCommonValues=fr, DrawingValues=fd))
    er, ed = sprite(T + 'aura_ember.png', 2, fade_in=2, fade_out=12, color=easing_color(
        color_range(hot(c), a(255), 10), color_range(body, 0, 15)))
    parts.append(node('Embers', CommonValues=emitter(span(40, 60), 2.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 3.4)},
                                                  velocity={'Y': span(0.015, 0.035)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      LocationAbsValues=turbulence(0.04, 4, 351),
                      GenerationLocationValues=circle(0.6, 1.6),
                      RendererCommonValues=er, DrawingValues=ed))
    dr, dd = sprite(T + 'aura_dust_ring.png', 1, billboard=2, fade_in=6, fade_out=20, color=fixed_color(DUST, a(80)))
    parts.append(node('DustRing', CommonValues=common(40, 1),
                      LocationValues=pva_location(location={'Y': rng(0.05)}),
                      RotationValues={'Type': 0, 'Fixed': {'Rotation': {'X': 90}}},
                      ScalingValues=scale_ease(rng(1.8), rng(3.2)),
                      RendererCommonValues=dr, DrawingValues=dd))
    return node('AuraOuter', parts, CommonValues=common(EMIT + 60), DrawingValues={'Type': 0}), []


def inner(hexcode):
    c = rgb_of(hexcode)
    dark = luma(c) < 0.18
    blend = 1 if dark else 2
    shell = mix(c, WHITE, 0.3) if dark else mix(hot(c), c, 0.2)
    parts = [
        # The inner shell hugging the body (Jiren's inner flame): bright, close, fast.
        # Faint and close, so it outlines the fighter instead of hiding them.
        puffs('Shell', T + 'aura_billow_a.png', (shell, c), blend, (0.32, 0.55), (0.0, 2.1), (0.45, 0.65), 0.6,
              life=(24, 36), rise=(0.012, 0.025), alpha=(60, 0)),
    ]
    gr, gd = sprite(T + 'aura_glow.png', 2, fade_in=10, fade_out=14, color=fixed_color(hot(c), a(25 if dark else 35)))
    parts.append(node('CoreGlow', CommonValues=common(EMIT + 14, 1),
                      LocationValues=pva_location(location={'Y': rng(1.1)}),
                      ScalingValues=scale_single(3.4), RendererCommonValues=gr, DrawingValues=gd))
    sr, sd = sprite(T + 'aura_star.png', 2, fade_in=4, fade_out=8, color=easing_color(
        color_range(WHITE, a(255)), color_range(mix(c, WHITE, 0.5), 0, 10)))
    parts.append(node('Stars', CommonValues=emitter(span(10, 20), 2.5),
                      LocationValues=pva_location(location={'Y': span(0.1, 2.4)},
                                                  velocity={'Y': span(0.002, 0.008)}),
                      ScalingValues=scale_ease(span(0.05, 0.11), rng(0.0)),
                      GenerationLocationValues=circle(0.1, 0.8),
                      RendererCommonValues=sr, DrawingValues=sd))
    return node('AuraInner', parts, CommonValues=common(EMIT + 40), DrawingValues={'Type': 0}), []


def rim(hexcode):
    """The flame outline while a form is on and the aura off: tiny flickering tongues on the body."""
    c = rgb_of(hexcode)
    dark = luma(c) < 0.18
    blend = 1 if dark else 2
    flame = mix(c, WHITE, 0.35) if dark else vivid(c)
    tr, td = sprite(T + 'aura_tongue.png', blend, billboard=1, fade_in=2, fade_out=5, color=easing_color(
        color_range(mix(flame, WHITE, 0.4), a(230), 10), color_range(flame, 0, 10)))
    tongues = node('Tongues', CommonValues=emitter(span(8, 14), 0.25),
                   LocationValues=pva_location(location={'Y': span(0.05, 1.85)},
                                               velocity={'Y': span(0.01, 0.025)}),
                   ScalingValues=scale_xyz_ease({'X': span(0.07, 0.12), 'Y': span(0.16, 0.28), 'Z': rng(1)},
                                                {'X': span(0.03, 0.06), 'Y': span(0.25, 0.4), 'Z': rng(1)}),
                   GenerationLocationValues=circle(0.27, 0.36),
                   RendererCommonValues=tr, DrawingValues=td)
    er, ed = sprite(T + 'aura_ember.png', 2, fade_out=6, color=easing_color(
        color_range(mix(flame, WHITE, 0.6), a(240)), color_range(flame, 0)))
    sparks = node('Sparks', CommonValues=emitter(span(10, 18), 1.5),
                  LocationValues=pva_location(location={'Y': span(0.1, 1.9)}, velocity={'Y': span(0.01, 0.02)}),
                  ScalingValues=scale_ease(span(0.02, 0.04), rng(0.0)),
                  GenerationLocationValues=circle(0.3, 0.4),
                  RendererCommonValues=er, DrawingValues=ed)
    return node('AuraRim', [tongues, sparks], CommonValues=common(EMIT + 16), DrawingValues={'Type': 0}), []


def at_level(level, build, hexcode):
    global K
    K = level
    try:
        return build(hexcode)
    finally:
        K = 1.0


PALETTE = palette()
EFFECTS = {}
for _level in LEVELS:
    for _h in PALETTE:
        for _kind, _build in (('out', outer), ('in', inner), ('rim', rim)):
            EFFECTS[f'aura_{_kind}_{_h.lower()}{suffix(_level)}'] = (
                lambda lv=_level, b=_build, h=_h: at_level(lv, b, h))
PREVIEW = {name: 90 for name in EFFECTS}


def extra_files(dest: Path):
    """palette.txt: the colours there are effects for, one hex per line (the game picks the nearest)."""
    (dest / 'palette.txt').write_text('\n'.join(h.lower() for h in PALETTE) + '\n', encoding='utf-8')
