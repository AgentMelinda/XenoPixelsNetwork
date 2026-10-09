"""Missile warhead explosion, after the owner's reference (tools/new_particles/
on_explostion_of_missile_particle_iwant.png): a white-hot flash and orange fireball at the centre,
a lumpy grey smoke cloud, orange flame rays and hundreds of streaking sparks and glowing embers
(white, yellow, orange, red) thrown out in every direction, arcing down as they cool.

Centred on the origin and upright (the game sends it unrotated). Radius about 3 blocks at scale 1;
the game scales it by blast power (MissileEffectRules.explosionScale, 0.5 to 6).

Motion is radial because every particle starts at the centre with a random velocity direction.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, color_range, common, easing_color, node, pva_location, random_color, rng,
                       scale_ease, scale_single, scale_xyz_ease, span, sphere, spin, sprite, turbulence)

NAME = 'explosion'

WHITE_HOT = (255, 250, 236)
YELLOW = (255, 216, 120)
ORANGE = (255, 142, 40)
RED = (230, 58, 24)
DARK_RED = (120, 22, 10)
SMOKE_LIGHT = (176, 172, 170)
SMOKE_DARK = (86, 82, 80)


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.glow(tex / 'blast_glow.png')
    tx.smoke_puff(tex / 'blast_smoke.png', 201)
    tx.smoke_puff(tex / 'blast_fire.png', 211)
    tx.mote(tex / 'blast_ember.png', core_width=0.2)
    tx.shock_diamond(tex / 'blast_streak.png')
    tx.streaks(tex / 'blast_ray.png', 221, w=128, h=256, streak_x=3.0, streak_y=0.4, gamma=1.2, bias=0.25)


def radial(v_lo, v_hi):
    """A random direction at a random speed: from the centre that is straight outward."""
    return {'X': span(-v_hi, v_hi), 'Y': span(-v_hi, v_hi), 'Z': span(-v_hi, v_hi)}


def missile_explosion():
    parts = []
    # White-hot flash, then the fireball swelling and cooling to red.
    fr, fd = sprite(T + 'blast_glow.png', 2, fade_out=10, color=easing_color(
        color_range(WHITE_HOT, 255), color_range(ORANGE, 0)))
    parts.append(node('Flash', CommonValues=common(14),
                      ScalingValues=scale_ease(rng(1.5), rng(7.0)),
                      RendererCommonValues=fr, DrawingValues=fd))
    br, bd = sprite(T + 'blast_fire.png', 2, fade_in=1, fade_out=14, color=easing_color(
        color_range(YELLOW, 240, 15), color_range(DARK_RED, 0, 10)))
    parts.append(node('Fireball', CommonValues=common(span(18, 30), 22, 0.3),
                      LocationValues=pva_location(velocity=radial(0.0, 0.05), accel={'Y': rng(0.0008)}),
                      RotationValues=spin((-180, 180), (-3, 3)),
                      ScalingValues=scale_ease(span(0.8, 1.2), span(2.2, 3.0)),
                      GenerationLocationValues=sphere(0.0, 0.4),
                      RendererCommonValues=br, DrawingValues=bd))
    # Orange flame rays shooting out: long directional streaks along their motion.
    rr, rd = sprite(T + 'blast_ray.png', 2, billboard=4, fade_in=1, fade_out=10, color=easing_color(
        color_range(YELLOW, 230, 15), color_range(RED, 0, 15)))
    parts.append(node('Rays', CommonValues=common(span(10, 20), 36, 0.2),
                      LocationValues=pva_location(velocity=radial(0.08, 0.2)),
                      ScalingValues=scale_xyz_ease({'X': span(0.25, 0.4), 'Y': span(1.2, 2.0), 'Z': rng(1)},
                                                   {'X': span(0.1, 0.18), 'Y': span(2.4, 3.4), 'Z': rng(1)}),
                      RendererCommonValues=rr, DrawingValues=rd))
    # Sparks: fast, streaking (directional), slowing and falling as they cool.
    sr, sd = sprite(T + 'blast_streak.png', 2, billboard=4, fade_out=12, color=easing_color(
        color_range(WHITE_HOT, 255, 5), color_range(RED, 0, 20)))
    parts.append(node('Sparks', CommonValues=common(span(25, 50), 160, 0.08),
                      LocationValues=pva_location(velocity=radial(0.12, 0.32),
                                                  accel={'Y': rng(-0.0035)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.06, 0.1), 'Y': span(0.4, 0.7), 'Z': rng(1)},
                                                   {'X': span(0.02, 0.04), 'Y': span(0.15, 0.3), 'Z': rng(1)}),
                      LocationAbsValues=turbulence(0.02, 3, 231),
                      RendererCommonValues=sr, DrawingValues=sd))
    # Embers: round glowing bits in every colour of the flame, flung wide and arcing down.
    for i, (colour, count) in enumerate(((WHITE_HOT, 60), (YELLOW, 70), (ORANGE, 70), (RED, 60))):
        er, ed = sprite(T + 'blast_ember.png', 2, fade_out=20, color=random_color(colour, 255, 18))
        parts.append(node(f'Embers{i + 1}', CommonValues=common(span(40, 90), count, 0.12),
                          LocationValues=pva_location(velocity=radial(0.05, 0.22),
                                                      accel={'Y': rng(-0.0022)}),
                          ScalingValues=scale_ease(span(0.06, 0.16), span(0.02, 0.05)),
                          LocationAbsValues=turbulence(0.03, 4, 241 + i),
                          RendererCommonValues=er, DrawingValues=ed))
    # The smoke cloud: grey, lumpy, billowing out and rising slowly; outlives everything else.
    mr, md = sprite(T + 'blast_smoke.png', 1, fade_in=6, fade_out=50, color=easing_color(
        color_range(SMOKE_LIGHT, 220, 12), color_range(SMOKE_DARK, 0, 8)))
    parts.append(node('Smoke', CommonValues=common(span(90, 150), 45, 0.25),
                      LocationValues=pva_location(velocity=radial(0.0, 0.045),
                                                  accel={'Y': rng(0.0004)}),
                      RotationValues=spin((-180, 180), (-1, 1)),
                      ScalingValues=scale_ease(span(0.9, 1.4), span(2.4, 3.4)),
                      LocationAbsValues=turbulence(0.02, 5, 251),
                      GenerationLocationValues=sphere(0.0, 0.8),
                      RendererCommonValues=mr, DrawingValues=md))
    # Glow the blast throws on its surroundings.
    gr, gd = sprite(T + 'blast_glow.png', 2, fade_in=2, fade_out=30, color=random_color(ORANGE, 140, 15))
    parts.append(node('Glow', CommonValues=common(40),
                      ScalingValues=scale_single(9.0),
                      RendererCommonValues=gr, DrawingValues=gd))
    return node('Explosion', parts, CommonValues=common(160), DrawingValues={'Type': 0}), []


EFFECTS = {'missile_explosion': missile_explosion}
PREVIEW = {'missile_explosion': 160}
PREVIEW_VIEW = {'missile_explosion': {'zoom': 7, 'frames': 3, 'gap_ms': 300, 'start_ms': 150}}
