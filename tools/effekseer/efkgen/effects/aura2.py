"""HD aura, variant 2 (2026-10-02 owner): the alternative look, chosen with /xenoaura v2.

Where variant 1 (aura.py) is a soft column of fire puffs, this one is drawn after DragonMineZ's
own aura and Jiren's flames: a cel-shaded, spiked silhouette of flame that stands round the
fighter - the egg shape, white-hot rim and flat darker bands of DMZ's kakarot_aura sheet
(textures.spiked_flame, four frames) - flickering between its frames, with flame-shaped licks
that rise from the feet, pass the silhouette, and vanish above it in the punch / ki-impact
glow (Effekseer ToonHit sample, punch_impact Glow: RGB(255,92,32) fading to (255,64,16)).

One effect per colour and brightness level, aura2_<hex>[_bNN], in the same palette and at the same
levels as variant 1, so the game picks it by the same nearest-colour rule (aura/palette.txt).

Smooth by the same construction as variant 1: the game re-sends the effect every 10 ticks
(30 frames); each copy emits for exactly EMIT frames and lives on until its last sprite dies.

Dimensions and placement, read off DragonMineZ 2.1.3 (AuraRenderer.executeAuraShaderDraw). Its
billboard is the quad [-1,1] x [-1,1], scaled by S = aura scale x 2.2 (aura scale = model scale x
1.05, so S = 2.166 for a player at rest) and centred 0.05 + 0.7 S above the feet; the flame fills
rows 29-986 and columns 119-881 of each 1024 frame. So at rest DMZ's flame runs from 0.44 below
the feet to 3.61 above them (4.05 tall) and is 3.22 wide.

This effect is authored to that box, in blocks, for /xenoaura size 1. An Effekseer sprite of
scale s is s across - its quad is +-0.5, not +-1. The first builds of this effect took it for 2 s,
so the silhouette came out half the planned height while its centre stayed where the full height
needed it, and the flame started at the fighter's shoulders (2026-10-02 owner, with a screenshot:
"aura start postion is wrong learn from dmz"). The game scales the effect about the feet by DMZ's
own aura scale, which is also how DMZ's aura grows, so the two stay matched as it stretches.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, node, pva_location, rng, scale_ease,
                       scale_xyz_ease, span, sprite)
from . import aura as v1

NAME = 'aura2'
FOLDER = 'aura2'

FRAMES = ('aura2_flame_a.png', 'aura2_flame_b.png', 'aura2_flame_c.png', 'aura2_flame_d.png')
# punch_impact / punch_heavy Glow node (ToonHit). R is 255; the sample XML omits it as the editor default.
IMPACT_HOT = (255, 92, 32)
IMPACT_COOL = (255, 64, 16)
# The silhouette sprite, in blocks. The flame fills 90% of its height (4.05 of 4.5) and its lowest
# point is 7% up from the sprite's bottom edge, so a centre 1.5 above the feet puts that point
# 0.44 below them.
SIZE = 4.5
CENTRE_Y = 1.5

# Whether the effect being built tests against the scene's depth. The "_nz" copies do not: the
# game plays those on your own character in third person, as DragonMineZ draws your own aura
# (AuraRenderer.applyAndDraw, ignoreSceneDepth), because charge rocks, dust and every other
# particle write depth and cut their outline out of an aura drawn after them (2026-10-02 owner,
# with a screenshot: "rocks are making the aura disspear").
DEPTH = True


def depth():
    """Renderer values for the copy being built: nothing, or the depth test switched off."""
    return None if DEPTH else {'ZTest': 'False'}


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    for name, seed in zip(FRAMES, (501, 511, 521, 531)):
        tx.spiked_flame(tex / name, seed)
    tx.flame_tongue(tex / 'aura2_tongue.png', 541)
    tx.mote(tex / 'aura2_ember.png', core_width=0.14)
    tx.glow(tex / 'aura2_glow.png')


def silhouette(index, c, dark, blend):
    """One frame of the spiked flame, shown again and again a third of a beat after the last."""
    # Stay on the form colour. hot() mixed 15% white and flattened blues into a pale wall
    # (2026-10-02 owner: "blue forms aura are fucking white").
    rim = v1.mix(c, v1.WHITE, 0.18) if dark else v1.mix(c, v1.WHITE, 0.08)
    body = v1.mix(c, v1.WHITE, 0.06) if dark else c
    r, d = sprite(T + FRAMES[index], blend, billboard=1, fade_in=4, fade_out=7, extra=depth(), color=easing_color(
        color_range(rim, v1.a(140), 6), color_range(body, v1.a(95), 6)))
    return node(f'Flame{index}', CommonValues=common(rng(15), v1.count(12), 12, delay=index * 3),
                LocationValues=pva_location(location={'Y': span(CENTRE_Y - 0.05, CENTRE_Y + 0.05)},
                                            velocity={'Y': span(0.002, 0.005)}),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-2, 2)}}},
                ScalingValues=scale_xyz_ease({'X': span(SIZE * 0.97, SIZE), 'Y': span(SIZE * 0.97, SIZE), 'Z': rng(1)},
                                             {'X': span(SIZE * 1.0, SIZE * 1.03),
                                              'Y': span(SIZE * 1.02, SIZE * 1.05), 'Z': rng(1)}),
                RendererCommonValues=r, DrawingValues=d)


def licks(name, start, end, radius, height, size_start, size_end, every, life, rise, accel=None):
    """Flame-shaped tongues, billboarded upright, rising from the spawn band and fading out."""
    # Additive even on dark form colours: these are fire, not the silhouette.
    r, d = sprite(T + 'aura2_tongue.png', 2, billboard=1, fade_in=3, fade_out=10, extra=depth(), color=easing_color(
        color_range(start, v1.a(210), 8), color_range(end, 0, 8)))
    (x0, y0), (x1, y1) = size_start, size_end
    loc = {'Y': span(*height)}
    vel = {'Y': span(*rise)}
    acc = {'Y': span(*accel)} if accel else None
    return node(name, CommonValues=v1.emitter(span(*life), every),
                LocationValues=pva_location(location=loc, velocity=vel, accel=acc),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-8, 8)}}},
                ScalingValues=scale_xyz_ease({'X': span(*x0), 'Y': span(*y0), 'Z': rng(1)},
                                             {'X': span(*x1), 'Y': span(*y1), 'Z': rng(1)}),
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=r, DrawingValues=d)


def aura2(hexcode):
    c = v1.rgb_of(hexcode)
    dark = v1.luma(c) < 0.18
    # Pale / ice extras add to white under additive blending; draw them normally.
    blend = 1 if dark or v1.luma(c) >= 0.72 else 2
    parts = [silhouette(i, c, dark, blend) for i in range(len(FRAMES))]
    # Silhouette top is 3.61 blocks at size 1. Spawn at the feet and rise past that, then fade.
    # Sprite scale s is s across (quad +-0.5); these tongues are authored in blocks, not 2s.
    parts += [
        licks('FootLicks', IMPACT_HOT, IMPACT_COOL, (0.28, 0.58), (-0.2, 0.35),
              ((0.18, 0.28), (0.55, 0.9)), ((0.10, 0.16), (1.05, 1.5)),
              0.55, (22, 32), (0.09, 0.13), accel=(0.001, 0.003)),
        licks('RiseLicks', IMPACT_HOT, IMPACT_COOL, (0.7, 1.35), (-0.25, 0.45),
              ((0.28, 0.42), (0.85, 1.25)), ((0.14, 0.22), (1.55, 2.25)),
              0.8, (28, 40), (0.12, 0.17), accel=(0.002, 0.004)),
    ]
    er, ed = sprite(T + 'aura2_ember.png', 2, fade_in=2, fade_out=12, extra=depth(), color=easing_color(
        color_range(IMPACT_HOT, v1.a(255), 10), color_range(IMPACT_COOL, 0, 15)))
    parts.append(node('Embers', CommonValues=v1.emitter(span(36, 56), 2.5),
                      LocationValues=pva_location(location={'Y': span(-0.2, 3.6)},
                                                  velocity={'Y': span(0.04, 0.08)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      GenerationLocationValues=circle(0.45, 1.4),
                      RendererCommonValues=er, DrawingValues=ed))
    return node('Aura2', parts, CommonValues=common(v1.EMIT + 60), DrawingValues={'Type': 0}), []


def shipped_palette():
    """The colours variant 1 was last built for (aura/palette.txt, what the game reads): building
    for the same list keeps the two variants colour for colour, even if a form pack scanned since
    would add one."""
    listed = v1.REPO / 'src' / 'main' / 'resources' / 'assets' / 'xenopixelsmod' / 'effeks' / 'aura' / 'palette.txt'
    if listed.is_file():
        colours = [line.strip().upper() for line in listed.read_text(encoding='utf-8').splitlines() if line.strip()]
        if colours:
            return colours
    return list(v1.PALETTE)


def at(level, tested, hexcode):
    global DEPTH
    DEPTH = tested
    try:
        return v1.at_level(level, aura2, hexcode)
    finally:
        DEPTH = True


EFFECTS = {}
for _level in v1.LEVELS:
    for _h in shipped_palette():
        for _tested, _tag in ((True, ''), (False, '_nz')):
            EFFECTS[f'aura2_{_h.lower()}{_tag}{v1.suffix(_level)}'] = (
                lambda lv=_level, h=_h, t=_tested: at(lv, t, h))
PREVIEW = {name: 90 for name in EFFECTS}
