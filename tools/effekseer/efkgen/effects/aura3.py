"""HD aura, variant 3 (2026-10-03 owner): /xenoaura v3.

Builds on variant 2's spiked silhouette (form colour) and adds:
  - punch / ki-blast ToonHit Glow edges and rising tongues (RGB(255,92,32) -> (255,64,16))
    that start at the feet, pass the silhouette and vanish above the head (Jiren full power);
  - Sparking lightning, crackle, tongues and embers all in the form colour.

The v1 inner shell (aura_in_*) is played by the game beside this effect, not baked in, so
/xenoaura inner still picks a dimmer baked level.

One effect per colour and brightness level, aura3_<hex>[_nz][_bNN], same palette as v1/v2.
Sprite scale s is s across (quad +-0.5). Authored to DMZ's flame box like aura2.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, node, pva_location, random_color,
                       rng, scale_ease, scale_xyz_ease, span, sprite)
from . import aura as v1
from . import aura2 as v2

NAME = 'aura3'
FOLDER = 'aura3'

FRAMES = ('aura3_flame_a.png', 'aura3_flame_b.png', 'aura3_flame_c.png', 'aura3_flame_d.png')
TONGUE = 'aura3_tongue.png'
EMBER = 'aura3_ember.png'
BOLT_A = 'aura3_bolt_a.png'
BOLT_B = 'aura3_bolt_b.png'
SPARK_FLAME = 'aura3_spark_flame.png'
SPARK_EMBER = 'aura3_spark_ember.png'
SPARK_GLOW = 'aura3_spark_glow.png'

IMPACT_HOT = v2.IMPACT_HOT
IMPACT_COOL = v2.IMPACT_COOL
SIZE = v2.SIZE
CENTRE_Y = v2.CENTRE_Y
DEPTH = True


def depth():
    return None if DEPTH else {'ZTest': 'False'}


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    for name, seed in zip(FRAMES, (601, 611, 621, 631)):
        tx.spiked_flame(tex / name, seed)
    tx.flame_tongue(tex / TONGUE, 641)
    tx.mote(tex / EMBER, core_width=0.14)
    tx.glow(tex / SPARK_GLOW)
    tx.lightning(tex / BOLT_A, 651)
    tx.lightning(tex / BOLT_B, 657, branches=4)
    tx.streaks(tex / SPARK_FLAME, 661, streak_x=5.0, streak_y=0.6, gamma=1.4, bias=0.32)
    tx.mote(tex / SPARK_EMBER, core_width=0.14)


def silhouette(index, c, dark, blend):
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
    r, d = sprite(T + TONGUE, 2, billboard=1, fade_in=3, fade_out=10, extra=depth(), color=easing_color(
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


def bolts(name, texture, every, colour, radius=(0.35, 0.65), height=(0.2, 1.8), size=(0.25, 0.4)):
    br, bd = sprite(texture, 2, billboard=1, fade_out=2, extra=depth(),
                    color=random_color(colour, 255, 20))
    return node(name, CommonValues=common(span(2, 4), 'inf', every),
                LocationValues=pva_location(location={'Y': span(*height)}),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-25, 25)}}},
                ScalingValues={'Type': 0, 'Fixed': {'Scale': {
                    'X': (size[0] + size[1]) / 2,
                    'Y': (size[0] + size[1]) / 2 * 2.4,
                    'Z': 1}}},
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=br, DrawingValues=bd)


def sparking(c):
    """Form-coloured sparking overlay: thunders, crackle, tongues and embers all follow the form."""
    hot = v1.mix(c, v1.WHITE, 0.35)
    cool = v1.mix(c, IMPACT_COOL, 0.25) if v1.luma(c) > 0.2 else v1.mix(c, v1.WHITE, 0.15)
    bolt = v1.mix(c, v1.WHITE, 0.55)  # bright form lightning
    crackle_end = v1.mix(c, v1.WHITE, 0.15)
    parts = [
        bolts('FormBoltsA', T + BOLT_A, span(4.0, 8.0), bolt, radius=(0.35, 0.65)),
        bolts('FormBoltsB', T + BOLT_B, span(5.0, 9.0), hot, radius=(0.4, 0.7)),
    ]
    cr, cd = sprite(T + SPARK_EMBER, 2, billboard=4, fade_out=3, extra=depth(), color=easing_color(
        color_range(bolt, 255, 15), color_range(crackle_end, 0, 15)))
    parts.append(node('FormCrackle', CommonValues=common(span(4, 9), 'inf', 0.5),
                      LocationValues=pva_location(location={'Y': span(0.2, 1.8)},
                                                  velocity={'X': span(-0.07, 0.07), 'Y': span(-0.07, 0.07),
                                                            'Z': span(-0.07, 0.07)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.025, 0.04), 'Y': span(0.14, 0.24), 'Z': rng(1)},
                                                   {'X': rng(0.01), 'Y': span(0.05, 0.1), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.3, 0.65),
                      RendererCommonValues=cr, DrawingValues=cd))
    tr, td = sprite(T + SPARK_FLAME, 2, billboard=1, fade_in=2, fade_out=8, extra=depth(), color=easing_color(
        color_range(hot, v1.a(220), 10), color_range(cool, 0, 15)))
    parts.append(node('SparkTongues', CommonValues=common(span(14, 22), 'inf', 1.2),
                      LocationValues=pva_location(location={'Y': span(0.0, 0.6)},
                                                  velocity={'Y': span(0.03, 0.055)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.18, 0.28), 'Y': span(0.4, 0.55), 'Z': rng(1)},
                                                   {'X': span(0.07, 0.11), 'Y': span(0.85, 1.15), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.35, 0.6),
                      RendererCommonValues=tr, DrawingValues=td))
    er, ed = sprite(T + SPARK_EMBER, 2, fade_in=2, fade_out=12, extra=depth(), color=easing_color(
        color_range(hot, v1.a(255), 10), color_range(cool, 0, 20)))
    parts.append(node('SparkEmbers', CommonValues=common(span(30, 50), 'inf', 1.2),
                      LocationValues=pva_location(location={'Y': span(0.0, 2.8)},
                                                  velocity={'Y': span(0.02, 0.05)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      GenerationLocationValues=circle(0.3, 0.9),
                      RendererCommonValues=er, DrawingValues=ed))
    gr, gd = sprite(T + SPARK_GLOW, 2, fade_in=12, fade_out=16, extra=depth(), color=easing_color(
        color_range(c, v1.a(55), 0), color_range(c, v1.a(40), 0)))
    parts.append(node('SparkGlow', CommonValues=common(v1.EMIT + 14, 1),
                      LocationValues=pva_location(location={'Y': rng(1.0)}),
                      ScalingValues=scale_ease(rng(2.6), rng(3.0)),
                      RendererCommonValues=gr, DrawingValues=gd))
    return parts


def aura3(hexcode):
    c = v1.rgb_of(hexcode)
    dark = v1.luma(c) < 0.18
    blend = 1 if dark or v1.luma(c) >= 0.72 else 2
    parts = [silhouette(i, c, dark, blend) for i in range(len(FRAMES))]
    # Punch / ki-blast edges and rising flames (Jiren): feet -> past silhouette top (3.61) -> fade.
    parts += [
        licks('EdgeLicks', IMPACT_HOT, IMPACT_COOL, (1.15, 1.55), (0.1, 3.0),
              ((0.16, 0.26), (0.55, 0.9)), ((0.08, 0.14), (1.1, 1.6)),
              0.7, (20, 30), (0.08, 0.12), accel=(0.001, 0.003)),
        licks('FootLicks', IMPACT_HOT, IMPACT_COOL, (0.28, 0.58), (-0.2, 0.35),
              ((0.18, 0.28), (0.55, 0.9)), ((0.10, 0.16), (1.05, 1.5)),
              0.55, (22, 32), (0.09, 0.13), accel=(0.001, 0.003)),
        licks('RiseLicks', IMPACT_HOT, IMPACT_COOL, (0.7, 1.35), (-0.25, 0.45),
              ((0.28, 0.42), (0.85, 1.25)), ((0.14, 0.22), (1.55, 2.25)),
              0.8, (28, 40), (0.12, 0.17), accel=(0.002, 0.004)),
        licks('CrownLicks', IMPACT_HOT, IMPACT_COOL, (0.35, 0.95), (2.6, 3.4),
              ((0.22, 0.34), (0.7, 1.05)), ((0.10, 0.16), (1.4, 2.0)),
              0.9, (24, 34), (0.10, 0.15), accel=(0.001, 0.003)),
    ]
    er, ed = sprite(T + EMBER, 2, fade_in=2, fade_out=12, extra=depth(), color=easing_color(
        color_range(IMPACT_HOT, v1.a(255), 10), color_range(IMPACT_COOL, 0, 15)))
    parts.append(node('Embers', CommonValues=v1.emitter(span(36, 56), 2.5),
                      LocationValues=pva_location(location={'Y': span(-0.2, 3.8)},
                                                  velocity={'Y': span(0.04, 0.09)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      GenerationLocationValues=circle(0.45, 1.4),
                      RendererCommonValues=er, DrawingValues=ed))
    parts += sparking(c)
    return node('Aura3', parts, CommonValues=common(v1.EMIT + 60), DrawingValues={'Type': 0}), []


def shipped_palette():
    return v2.shipped_palette()


def at(level, tested, hexcode):
    global DEPTH
    DEPTH = tested
    try:
        return v1.at_level(level, aura3, hexcode)
    finally:
        DEPTH = True


EFFECTS = {}
for _level in v1.LEVELS:
    for _h in shipped_palette():
        for _tested, _tag in ((True, ''), (False, '_nz')):
            EFFECTS[f'aura3_{_h.lower()}{_tag}{v1.suffix(_level)}'] = (
                lambda lv=_level, h=_h, t=_tested: at(lv, t, h))
PREVIEW = {name: 90 for name in EFFECTS}
