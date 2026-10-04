"""HD aura, variant 4 (2026-10-04 owner): /xenoaura v4, the default.

v3 stacked the dense v1 outer billow (AuraOuter: OuterA/OuterB/Edge/Mid fire_billow puffs)
under the spiked silhouette. That column reads as a smoke plume and fills the screen with
large additive quads — client FPS drops in built areas.

v4 keeps v3's silhouette (played by the game from aura3/) and the v1 inner shell (aura_in_*),
and replaces aura_out_* with this cheaper plume that still reads as the v1 smoke wall:
  - OuterA + Edge puffs (the plume), normal blend, ~2x slower spawn, ~25% smaller sprites
  - one Mid layer, smaller
  - tongues and fibres at about half the v1 rate
  - additive embers only
  - no OuterB (second overlapping column) / DustRing (large ground quad)

Do not mutate aura.py. Same 10-tick / EMIT=30 contract. Authored as a feet-up column like v1;
the game applies the v1 box/drop. _nz copies skip Z-test for your own third-person overlay.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, node, pva_location, rng,
                       scale_ease, scale_xyz_ease, span, spin, sprite)
from . import aura as v1
from . import aura2 as v2

NAME = 'aura4'
FOLDER = 'aura4'

BILLOW = 'aura4_billow.png'
TONGUE = 'aura4_tongue.png'
EMBER = 'aura4_ember.png'
DEPTH = True


def depth():
    return None if DEPTH else {'ZTest': 'False'}


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.fire_billow(tex / BILLOW, 401)
    tx.flame_tongue(tex / TONGUE, 331)
    tx.mote(tex / EMBER, core_width=0.14)


def puffs(name, colours, radius, height, size, every, life=(28, 40), rise=(0.01, 0.02),
          alpha=(90, 0)):
    start, end = colours
    r, d = sprite(T + BILLOW, 1, fade_in=8, fade_out=16, extra=depth(), color=easing_color(
        color_range(start, v1.a(alpha[0]), 14), color_range(end, alpha[1], 12)))
    return node(name, CommonValues=v1.emitter(span(*life), every),
                LocationValues=pva_location(location={'Y': span(*height)},
                                            velocity={'Y': span(*rise)}),
                RotationValues=spin((-180, 180), (-1.2, 1.2)),
                ScalingValues=scale_ease(span(size[0], size[1]), span(size[1] * 1.2, size[1] * 1.4)),
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=r, DrawingValues=d)


def tongues(name, colours, radius, height, size_start, size_end, every, life=(16, 26),
            rise=(0.03, 0.05)):
    start, end = colours
    r, d = sprite(T + TONGUE, 2, billboard=1, fade_in=3, fade_out=8, extra=depth(), color=easing_color(
        color_range(start, v1.a(200), 12), color_range(end, 0, 10)))
    (x0, y0), (x1, y1) = size_start, size_end
    return node(name, CommonValues=v1.emitter(span(*life), every),
                LocationValues=pva_location(location={'Y': span(*height)}, velocity={'Y': span(*rise)}),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-12, 12)}}},
                ScalingValues=scale_xyz_ease({'X': span(*x0), 'Y': span(*y0), 'Z': rng(1)},
                                             {'X': span(*x1), 'Y': span(*y1), 'Z': rng(1)}),
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=r, DrawingValues=d)


def aura4(hexcode):
    c = v1.rgb_of(hexcode)
    dark = v1.luma(c) < 0.18
    body = v1.mix(c, v1.WHITE, 0.15) if dark else v1.vivid(c)
    rim = v1.mix(c, v1.WHITE, 0.4) if dark else v1.deep(c)
    parts = [
        # The v1 smoke wall: one OuterA (v1 also stacked OuterB on top of this).
        puffs('OuterA', (body, rim), (0.95, 1.35), (0.0, 3.3),
              (0.9, 1.25), 1.0, life=(28, 42), alpha=(110, 0)),
        # Ragged rim so the column still reads as a plume, not a tube.
        puffs('Edge', (v1.deep(c) if not dark else rim, rim), (1.25, 1.55), (0.1, 3.4),
              (0.65, 0.9), 1.4, life=(28, 40), alpha=(100, 0)),
        puffs('Mid', (v1.mix(body, v1.hot(c), 0.6), body), (0.7, 1.05), (0.0, 2.8),
              (0.55, 0.8), 1.2, alpha=(90, 0)),
        tongues('EdgePoints', (body, rim), (1.05, 1.4), (0.2, 2.8),
                ((0.45, 0.7), (0.7, 1.05)), ((0.3, 0.45), (1.05, 1.45)),
                1.0, life=(14, 22), rise=(0.03, 0.05)),
        tongues('CrownPoints', (v1.mix(body, v1.hot(c), 0.3), rim), (0.2, 1.0), (2.2, 3.2),
                ((0.7, 1.0), (1.1, 1.55)), ((0.45, 0.7), (1.55, 2.1)),
                1.2, life=(16, 26), rise=(0.03, 0.05)),
    ]
    fr, fd = sprite(T + TONGUE, 2, billboard=1, fade_in=2, fade_out=8, extra=depth(), color=easing_color(
        color_range(v1.hot(c), v1.a(220), 10), color_range(body, 0, 10)))
    parts.append(node('Fibres', CommonValues=v1.emitter(span(14, 24), 2.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 2.4)},
                                                  velocity={'Y': span(0.05, 0.09)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.1, 0.18), 'Y': span(0.7, 1.05), 'Z': rng(1)},
                                                   {'X': span(0.04, 0.08), 'Y': span(1.15, 1.7), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.6, 1.1),
                      RendererCommonValues=fr, DrawingValues=fd))
    er, ed = sprite(T + EMBER, 2, fade_in=2, fade_out=12, extra=depth(), color=easing_color(
        color_range(v1.hot(c), v1.a(255), 10), color_range(body, 0, 15)))
    parts.append(node('Embers', CommonValues=v1.emitter(span(36, 52), 4.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 3.2)},
                                                  velocity={'Y': span(0.015, 0.035)}),
                      ScalingValues=scale_ease(span(0.04, 0.07), rng(0.0)),
                      GenerationLocationValues=circle(0.55, 1.4),
                      RendererCommonValues=er, DrawingValues=ed))
    return node('Aura4', parts, CommonValues=common(v1.EMIT + 60), DrawingValues={'Type': 0}), []


def shipped_palette():
    return v2.shipped_palette()


def at(level, tested, hexcode):
    global DEPTH
    DEPTH = tested
    try:
        return v1.at_level(level, aura4, hexcode)
    finally:
        DEPTH = True


EFFECTS = {}
for _level in v1.LEVELS:
    for _h in shipped_palette():
        for _tested, _tag in ((True, ''), (False, '_nz')):
            EFFECTS[f'aura4_{_h.lower()}{_tag}{v1.suffix(_level)}'] = (
                lambda lv=_level, h=_h, t=_tested: at(lv, t, h))
PREVIEW = {name: 90 for name in EFFECTS}
