"""Sparking (Budokai Tenkaichi 3's "Sparking!" mode): a blazing gold aura with lightning crawling
over it, embers rising, and the ground giving way under the pressure. Authored upright, at the
feet, for a 1.8-block body; the game scales by body height.

  sparking_aura   re-sent every 10 ticks while Sparking (pulses cross-fade): gold ki veil and flame
                  strands, flame tongues, lightning bolts, embers, a dust ring and rock chips.
  sparking_burst  once when Sparking starts: a gold flash, a ground shockwave, a lightning burst,
                  a rock spray and a rolling dust cloud.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, fixed_color, lathe, model, node,
                       pva_location, random_color, rng, scale_ease, scale_single, scale_xyz_ease, span,
                       spin, sprite, turbulence)

NAME = 'sparking'

GOLD = (255, 196, 58)
WHITE_GOLD = (255, 246, 206)
ORANGE_GOLD = (255, 148, 32)
BOLT = (214, 232, 255)
# Budokai Tenkaichi 3's Sparking crackles blue electricity through the gold aura (2026-09-29 owner).
ELECTRIC_BLUE = (40, 205, 255)   # 2026-09-29 owner: more cyan (was 70, 150, 255)
ELECTRIC_CYAN = (160, 250, 255)  # was 150, 215, 255
DUST = (196, 176, 140)


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.streaks(tex / 'spark_flame.png', 101, streak_x=5.0, streak_y=0.6, gamma=1.4, bias=0.32)
    tx.mask(tex / 'spark_mask.png', 111, lo=0.22, span=0.5)
    tx.distortion(tex / 'spark_distort.png', 121)
    tx.lightning(tex / 'spark_bolt_a.png', 131)
    tx.lightning(tex / 'spark_bolt_b.png', 137, branches=4)
    tx.mote(tex / 'spark_ember.png', core_width=0.14)
    tx.glow(tex / 'spark_glow.png')
    tx.ring(tex / 'spark_ring.png', 141, radius=0.8, width=0.08, broken=0.5)
    tx.dust_ring(tex / 'spark_dust_ring.png', 151)
    tx.smoke_puff(tex / 'spark_dust.png', 161)
    tx.shard(tex / 'spark_rock.png', 171, lum_base=0.45, rim_gain=0.5, tint=(0.62, 0.55, 0.47))


def bolts(name, texture, every, count, life=(3, 5), radius=(0.35, 0.6), height=(0.3, 1.7),
          size=(0.3, 0.45), stretch=2.2, colour=BOLT):
    br, bd = sprite(texture, 2, billboard=1, fade_out=2, color=random_color(colour, 255, 20))
    return node(name, CommonValues=common(span(*life), count, every),
                LocationValues=pva_location(location={'Y': span(*height)}),
                RotationValues={'Type': 1, 'PVA': {'Rotation': {'Z': span(-25, 25)}}},
                ScalingValues={'Type': 0, 'Fixed': {'Scale': {'X': (size[0] + size[1]) / 2,
                                                              'Y': (size[0] + size[1]) / 2 * stretch, 'Z': 1}}},
                GenerationLocationValues=circle(*radius),
                RendererCommonValues=br, DrawingValues=bd)


def blue_electric(rate=1.0, flight=False, count='inf', life_scale=1.0):
    """Blue lightning and crackling blue sparks through the gold (BT3 Sparking).

    rate multiplies the spawn intervals (3 for the constant-sum aura, which overlaps three copies);
    flight puts them along the body lying on local Z (the flight aura) instead of standing on Y.
    """
    if flight:
        where = {'Z': span(-1.1, 0.8)}
        ring = circle(0.3, 0.6, axis=2)
    else:
        where = {'Y': span(0.2, 1.8)}
        ring = circle(0.3, 0.65)
    bolt = bolts('BlueBolts', T + 'spark_bolt_b.png', span(4.0 * rate, 8.0 * rate), count,
                 life=(2, 4), radius=(0.3, 0.65), size=(0.25, 0.4), stretch=2.4, colour=ELECTRIC_BLUE)
    bolt['LocationValues'] = pva_location(location=where)
    bolt['GenerationLocationValues'] = ring
    cr, cd = sprite(T + 'spark_ember.png', 2, billboard=4, fade_out=3, color=easing_color(
        color_range(ELECTRIC_CYAN, 255, 15), color_range(ELECTRIC_BLUE, 0, 15)))
    crackle = node('BlueCrackle', CommonValues=common(span(4, int(9 * life_scale)), count, 0.5 * rate),
                   LocationValues=pva_location(location=where,
                                               velocity={'X': span(-0.07, 0.07), 'Y': span(-0.07, 0.07),
                                                         'Z': span(-0.07, 0.07)}),
                   ScalingValues=scale_xyz_ease({'X': span(0.025, 0.04), 'Y': span(0.14, 0.24), 'Z': rng(1)},
                                                {'X': rng(0.01), 'Y': span(0.05, 0.1), 'Z': rng(1)}),
                   GenerationLocationValues=ring,
                   RendererCommonValues=cr, DrawingValues=cd)
    return [bolt, crackle]


# The game re-sends the aura every 10 ticks = 30 frames (AAA runs Effekseer at 60 frames a second).
# Each copy fades in over 30 frames, holds 30 and fades out over 30, so three copies overlap and
# their summed brightness is constant: no pulsing or popping at the handover. Each copy is drawn
# at half brightness and spawns at a third of the rate, since two to three are always showing.
AURA_PERIOD = 30


def sparking_aura():
    """The classic aura (the look the owner approved): one bright copy, re-sent every 10 ticks.
    Restored 2026-09-29 as the default after the constant-sum version looked washed out; that one
    is sparking_aura_smooth (/xenoset sparkingsmooth true)."""
    life = 48
    veil_points = [(0.72, 0.0), (0.48, 0.45), (0.5, 1.6), (0.3, 2.5)]
    strand_points = [(0.95, 0.0), (0.6, 0.6), (0.58, 1.8), (0.7, 2.8)]
    parts = [
        node('Veil', CommonValues=common(life),
             **model(0, GOLD + (220,), 2, T + 'spark_flame.png', 10, 16, (256, 512, -0.6, -12),
                     distort=(T + 'spark_distort.png', 0.12), cutoff=(0.2, 0.07, WHITE_GOLD))),
        node('InnerGlow', CommonValues=common(life),
             **model(0, WHITE_GOLD + (120,), 2, T + 'spark_flame.png', 12, 16, (256, 512, 0.8, -18),
                     alpha_tex=T + 'spark_mask.png')),
        node('Strands', CommonValues=common(life),
             **model(1, ORANGE_GOLD + (200,), 2, T + 'spark_flame.png', 12, 18, (256, 512, 1.2, -16),
                     alpha_tex=T + 'spark_mask.png')),
    ]
    tr, td = sprite(T + 'spark_flame.png', 2, billboard=1, fade_in=2, fade_out=8, color=easing_color(
        color_range(WHITE_GOLD, 230, 10), color_range(ORANGE_GOLD, 0, 15)))
    parts.append(node('Tongues', CommonValues=common(span(14, 22), 'inf', 1),
                      LocationValues=pva_location(location={'Y': span(0.0, 0.6)},
                                                  velocity={'Y': span(0.03, 0.055)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.22, 0.3), 'Y': span(0.45, 0.6), 'Z': rng(1)},
                                                   {'X': span(0.08, 0.12), 'Y': span(0.9, 1.2), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.35, 0.6),
                      RendererCommonValues=tr, DrawingValues=td))
    parts.append(bolts('BoltA', T + 'spark_bolt_a.png', span(5.0, 9.0), 'inf'))
    parts.append(bolts('BoltB', T + 'spark_bolt_b.png', span(6.0, 11.0), 'inf', radius=(0.4, 0.7)))
    parts += blue_electric()
    er, ed = sprite(T + 'spark_ember.png', 2, fade_in=2, fade_out=12, color=easing_color(
        color_range(WHITE_GOLD, 255, 10), color_range(ORANGE_GOLD, 0, 20)))
    parts.append(node('Embers', CommonValues=common(span(30, 50), 'inf', 1.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 1.6)},
                                                  velocity={'Y': span(0.012, 0.03)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      LocationAbsValues=turbulence(0.04, 4, 31),
                      GenerationLocationValues=circle(0.3, 0.75),
                      RendererCommonValues=er, DrawingValues=ed))
    dr, dd = sprite(T + 'spark_dust_ring.png', 1, billboard=2, fade_in=6, fade_out=20,
                    color=fixed_color(DUST, 150))
    parts.append(node('DustRing', CommonValues=common(40),
                      LocationValues=pva_location(location={'Y': rng(0.05)}),
                      RotationValues={'Type': 0, 'Fixed': {'Rotation': {'X': 90}}},
                      ScalingValues=scale_ease(rng(1.2), rng(2.2)),
                      RendererCommonValues=dr, DrawingValues=dd))
    rr, rd = sprite(T + 'spark_rock.png', 1, fade_out=10, color=fixed_color((255, 255, 255)))
    parts.append(node('Rocks', CommonValues=common(span(35, 55), 6, 6),
                      LocationValues=pva_location(location={'Y': rng(0.05)},
                                                  velocity={'X': span(-0.01, 0.01), 'Y': span(0.04, 0.075),
                                                            'Z': span(-0.01, 0.01)},
                                                  accel={'Y': rng(-0.0028)}),
                      RotationValues=spin((-180, 180), (-8, 8)),
                      ScalingValues=scale_single(span(0.06, 0.12)),
                      GenerationLocationValues=circle(0.3, 0.7),
                      RendererCommonValues=rr, DrawingValues=rd))
    gr, gd = sprite(T + 'spark_glow.png', 2, fade_in=12, fade_out=16, color=fixed_color(GOLD, 70))
    parts.append(node('Glow', CommonValues=common(life),
                      LocationValues=pva_location(location={'Y': rng(1.0)}),
                      ScalingValues=scale_single(3.0),
                      RendererCommonValues=gr, DrawingValues=gd))
    root = node('SparkingAura', parts, CommonValues=common(life), DrawingValues={'Type': 0})
    return root, [lathe('Veil', veil_points), lathe('Strands', strand_points, mesh_type=1, ribbon_count=7, twist=0)]


def sparking_aura_smooth():
    """Constant-sum version: steadier brightness, but flatter (optional)."""
    life = 3 * AURA_PERIOD
    fade_in = fade_out = AURA_PERIOD
    veil_points = [(0.72, 0.0), (0.48, 0.45), (0.5, 1.6), (0.3, 2.5)]
    strand_points = [(0.95, 0.0), (0.6, 0.6), (0.58, 1.8), (0.7, 2.8)]
    parts = [
        node('Veil', CommonValues=common(life),
             **model(0, GOLD + (110,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, -0.6, -12),
                     distort=(T + 'spark_distort.png', 0.12), cutoff=(0.2, 0.07, WHITE_GOLD))),
        node('InnerGlow', CommonValues=common(life),
             **model(0, WHITE_GOLD + (60,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, 0.8, -18),
                     alpha_tex=T + 'spark_mask.png')),
        node('Strands', CommonValues=common(life),
             **model(1, ORANGE_GOLD + (100,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, 1.2, -16),
                     alpha_tex=T + 'spark_mask.png')),
    ]
    tr, td = sprite(T + 'spark_flame.png', 2, billboard=1, fade_in=2, fade_out=8, color=easing_color(
        color_range(WHITE_GOLD, 230, 10), color_range(ORANGE_GOLD, 0, 15)))
    parts.append(node('Tongues', CommonValues=common(span(14, 22), 'inf', 3),
                      LocationValues=pva_location(location={'Y': span(0.0, 0.6)},
                                                  velocity={'Y': span(0.03, 0.055)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.22, 0.3), 'Y': span(0.45, 0.6), 'Z': rng(1)},
                                                   {'X': span(0.08, 0.12), 'Y': span(0.9, 1.2), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.35, 0.6),
                      RendererCommonValues=tr, DrawingValues=td))
    parts.append(bolts('BoltA', T + 'spark_bolt_a.png', span(15.0, 27.0), 'inf'))
    parts.append(bolts('BoltB', T + 'spark_bolt_b.png', span(18.0, 33.0), 'inf', radius=(0.4, 0.7)))
    parts += blue_electric(rate=3.0)
    er, ed = sprite(T + 'spark_ember.png', 2, fade_in=2, fade_out=12, color=easing_color(
        color_range(WHITE_GOLD, 255, 10), color_range(ORANGE_GOLD, 0, 20)))
    parts.append(node('Embers', CommonValues=common(span(30, 50), 'inf', 3.0),
                      LocationValues=pva_location(location={'Y': span(0.0, 1.6)},
                                                  velocity={'Y': span(0.012, 0.03)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      LocationAbsValues=turbulence(0.04, 4, 31),
                      GenerationLocationValues=circle(0.3, 0.75),
                      RendererCommonValues=er, DrawingValues=ed))
    dr, dd = sprite(T + 'spark_dust_ring.png', 1, billboard=2, fade_in=6, fade_out=20,
                    color=fixed_color(DUST, 150))
    parts.append(node('DustRing', CommonValues=common(40),
                      LocationValues=pva_location(location={'Y': rng(0.05)}),
                      RotationValues={'Type': 0, 'Fixed': {'Rotation': {'X': 90}}},
                      ScalingValues=scale_ease(rng(1.2), rng(2.2)),
                      RendererCommonValues=dr, DrawingValues=dd))
    rr, rd = sprite(T + 'spark_rock.png', 1, fade_out=10, color=fixed_color((255, 255, 255)))
    parts.append(node('Rocks', CommonValues=common(span(35, 55), 6, 6),
                      LocationValues=pva_location(location={'Y': rng(0.05)},
                                                  velocity={'X': span(-0.01, 0.01), 'Y': span(0.04, 0.075),
                                                            'Z': span(-0.01, 0.01)},
                                                  accel={'Y': rng(-0.0028)}),
                      RotationValues=spin((-180, 180), (-8, 8)),
                      ScalingValues=scale_single(span(0.06, 0.12)),
                      GenerationLocationValues=circle(0.3, 0.7),
                      RendererCommonValues=rr, DrawingValues=rd))
    gr, gd = sprite(T + 'spark_glow.png', 2, fade_in=fade_in, fade_out=fade_out, color=fixed_color(GOLD, 35))
    parts.append(node('Glow', CommonValues=common(life),
                      LocationValues=pva_location(location={'Y': rng(1.0)}),
                      ScalingValues=scale_single(3.0),
                      RendererCommonValues=gr, DrawingValues=gd))
    root = node('SparkingAura', parts, CommonValues=common(life), DrawingValues={'Type': 0})
    return root, [lathe('Veil', veil_points), lathe('Strands', strand_points, mesh_type=1, ribbon_count=7, twist=0)]


def sparking_burst():
    fr, fd = sprite(T + 'spark_glow.png', 2, fade_out=10, color=easing_color(
        color_range(WHITE_GOLD, 255), color_range(GOLD, 0)))
    flash = node('Flash', CommonValues=common(14),
                 LocationValues=pva_location(location={'Y': rng(1.0)}),
                 ScalingValues=scale_ease(rng(1.2), rng(5.0)),
                 RendererCommonValues=fr, DrawingValues=fd)
    wr, wd = sprite(T + 'spark_ring.png', 2, billboard=2, fade_out=14, color=easing_color(
        color_range(WHITE_GOLD, 255), color_range(ORANGE_GOLD, 0)))
    wave = node('Shockwave', CommonValues=common(26),
                LocationValues=pva_location(location={'Y': rng(0.08)}),
                RotationValues={'Type': 0, 'Fixed': {'Rotation': {'X': 90}}},
                ScalingValues=scale_ease(rng(0.6), rng(7.0)),
                RendererCommonValues=wr, DrawingValues=wd)
    burst_bolts = bolts('Bolts', T + 'spark_bolt_a.png', span(1.0, 2.5), 8, life=(4, 7),
                        radius=(0.4, 1.1), height=(0.2, 2.0), size=(0.4, 0.6), stretch=2.6)
    burst_bolts_b = bolts('BoltsB', T + 'spark_bolt_b.png', span(1.5, 3.0), 6, life=(4, 7),
                          radius=(0.5, 1.2), height=(0.2, 2.2), size=(0.4, 0.6), stretch=2.6)
    rr, rd = sprite(T + 'spark_rock.png', 1, fade_out=12, color=fixed_color((255, 255, 255)))
    rocks = node('Rocks', CommonValues=common(span(40, 70), 18, 0.5),
                 LocationValues=pva_location(location={'Y': rng(0.05)},
                                             velocity={'X': span(0.01, 0.04), 'Y': span(0.06, 0.12)},
                                             accel={'Y': rng(-0.003)}),
                 RotationValues=spin((-180, 180), (-10, 10)),
                 ScalingValues=scale_single(span(0.07, 0.15)),
                 GenerationLocationValues=dict(circle(0.3, 0.6), EffectsRotation='True'),
                 RendererCommonValues=rr, DrawingValues=rd)
    cr, cd = sprite(T + 'spark_dust.png', 1, fade_in=4, fade_out=30, color=easing_color(
        color_range(DUST, 170, 10), color_range(DUST, 0, 10)))
    cloud = node('DustCloud', CommonValues=common(span(50, 80), 24, 0.4),
                 LocationValues=pva_location(location={'Y': span(0.05, 0.3)},
                                             velocity={'X': span(0.03, 0.06), 'Y': span(0.0, 0.01)},
                                             accel={'X': rng(-0.0006)}),
                 RotationValues=spin((-180, 180), (-1, 1)),
                 ScalingValues=scale_ease(span(0.5, 0.7), span(1.4, 1.9)),
                 GenerationLocationValues=dict(circle(0.3, 0.5), EffectsRotation='True'),
                 RendererCommonValues=cr, DrawingValues=cd)
    return node('SparkingBurst', [flash, wave, burst_bolts, burst_bolts_b, rocks, cloud]
                + blue_electric(rate=0.5, count=60, life_scale=1.5),
                CommonValues=common(100), DrawingValues={'Type': 0}), []


RELEASE = {'LocationEffectType': 1, 'RotationEffectType': 1}  # stay in the world once born


def sparking_flight():
    """The aura while flying (DMZ fly): played bound to the look and anchored at the body's centre
    (0.9 behind the eyes), so local +Z is the flight direction, the head is at about +0.9 and the
    feet at -0.9; centred there, a larger scale grows it around the flyer. A comet of gold ki wrapping the
    body, streaks and embers peeling off backwards (released, so they trail), lightning along it."""
    # Same brightness and timing as the classic ground aura (the approved look).
    life = 48
    fade_in, fade_out = 10, 16
    body = rng(-1.5)  # the comet runs from past the feet (-1.5) to just ahead of the head (+1.15)
    parts = [
        node('Veil', CommonValues=common(life), LocationValues=pva_location(location={'Z': body}),
             **model(0, GOLD + (220,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, -0.6, 14),
                     distort=(T + 'spark_distort.png', 0.12), cutoff=(0.2, 0.07, WHITE_GOLD))),
        node('InnerGlow', CommonValues=common(life), LocationValues=pva_location(location={'Z': body}),
             **model(0, WHITE_GOLD + (120,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, 0.8, 20),
                     alpha_tex=T + 'spark_mask.png')),
        node('Strands', CommonValues=common(life), LocationValues=pva_location(location={'Z': body}),
             **model(1, ORANGE_GOLD + (200,), 2, T + 'spark_flame.png', fade_in, fade_out, (256, 512, 1.2, 18),
                     alpha_tex=T + 'spark_mask.png')),
    ]
    sr, sd = sprite(T + 'spark_flame.png', 2, billboard=4, fade_in=2, fade_out=8, color=easing_color(
        color_range(WHITE_GOLD, 220, 10), color_range(ORANGE_GOLD, 0, 15)))
    parts.append(node('Streaks', CommonValues=dict(common(span(12, 20), 'inf', 1), **RELEASE),
                      LocationValues=pva_location(location={'Z': span(-1.2, 0.8)},
                                                  velocity={'Z': span(-0.05, -0.025)}),
                      ScalingValues=scale_xyz_ease({'X': span(0.12, 0.18), 'Y': span(0.5, 0.8), 'Z': rng(1)},
                                                   {'X': span(0.04, 0.07), 'Y': span(1.0, 1.4), 'Z': rng(1)}),
                      GenerationLocationValues=circle(0.35, 0.6, axis=2),
                      RendererCommonValues=sr, DrawingValues=sd))
    b = bolts('Bolts', T + 'spark_bolt_a.png', span(5.0, 9.0), 'inf', radius=(0.35, 0.6), height=(0.0, 0.0))
    b['LocationValues'] = pva_location(location={'Z': span(-1.1, 0.8)})
    b['GenerationLocationValues'] = circle(0.35, 0.6, axis=2)
    parts.append(b)
    parts += blue_electric(flight=True)
    er, ed = sprite(T + 'spark_ember.png', 2, fade_in=2, fade_out=12, color=easing_color(
        color_range(WHITE_GOLD, 255, 10), color_range(ORANGE_GOLD, 0, 20)))
    parts.append(node('Embers', CommonValues=dict(common(span(30, 50), 'inf', 1.0), **RELEASE),
                      LocationValues=pva_location(location={'Z': span(-1.3, 0.7)},
                                                  velocity={'Z': span(-0.03, -0.01)}),
                      ScalingValues=scale_ease(span(0.04, 0.08), rng(0.0)),
                      LocationAbsValues=turbulence(0.04, 4, 33),
                      GenerationLocationValues=circle(0.3, 0.7, axis=2),
                      RendererCommonValues=er, DrawingValues=ed))
    gr, gd = sprite(T + 'spark_glow.png', 2, fade_in=12, fade_out=16, color=fixed_color(GOLD, 70))
    parts.append(node('Glow', CommonValues=common(life),
                      LocationValues=pva_location(location={'Z': rng(0.0)}),
                      ScalingValues=scale_single(3.0),
                      RendererCommonValues=gr, DrawingValues=gd))
    root = node('SparkingFlight', parts, CommonValues=common(life), DrawingValues={'Type': 0})
    comet = [(0.2, 0.0), (0.62, 0.8), (0.55, 2.0), (0.32, 2.65)]
    comet_strands = [(0.3, 0.0), (0.8, 0.9), (0.7, 2.1), (0.45, 2.8)]
    return root, [lathe('Comet', comet, axis=2),
                  lathe('CometStrands', comet_strands, mesh_type=1, ribbon_count=7, twist=0, axis=2)]


EFFECTS = {'sparking_aura': sparking_aura, 'sparking_aura_smooth': sparking_aura_smooth,
           'sparking_burst': sparking_burst, 'sparking_flight': sparking_flight}
PREVIEW = {'sparking_aura': 48, 'sparking_aura_smooth': 90, 'sparking_burst': 110, 'sparking_flight': 48}
PREVIEW_VIEW = {'sparking_aura': {'zoom': 12, 'frames': 2, 'gap_ms': 250, 'start_ms': 400},
                'sparking_burst': {'zoom': 10, 'frames': 3, 'gap_ms': 250, 'start_ms': 120}}
