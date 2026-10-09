"""Hakai (Dragon Ball Super, Beerus erasing Zamasu): a purple veil clings to the victim, the body
crumbles into violet flakes from the head down, the flakes drift upward. No explosion. The caster's
palm carries a small purple flame. Sized for a 1.8-block body; the game scales by body height.

  hakai_channel  at the feet every pulse: the veil, its flowing strands, rising motes.
  hakai_crumble  at the dissolve line: flakes and dust breaking off, and a glowing edge.
  hakai_erase    at the body centre when the body is gone: soft flash, the last flakes.
  hakai_palm     at the caster's hand: a flickering purple flame and sparks.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, fixed_color, lathe, model, node,
                       pva_location, random_color, rng, scale_ease, span, sphere, spin, sprite,
                       turbulence)

NAME = 'hakai'

# Palette (RGB 0-255): deep violet body, bright violet energy, lilac-white highlights.
DEEP = (70, 8, 120)
VIOLET = (165, 55, 255)
MAGENTA = (225, 90, 255)
LILAC = (245, 215, 255)
EDGE = (255, 120, 255)   # the crackling rim where the veil's energy is thinnest
SHARD = (255, 170, 255)  # a flake as it tears away, before it cools to violet


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.streaks(tex / 'hakai_wisp.png', 11)
    tx.mask(tex / 'hakai_mask.png', 21)
    tx.distortion(tex / 'hakai_distort.png', 31)
    tx.mote(tex / 'hakai_mote.png')
    tx.shard(tex / 'hakai_flake.png', 41)
    tx.glow(tex / 'hakai_glow.png')
    tx.ring(tex / 'hakai_ring.png', 51)


def hakai_channel():
    """The veil: dark violet coating, bright violet energy scrolling up, strands and motes."""
    life = 48  # frames; the game re-plays it every half second, so pulses cross-fade.
    veil_points = [(0.62, 0.0), (0.40, 0.45), (0.40, 1.55), (0.22, 2.25)]
    strand_points = [(0.85, 0.0), (0.50, 0.6), (0.48, 1.7), (0.62, 2.55)]
    body = [
        node('VeilDark', CommonValues=common(life),
             **model(0, DEEP + (170,), 1, T + 'hakai_wisp.png', 12, 16, (256, 512, 0.6, -5),
                     alpha_tex=T + 'hakai_mask.png', distort=(T + 'hakai_distort.png', 0.06))),
        node('VeilEnergy', CommonValues=common(life),
             **model(0, VIOLET, 2, T + 'hakai_wisp.png', 10, 16, (256, 512, -0.8, -7),
                     distort=(T + 'hakai_distort.png', 0.1),
                     cutoff=(0.22, 0.07, EDGE))),
        node('Strands', CommonValues=common(life),
             **model(1, MAGENTA + (200,), 2, T + 'hakai_wisp.png', 14, 18, (256, 512, 1.2, -10),
                     alpha_tex=T + 'hakai_mask.png')),
    ]
    motes_r, motes_d = sprite(T + 'hakai_mote.png', 2, fade_in=4, fade_out=14, color=easing_color(
        color_range(LILAC, 255, 10), color_range(VIOLET, 0, 20)))
    motes = node('Motes',
                 CommonValues=common(span(30, 46), 'inf', 1.2),
                 LocationValues=pva_location(location={'Y': span(0.0, 1.9)},
                                             velocity={'Y': span(0.008, 0.02)},
                                             accel={'Y': rng(0.0002)}),
                 ScalingValues=scale_ease(span(0.05, 0.12), rng(0.0)),
                 LocationAbsValues=turbulence(0.03, 5),
                 GenerationLocationValues=circle(0.30, 0.55),
                 RendererCommonValues=motes_r, DrawingValues=motes_d)
    glow_r, glow_d = sprite(T + 'hakai_glow.png', 2, fade_in=12, fade_out=18,
                            color=fixed_color(VIOLET, 70))
    glow = node('Glow', CommonValues=common(life),
                LocationValues=pva_location(location={'Y': rng(0.95)}),
                ScalingValues={'Type': 3, 'SinglePVA': {'Scale': rng(2.4)}},
                RendererCommonValues=glow_r, DrawingValues=glow_d)
    root = node('Hakai', body + [motes, glow], CommonValues=common(life), DrawingValues={'Type': 0})
    return root, [lathe('Veil', veil_points), lathe('Strands', strand_points, mesh_type=1, ribbon_count=6, twist=0)]


def hakai_crumble():
    """Flakes and dust breaking off the body at the dissolve line, and the line's glowing edge."""
    flake_r, flake_d = sprite(T + 'hakai_flake.png', 2, fade_in=3, fade_out=24, color=easing_color(
        color_range(SHARD, 255, 12), color_range(VIOLET, 0, 15)))
    flakes = node('Flakes',
                  CommonValues=common(span(45, 75), 26, 0.6),
                  LocationValues=pva_location(location={'Y': span(-0.06, 0.06)},
                                              velocity={'X': span(0.001, 0.006), 'Y': span(0.006, 0.018)},
                                              accel={'Y': rng(0.00025)}),
                  RotationValues=spin(),
                  ScalingValues=scale_ease(span(0.06, 0.13), rng(0.01)),
                  LocationAbsValues=turbulence(0.02, 6, 3),
                  GenerationLocationValues=dict(circle(0.12, 0.32), EffectsRotation='True'),
                  RendererCommonValues=flake_r, DrawingValues=flake_d)
    dust_r, dust_d = sprite(T + 'hakai_mote.png', 2, fade_in=2, fade_out=20, color=easing_color(
        color_range(MAGENTA, 230, 20), color_range(VIOLET, 0, 20)))
    dust = node('Dust',
                CommonValues=common(span(30, 60), 40, 0.4),
                LocationValues=pva_location(location={'Y': span(-0.05, 0.05)},
                                            velocity={'X': span(0.0, 0.008), 'Y': span(0.01, 0.026)}),
                ScalingValues=scale_ease(span(0.025, 0.06), rng(0.0)),
                LocationAbsValues=turbulence(0.04, 4, 5),
                GenerationLocationValues=dict(circle(0.10, 0.34), EffectsRotation='True'),
                RendererCommonValues=dust_r, DrawingValues=dust_d)
    edge_r, edge_d = sprite(T + 'hakai_ring.png', 2, billboard=2, fade_in=4, fade_out=14,
                            color=fixed_color(MAGENTA, 200))
    edge = node('Edge', CommonValues=common(24),
                RotationValues={'Type': 0, 'Fixed': {'Rotation': {'X': 90}}},
                ScalingValues=scale_ease(rng(0.55), rng(0.8)),
                RendererCommonValues=edge_r, DrawingValues=edge_d)
    return node('Crumble', [flakes, dust, edge], CommonValues=common(80), DrawingValues={'Type': 0}), []


def hakai_erase():
    """The body is gone: a soft violet flash and the last flakes and wisps drifting away."""
    flash_r, flash_d = sprite(T + 'hakai_glow.png', 2, fade_out=14, color=easing_color(
        color_range(LILAC, 230), color_range(VIOLET, 0)))
    flash = node('Flash', CommonValues=common(18),
                 ScalingValues=scale_ease(rng(0.8), rng(2.8)),
                 RendererCommonValues=flash_r, DrawingValues=flash_d)
    flake_r, flake_d = sprite(T + 'hakai_flake.png', 2, fade_in=2, fade_out=40, color=easing_color(
        color_range(SHARD, 255, 12), color_range(VIOLET, 0, 15)))
    flakes = node('Flakes',
                  CommonValues=common(span(60, 110), 70, 0.35),
                  LocationValues=pva_location(location={'Y': span(-0.85, 0.85)},
                                              velocity={'X': span(0.002, 0.01), 'Y': span(0.008, 0.03)},
                                              accel={'Y': rng(0.0003)}),
                  RotationValues=spin(),
                  ScalingValues=scale_ease(span(0.06, 0.14), rng(0.01)),
                  LocationAbsValues=turbulence(0.03, 6, 7),
                  GenerationLocationValues=dict(circle(0.05, 0.3), EffectsRotation='True'),
                  RendererCommonValues=flake_r, DrawingValues=flake_d)
    mote_r, mote_d = sprite(T + 'hakai_mote.png', 2, fade_in=3, fade_out=40, color=easing_color(
        color_range(MAGENTA, 240, 20), color_range(VIOLET, 0, 20)))
    motes = node('Motes',
                 CommonValues=common(span(70, 130), 90, 0.3),
                 LocationValues=pva_location(location={'Y': span(-0.9, 0.9)},
                                             velocity={'X': span(0.0, 0.012), 'Y': span(0.01, 0.035)}),
                 ScalingValues=scale_ease(span(0.03, 0.07), rng(0.0)),
                 LocationAbsValues=turbulence(0.05, 4, 9),
                 GenerationLocationValues=dict(circle(0.05, 0.35), EffectsRotation='True'),
                 RendererCommonValues=mote_r, DrawingValues=mote_d)
    wisp_r, wisp_d = sprite(T + 'hakai_wisp.png', 2, billboard=1, fade_in=6, fade_out=26,
                            color=fixed_color(VIOLET, 150))
    wisps = node('Wisps',
                 CommonValues=common(span(40, 55), 8, 1.5),
                 LocationValues=pva_location(location={'Y': span(-0.6, 0.4)},
                                             velocity={'Y': span(0.015, 0.03)}),
                 ScalingValues={'Type': 2, 'Easing': {
                     'Start': {'X': span(0.25, 0.35), 'Y': span(0.6, 0.8), 'Z': rng(1)},
                     'End': {'X': span(0.1, 0.15), 'Y': span(1.2, 1.6), 'Z': rng(1)}}},
                 GenerationLocationValues=circle(0.1, 0.3),
                 RendererCommonValues=wisp_r, DrawingValues=wisp_d)
    return node('Erase', [flash, flakes, motes, wisps], CommonValues=common(140),
                DrawingValues={'Type': 0}), []


def hakai_palm():
    """The caster's raised palm: a flickering purple flame with a dark core and rising sparks."""
    life = 48
    flame_r, flame_d = sprite(T + 'hakai_glow.png', 2, fade_in=4, fade_out=6,
                              color=random_color(MAGENTA, 210, 25))
    flame = node('Flame', CommonValues=common(span(6, 9), 'inf', 2, ),
                 ScalingValues={'Type': 3, 'SinglePVA': {'Scale': span(0.42, 0.6)}},
                 RendererCommonValues=flame_r, DrawingValues=flame_d)
    core_r, core_d = sprite(T + 'hakai_glow.png', 1, fade_in=6, fade_out=10,
                            color=fixed_color(DEEP, 200))
    core = node('Core', CommonValues=common(life),
                ScalingValues={'Type': 3, 'SinglePVA': {'Scale': rng(0.26)}},
                RendererCommonValues=core_r, DrawingValues=core_d)
    tongue_r, tongue_d = sprite(T + 'hakai_wisp.png', 2, billboard=1, fade_in=3, fade_out=8,
                                color=fixed_color(VIOLET, 170))
    tongue = node('Tongue', CommonValues=common(span(10, 16), 'inf', 3),
                  LocationValues=pva_location(location={'Y': rng(0.08)}, velocity={'Y': span(0.004, 0.01)}),
                  ScalingValues={'Type': 0, 'Fixed': {'Scale': {'X': 0.24, 'Y': 0.5, 'Z': 1}}},
                  RendererCommonValues=tongue_r, DrawingValues=tongue_d)
    spark_r, spark_d = sprite(T + 'hakai_mote.png', 2, fade_out=10, color=easing_color(
        color_range(LILAC, 255), color_range(VIOLET, 0)))
    sparks = node('Sparks', CommonValues=common(span(18, 28), 'inf', 2),
                  LocationValues=pva_location(velocity={'X': span(-0.004, 0.004), 'Y': span(0.006, 0.016),
                                                        'Z': span(-0.004, 0.004)}),
                  ScalingValues=scale_ease(span(0.025, 0.045), rng(0.0)),
                  LocationAbsValues=turbulence(0.03, 3, 11),
                  GenerationLocationValues=sphere(0.02, 0.12),
                  RendererCommonValues=spark_r, DrawingValues=spark_d)
    return node('Palm', [core, flame, tongue, sparks], CommonValues=common(life),
                DrawingValues={'Type': 0}), []


EFFECTS = {'hakai_channel': hakai_channel, 'hakai_crumble': hakai_crumble,
           'hakai_erase': hakai_erase, 'hakai_palm': hakai_palm}
# Editor preview loop length (frames at 60 fps); the game plays each one-shot.
PREVIEW = {'hakai_channel': 48, 'hakai_crumble': 90, 'hakai_erase': 150, 'hakai_palm': 48}
