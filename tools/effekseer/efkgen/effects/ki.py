"""HD ki attacks, after Dragon Ball Super: one look per DragonMineZ ki type, in every aura colour.

Played by Combat V3's own shots (V3KiShots) and, on the client, over DragonMineZ's ki projectiles
(HdKiClient). Nothing here is an entity: the game sends these short effects where the attack is.
Each is built once per colour of the aura palette (effeks/aura/palette.txt) as ki_<part>_<hex>;
the game picks the nearest colour to the attack's own, exactly as the HD aura does.

  part         ki types                    how the game plays it
  charge       all                         once, upright, in the caster's hands (16 ticks)
  ball         SMALL_BALL MEDIUM_BALL      every tick at the shot: the pulses overlap into an orb
               BARRAGE                     with a trail; the game scales it
  giant        GIANT_BALL                  every 2 ticks at the ball; radius 1 block at scale 1
  wave_body    WAVE                        one 4-block length along +Z, built 4.4 long; laid end to
                                           end and re-sent every 6 ticks
  wave_head    WAVE                        every tick at the beam's tip, +Z along the beam
  wave_muzzle  WAVE BEAM LASER             every 3 ticks at the hands, +Z along the beam
  laser        LASER BEAM                  one 4-block length of a needle beam, as wave_body
  spiral       BEAM                        one 4-block length of a helix round a laser, in the
                                           attack's second colour (Makankosappo)
  disc         DISK                        every tick at the disc, upright
  explosion    EXPLOSION                   once, upright; radius about 3 blocks at scale 1
  shield       SHIELD                      every 10 ticks, upright; radius 1 block at scale 1
  area         AREA                        every 10 ticks, upright, on the ground; radius 1 block
  impact       all                         once where a shot lands; radius about 1.5 blocks

AAA runs Effekseer at 60 frames a second: one game tick is three frames. Beams run along +Z
(AAA's rotationFromForward maps +Z onto the direction the game sends).

Borrowed from the CC-0 Effekseer 1.80 samples (credited in effeks/credits.txt): the plasma web,
cell, hexagon, lightning, burst and normal-map textures, and the techniques of Laser01-03 (stacked
scrolling cylinders), Aura01_HDR (emissive cores, ribbon helix) and Simple_Ring_Shape (ring bands).

KI_COLOURS=5240831 (comma separated hex) builds only those colours, for trying a change quickly.
"""
import os
import shutil
from pathlib import Path

from .. import textures as tx
from ..project import (T, circle, color_range, common, easing_color, lathe, model, node, pva_location,
                       random_color, ring_shape, rng, scale_ease, scale_single, scale_xyz_ease, span, sphere, spin,
                       sprite, turbulence)
from . import aura as v1
from . import aura2 as v2

NAME = 'ki'
FOLDER = 'ki'

SAMPLES = v1.REPO / 'tools' / 'new_particles' / 'Effekseer1.80.6Win' / 'Sample'
BORROWED = {
    'ki_web.png': '00_Version16/Textures/Aura05_T.png',
    'ki_cells.png': '00_Version16/Textures/Aura02_T.png',
    'ki_hex.png': '00_Version16/Textures/ShapePattern01_T.png',
    'ki_normal.png': '00_Version16/Textures/Normal01.png',
    'ki_thunder.png': '00_Basic/Texture/Thunder01.png',
    'ki_burst.png': '00_Basic/Texture/Burst01.png',
}

FLOW = T + 'ki_flow.png'        # 512 x 1024
WEB = T + 'ki_web.png'          # 512
CELLS = T + 'ki_cells.png'      # 512
HEX = T + 'ki_hex.png'          # 512
NORMAL = T + 'ki_normal.png'
THUNDER = T + 'ki_thunder.png'
BURST = T + 'ki_burst.png'
GLOW = T + 'ki_glow.png'
CORE = T + 'ki_core.png'
CORONA = T + 'ki_corona.png'
RING = T + 'ki_ring.png'
SAW = T + 'ki_saw.png'
LINES = T + 'ki_lines.png'
STREAK = T + 'ki_streak.png'
SPARK = T + 'ki_spark.png'
SMOKE = T + 'ki_smoke.png'

AXIS_Y = 1
AXIS_Z = 2
LENGTH = 4.4        # the game spaces beam lengths 4.0 apart (KiLook.SEGMENT)
BODY_FRAMES = 30    # re-sent every 6 ticks = 18 frames, so each copy overlaps the next
FLAT = {'Type': 0, 'Fixed': {'Rotation': {'X': 90}}}
HDR = 2.0
WHITE = (255, 255, 255)


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    for name, source in BORROWED.items():
        shutil.copyfile(SAMPLES / source, tex / name)
    tx.energy_flow(tex / 'ki_flow.png', 601)
    tx.glow(tex / 'ki_glow.png', size=256)
    tx.mote(tex / 'ki_core.png', size=256, core_width=0.22)
    tx.corona(tex / 'ki_corona.png', 611)
    tx.shock_ring_hd(tex / 'ki_ring.png', 621)
    tx.saw_disc(tex / 'ki_saw.png')
    tx.speed_lines(tex / 'ki_lines.png', 631)
    tx.shock_diamond(tex / 'ki_streak.png', size=256)
    tx.mote(tex / 'ki_spark.png', core_width=0.14)
    tx.smoke_puff(tex / 'ki_smoke.png', 641)


def tones(hexcode):
    """(white-hot core, body, rim) for one palette colour. Dark ki (black, deep purple) is lifted
    so it still shows when added to the scene."""
    c = v1.rgb_of(hexcode)
    dark = v1.luma(c) < 0.18
    body = v1.mix(c, WHITE, 0.25) if dark else v1.vivid(c)
    rim = v1.mix(c, WHITE, 0.45) if dark else v1.mix(v1.deep(c), body, 0.45)
    return v1.mix(body, WHITE, 0.8), body, rim


def radial(speed):
    return {'X': span(-speed, speed), 'Y': span(-speed, speed), 'Z': span(-speed, speed)}


def orb(radius, axis, length=None):
    """A rounded body of revolution: a ball, or a bulb stretched to `length` along its axis."""
    half = radius if length is None else length / 2.0
    return [(0.02, -half), (radius, -half * 0.55), (radius, half * 0.55), (0.02, half)]


def glow(name, texture, colour, alpha, life, start, end, fade_in=0, fade_out=0, emissive=None, spins=None,
         location=None, jitter=8, blend=2, count=None, every=None, delay=None):
    r, d = sprite(texture, blend, fade_in=fade_in, fade_out=fade_out, emissive=emissive,
                  color=random_color(colour, alpha, jitter))
    extra = {}
    if spins:
        extra['RotationValues'] = spin((-180, 180), spins)
    if location:
        extra['LocationValues'] = pva_location(location=location)
    return node(name, CommonValues=common(life, count, every, delay),
                ScalingValues=scale_ease(rng(start), rng(end)) if start != end else scale_single(start),
                RendererCommonValues=r, DrawingValues=d, **extra)


def sparks(name, colours, count, every, life, speed, size, spawn, gravity=0.0, seed=None):
    start, end = colours
    r, d = sprite(SPARK, 2, fade_out=8, color=easing_color(color_range(start, 255), color_range(end, 0, 12)))
    extra = {'LocationAbsValues': turbulence(0.03, 3, seed)} if seed else {}
    velocity = speed if isinstance(speed, dict) else radial(speed)
    return node(name, CommonValues=common(span(*life), count, every),
                LocationValues=pva_location(velocity=velocity, accel={'Y': rng(-gravity)} if gravity else None),
                ScalingValues=scale_ease(span(*size), rng(0.0)),
                GenerationLocationValues=spawn, RendererCommonValues=r, DrawingValues=d, **extra)


def streaks(name, texture, colours, count, every, life, velocity, start, end, spawn=None, gravity=0.0,
            emissive=None):
    """Sprites stretched along the way they fly: energy streaks, rays, lightning along a beam."""
    r, d = sprite(texture, 2, billboard=4, fade_in=1, fade_out=6, emissive=emissive, color=easing_color(
        color_range(colours[0], 255, 6), color_range(colours[1], 0, 12)))
    extra = {'GenerationLocationValues': spawn} if spawn else {}
    return node(name, CommonValues=common(span(*life), count, every),
                LocationValues=pva_location(velocity=velocity, accel={'Y': rng(-gravity)} if gravity else None),
                ScalingValues=scale_xyz_ease({'X': span(*start[0]), 'Y': span(*start[1]), 'Z': rng(1)},
                                             {'X': span(*end[0]), 'Y': span(*end[1]), 'Z': rng(1)}),
                RendererCommonValues=r, DrawingValues=d, **extra)


def band(name, colour, inner, outer, life, start, end, alpha=255, fade_out=8, rotation=None, velocity=None,
         count=None, every=None, delay=None, location=None, texture=None, emissive=None):
    """A ring band that grows from `start` to `end` times its size."""
    r, d = ring_shape(texture, inner, outer, colour + (0,), colour + (alpha,), fade_in=1, fade_out=fade_out,
                      emissive=emissive)
    extra = {}
    if rotation:
        extra['RotationValues'] = rotation
    if velocity or location:
        extra['LocationValues'] = pva_location(location=location, velocity=velocity)
    return node(name, CommonValues=common(life, count, every, delay),
                ScalingValues=scale_ease(rng(start), rng(end)),
                RendererCommonValues=r, DrawingValues=d, **extra)


# ---------------------------------------------------------------------------------- orbs

def ball(h):
    core, body, rim = tones(h)
    parts = [
        glow('Core', CORE, core, 255, 12, 0.95, 0.6, fade_out=6, emissive=HDR),
        glow('Plasma', WEB, body, 235, 15, 1.35, 1.05, fade_in=1, fade_out=9, spins=(-6, 6)),
        glow('Corona', CORONA, body, 190, 18, 2.5, 1.9, fade_in=1, fade_out=11, spins=(-9, 9)),
        glow('Halo', GLOW, rim, 120, 18, 3.2, 2.4, fade_in=1, fade_out=12),
        streaks('Arc', THUNDER, (core, body), 1, 1, (4, 7), radial(0.04), ((0.25, 0.4), (0.7, 1.1)),
                ((0.2, 0.3), (0.9, 1.3)), spawn=sphere(0.2, 0.5)),
        sparks('Sparks', (core, body), 5, 1, (10, 20), 0.035, (0.08, 0.16), sphere(0.15, 0.5)),
    ]
    return node('KiBall', parts, CommonValues=common(22), DrawingValues={'Type': 0}), []


def giant(h):
    core, body, rim = tones(h)
    life = 24
    parts = [
        node('Surface', CommonValues=common(life),
             **model(0, body + (215,), 2, WEB, 5, 12, (512, 512, 1.2, -2.0), distort=(NORMAL, 0.08))),
        node('Depth', CommonValues=common(life),
             **model(1, rim + (150,), 2, CELLS, 5, 12, (512, 512, -0.8, 1.4))),
        node('Heart', CommonValues=common(life),
             **model(2, core + (255,), 2, WEB, 5, 12, (512, 512, 0.6, -3.0), emissive=HDR)),
        glow('Shine', CORE, core, 200, life, 1.6, 1.6, fade_in=5, fade_out=12, emissive=HDR),
        glow('Corona', CORONA, body, 170, life, 3.4, 3.1, fade_in=5, fade_out=12, spins=(-2, 2)),
        glow('Light', GLOW, rim, 95, life, 5.5, 5.5, fade_in=5, fade_out=12),
        node('Belt', [band('BeltRing', body, 1.12, 1.4, life, 1.0, 1.08, alpha=150, fade_out=12)],
             CommonValues=common(life), DrawingValues={'Type': 0}, RotationValues=FLAT),
        streaks('Arcs', THUNDER, (core, body), 3, 2, (5, 9), radial(0.03), ((0.3, 0.5), (0.9, 1.5)),
                ((0.2, 0.4), (1.2, 1.9)), spawn=sphere(0.9, 1.15)),
        sparks('Sparks', (core, body), 8, 1, (16, 30), 0.03, (0.1, 0.2), sphere(0.9, 1.4), seed=651),
    ]
    shapes = [lathe('Surface', orb(1.0, AXIS_Y), axis=AXIS_Y, fade_ends=False),
              lathe('Depth', orb(1.08, AXIS_Y), axis=AXIS_Y, fade_ends=False),
              lathe('Heart', orb(0.62, AXIS_Y), axis=AXIS_Y, fade_ends=False)]
    return node('KiGiant', parts, CommonValues=common(life + 6), DrawingValues={'Type': 0}), shapes


# ---------------------------------------------------------------------------------- beams

def tube(name, radius, bulge=1.06):
    """A straight length of beam. Not faded at the rims: neighbouring lengths must join."""
    return lathe(name, [(radius, 0.0), (radius * bulge, LENGTH * 0.3), (radius * bulge, LENGTH * 0.7),
                        (radius, LENGTH)], axis=AXIS_Z, fade_ends=False)


def wave_body(h):
    core, body, rim = tones(h)
    life = BODY_FRAMES
    along = lambda lo, hi: {'X': span(-0.02, 0.02), 'Y': span(-0.02, 0.02), 'Z': span(lo, hi)}
    parts = [
        node('Sheath', CommonValues=common(life),
             **model(0, rim + (150,), 2, FLOW, 3, 12, (512, 1024, 0.8, -46), distort=(NORMAL, 0.1))),
        node('Body', CommonValues=common(life),
             **model(1, body + (225,), 2, FLOW, 3, 12, (512, 1024, -0.5, -60), distort=(NORMAL, 0.06))),
        node('Core', CommonValues=common(life),
             **model(2, core + (255,), 2, FLOW, 3, 12, (512, 1024, 0.0, -80), emissive=HDR)),
        node('Coil', CommonValues=common(life),
             **model(3, body + (170,), 2, WEB, 3, 12, (512, 512, 0.0, -30))),
    ]
    for i, z in enumerate((1.0, 3.2)):
        parts.append(glow(f'Light{i + 1}', GLOW, body, 95, life, 3.4, 3.4, fade_in=3, fade_out=12,
                          location={'Z': rng(z)}))
    parts += [
        # Shock rings racing down the beam.
        band('Rings', core, 0.86, 1.05, 14, 0.9, 1.5, alpha=200, velocity={'Z': rng(0.16)}, count=2, every=9,
             emissive=HDR),
        streaks('Bolts', THUNDER, (core, body), 4, 4, (5, 9), along(0.05, 0.14), ((0.3, 0.5), (1.2, 2.0)),
                ((0.2, 0.3), (1.6, 2.6)), spawn=circle(0.7, 0.95, axis=AXIS_Z)),
        streaks('Rush', STREAK, (core, body), 8, 2, (6, 12), along(0.25, 0.45), ((0.08, 0.14), (0.6, 1.1)),
                ((0.03, 0.06), (0.9, 1.6)), spawn=circle(0.5, 0.9, axis=AXIS_Z)),
        sparks('Sparks', (core, body), 6, 2, (12, 24), {'X': span(-0.05, 0.05), 'Y': span(-0.05, 0.05),
                                                       'Z': span(0.02, 0.1)}, (0.08, 0.16),
               circle(0.7, 1.0, axis=AXIS_Z)),
    ]
    shapes = [tube('Sheath', 0.78), tube('Body', 0.52), tube('Core', 0.28),
              lathe('Coil', [(0.92, 0.0), (0.96, LENGTH * 0.3), (0.96, LENGTH * 0.7), (0.92, LENGTH)],
                    mesh_type=1, ribbon_count=3, twist=2, axis=AXIS_Z, fade_ends=False)]
    return node('KiWaveBody', parts, CommonValues=common(life), DrawingValues={'Type': 0}), shapes


def wave_head(h):
    core, body, rim = tones(h)
    life = 15
    parts = [
        node('Bulb', CommonValues=common(life),
             **model(0, body + (220,), 2, WEB, 1, 9, (512, 512, 0.6, -6.0), distort=(NORMAL, 0.08))),
        node('Heart', CommonValues=common(life),
             **model(1, core + (255,), 2, FLOW, 1, 9, (512, 1024, 0.0, -40), emissive=HDR)),
        glow('Shine', CORE, core, 255, 12, 2.6, 2.0, fade_out=7, emissive=HDR, location={'Z': rng(0.6)}),
        glow('Corona', CORONA, body, 200, life, 4.4, 3.6, fade_in=1, fade_out=9, spins=(-8, 8),
             location={'Z': rng(0.6)}),
        glow('Light', GLOW, rim, 110, life, 6.5, 5.5, fade_in=1, fade_out=10, location={'Z': rng(0.4)}),
        band('Bow', core, 1.1, 1.45, 12, 0.9, 2.2, alpha=190, location={'Z': rng(0.2)}, emissive=HDR),
        streaks('Arcs', THUNDER, (core, body), 3, 1, (4, 7), radial(0.06), ((0.3, 0.5), (1.0, 1.6)),
                ((0.2, 0.4), (1.4, 2.2)), spawn=sphere(0.8, 1.3)),
        sparks('Sparks', (core, body), 6, 1, (10, 20), 0.08, (0.1, 0.2), sphere(0.6, 1.2)),
    ]
    shapes = [lathe('Bulb', [(0.6, -0.8), (1.3, -0.1), (1.25, 0.9), (0.03, 1.9)], axis=AXIS_Z, fade_ends=False),
              lathe('Heart', [(0.3, -0.8), (0.75, -0.1), (0.7, 0.8), (0.02, 1.5)], axis=AXIS_Z, fade_ends=False)]
    return node('KiWaveHead', parts, CommonValues=common(life + 5), DrawingValues={'Type': 0}), shapes


def wave_muzzle(h):
    core, body, rim = tones(h)
    parts = [
        glow('Flare', CORE, core, 255, 14, 2.4, 1.8, fade_in=1, fade_out=8, emissive=HDR),
        glow('Corona', CORONA, body, 200, 18, 3.8, 3.0, fade_in=2, fade_out=10, spins=(-6, 6)),
        glow('Lines', LINES, core, 170, 14, 3.0, 4.2, fade_in=1, fade_out=8, spins=(-3, 3)),
        band('Collar', core, 0.9, 1.2, 14, 0.8, 2.0, alpha=200, location={'Z': rng(0.3)}, emissive=HDR),
        sparks('Sparks', (core, body), 5, 1, (10, 18), 0.07, (0.1, 0.18), sphere(0.3, 0.9)),
    ]
    return node('KiWaveMuzzle', parts, CommonValues=common(22), DrawingValues={'Type': 0}), []


def laser(h):
    core, body, rim = tones(h)
    life = BODY_FRAMES
    parts = [
        node('Sheath', CommonValues=common(life),
             **model(0, body + (200,), 2, FLOW, 2, 12, (512, 1024, 0.0, -70))),
        node('Core', CommonValues=common(life),
             **model(1, core + (255,), 2, FLOW, 2, 12, (512, 1024, 0.0, -90), emissive=HDR)),
    ]
    for i, z in enumerate((0.7, 2.2, 3.7)):
        parts.append(glow(f'Light{i + 1}', GLOW, body, 110, life, 1.1, 1.1, fade_in=2, fade_out=12,
                          location={'Z': rng(z)}))
    parts.append(sparks('Sparks', (core, body), 4, 4, (8, 16), {'X': span(-0.03, 0.03), 'Y': span(-0.03, 0.03),
                                                               'Z': span(0.05, 0.15)}, (0.05, 0.1),
                        circle(0.1, 0.25, axis=AXIS_Z)))
    shapes = [tube('Sheath', 0.19, 1.0), tube('Core', 0.09, 1.0)]
    return node('KiLaser', parts, CommonValues=common(life), DrawingValues={'Type': 0}), shapes


def spiral(h):
    core, body, rim = tones(h)
    life = BODY_FRAMES
    parts = [
        node('Helix', CommonValues=common(life),
             **model(0, body + (255,), 2, FLOW, 2, 12, (512, 1024, 0.0, -50), emissive=HDR)),
        node('Wake', CommonValues=common(life),
             **model(1, rim + (120,), 2, WEB, 2, 12, (512, 512, 0.0, -24))),
    ]
    helix = lambda name, r, n: lathe(name, [(r, 0.0), (r, LENGTH * 0.3), (r, LENGTH * 0.7), (r, LENGTH)],
                                     mesh_type=1, ribbon_count=n, twist=3, axis=AXIS_Z, fade_ends=False)
    return (node('KiSpiral', parts, CommonValues=common(life), DrawingValues={'Type': 0}),
            [helix('Helix', 0.42, 1), helix('Wake', 0.5, 2)])


# ---------------------------------------------------------------------------------- disc

def disc(h):
    core, body, rim = tones(h)
    life = 12
    flat = [
        glow('Saw', SAW, core, 255, life, 2.6, 2.6, fade_out=6, spins=(24, 32), emissive=HDR),
        glow('Fill', GLOW, body, 150, life, 2.4, 2.4, fade_out=7),
        band('Rim', core, 1.02, 1.1, life, 1.0, 1.0, alpha=255, fade_out=6, emissive=HDR),
    ]
    parts = [
        node('Flat', flat, CommonValues=common(life), DrawingValues={'Type': 0}, RotationValues=FLAT),
        sparks('Sparks', (core, body), 5, 1, (8, 14), {'X': span(-0.12, 0.12), 'Y': span(-0.01, 0.01),
                                                      'Z': span(-0.12, 0.12)}, (0.08, 0.14),
               circle(0.9, 1.1, axis=AXIS_Y)),
    ]
    return node('KiDisc', parts, CommonValues=common(life + 6), DrawingValues={'Type': 0}), []


# ---------------------------------------------------------------------------------- one-shots

def charge(h):
    core, body, rim = tones(h)
    parts = [
        glow('Orb', CORE, core, 255, 48, 0.2, 1.1, fade_in=6, fade_out=6, emissive=HDR),
        glow('Plasma', WEB, body, 220, 48, 0.3, 1.6, fade_in=6, fade_out=6, spins=(-5, 5)),
        glow('Corona', CORONA, body, 190, 48, 0.8, 3.0, fade_in=8, fade_out=8, spins=(-4, 4)),
        glow('Light', GLOW, rim, 100, 48, 1.5, 4.5, fade_in=8, fade_out=8),
        # Rings and lines closing in on the hands: the energy being drawn together.
        glow('Gather', RING, body, 230, 14, 3.4, 0.4, fade_in=3, fade_out=4, count=4, every=10),
        glow('Inrush', LINES, core, 200, 12, 4.5, 1.5, fade_in=3, fade_out=4, spins=(-2, 2), count=5, every=8),
        streaks('Arcs', THUNDER, (core, body), 10, 4, (4, 8), radial(0.03), ((0.25, 0.45), (0.8, 1.4)),
                ((0.2, 0.3), (1.1, 1.8)), spawn=sphere(0.3, 0.9)),
        sparks('Motes', (body, core), 30, 1.4, (14, 26), 0.012, (0.1, 0.18), sphere(0.4, 1.2), seed=661),
    ]
    return node('KiCharge', parts, CommonValues=common(54), DrawingValues={'Type': 0}), []


def impact(h):
    core, body, rim = tones(h)
    parts = [
        glow('Flash', CORE, WHITE, 255, 9, 1.5, 6.0, fade_out=7, emissive=HDR),
        glow('Burst', BURST, core, 255, 14, 1.5, 5.0, fade_in=1, fade_out=9, spins=(-180, 180), emissive=HDR),
        glow('Fire', WEB, body, 230, 20, 1.2, 4.0, fade_in=1, fade_out=13, spins=(-3, 3)),
        glow('Shock', RING, core, 255, 18, 0.6, 6.5, fade_out=11),
        glow('Light', GLOW, rim, 130, 30, 5.0, 7.0, fade_in=2, fade_out=20),
        streaks('Rays', STREAK, (core, body), 46, 0.1, (12, 24), radial(0.24), ((0.07, 0.12), (0.5, 0.9)),
                ((0.02, 0.04), (0.2, 0.4)), gravity=0.003),
        streaks('Arcs', THUNDER, (core, body), 6, 1, (5, 9), radial(0.05), ((0.3, 0.5), (1.0, 1.8)),
                ((0.2, 0.3), (1.5, 2.4)), spawn=sphere(0.3, 0.9)),
        sparks('Embers', (body, rim), 36, 0.15, (24, 46), 0.13, (0.1, 0.2), sphere(0.0, 0.3), gravity=0.002,
               seed=671),
    ]
    return node('KiImpact', parts, CommonValues=common(54), DrawingValues={'Type': 0}), []


def explosion(h):
    core, body, rim = tones(h)
    smoke_r, smoke_d = sprite(SMOKE, 1, fade_in=6, fade_out=40, color=easing_color(
        color_range(v1.mix(rim, (150, 150, 150), 0.5), 200, 10), color_range((70, 70, 74), 0, 8)))
    dome = lambda name, ref, colour, alpha, texture, life, start, end, emissive=None: node(
        name, CommonValues=common(life), ScalingValues=scale_ease(rng(start), rng(end)),
        **model(ref, colour + (alpha,), 2, texture, 2, life // 2, (512, 512, 1.0, -3.0), distort=(NORMAL, 0.1),
                emissive=emissive))
    parts = [
        glow('Flash', CORE, WHITE, 255, 12, 2.0, 12.0, fade_out=9, emissive=HDR),
        dome('Dome', 0, body, 220, WEB, 44, 0.4, 3.0),
        dome('Shell', 0, rim, 150, CELLS, 54, 0.5, 3.4),
        dome('Heart', 0, core, 255, WEB, 28, 0.3, 2.3, emissive=HDR),
        node('Ground', [band('GroundRing', core, 0.8, 1.0, 34, 0.6, 7.5, alpha=230, fade_out=22, emissive=HDR),
                        band('GroundWake', body, 0.55, 1.0, 44, 0.4, 6.0, alpha=140, fade_out=30, texture=FLOW)],
             CommonValues=common(46), DrawingValues={'Type': 0}, RotationValues=FLAT),
        glow('Shock', RING, core, 230, 26, 1.0, 12.0, fade_out=16),
        glow('Burst', BURST, core, 255, 18, 2.0, 9.0, fade_in=1, fade_out=12, spins=(-180, 180), emissive=HDR),
        streaks('Rays', STREAK, (core, body), 70, 0.12, (16, 34), radial(0.36), ((0.1, 0.18), (0.8, 1.4)),
                ((0.03, 0.06), (0.3, 0.6)), gravity=0.003),
        streaks('Arcs', THUNDER, (core, body), 12, 2, (6, 10), radial(0.05), ((0.4, 0.7), (1.6, 2.6)),
                ((0.3, 0.4), (2.2, 3.4)), spawn=sphere(1.0, 2.6)),
        sparks('Embers', (body, rim), 70, 0.12, (40, 90), 0.2, (0.12, 0.26), sphere(0.0, 0.6), gravity=0.0022,
               seed=681),
        node('Smoke', CommonValues=common(span(80, 130), 18, 0.6),
             LocationValues=pva_location(velocity=radial(0.04), accel={'Y': rng(0.0004)}),
             RotationValues=spin((-180, 180), (-1, 1)),
             ScalingValues=scale_ease(span(1.2, 1.8), span(3.0, 4.2)),
             LocationAbsValues=turbulence(0.02, 5, 691),
             GenerationLocationValues=sphere(0.0, 1.4),
             RendererCommonValues=smoke_r, DrawingValues=smoke_d),
        glow('Light', GLOW, rim, 130, 60, 10.0, 14.0, fade_in=3, fade_out=40),
    ]
    return (node('KiExplosion', parts, CommonValues=common(150), DrawingValues={'Type': 0}),
            [lathe('Dome', orb(1.0, AXIS_Y), axis=AXIS_Y, fade_ends=False)])


# ---------------------------------------------------------------------------------- held shapes

def shield(h):
    core, body, rim = tones(h)
    life = 40   # re-sent every 10 ticks = 30 frames; fades cross over 10
    parts = [
        node('Cells', CommonValues=common(life),
             **model(0, body + (120,), 2, HEX, 10, 10, (512, 512, 0.3, -0.6))),
        node('Flow', CommonValues=common(life),
             **model(1, rim + (90,), 2, WEB, 10, 10, (512, 512, -0.5, 1.0), distort=(NORMAL, 0.06))),
        glow('Rim', RING, core, 150, life, 2.35, 2.35, fade_in=10, fade_out=10),
        glow('Light', GLOW, rim, 55, life, 3.4, 3.4, fade_in=10, fade_out=10),
        node('Belt', [band('BeltRing', core, 1.0, 1.08, life, 1.0, 1.0, alpha=170, fade_out=10, emissive=HDR)],
             CommonValues=common(life), DrawingValues={'Type': 0}, RotationValues=FLAT),
        sparks('Motes', (core, body), 8, 3, (14, 26), 0.01, (0.06, 0.12), sphere(0.95, 1.05)),
    ]
    shapes = [lathe('Cells', orb(1.0, AXIS_Y), axis=AXIS_Y, fade_ends=False),
              lathe('Flow', orb(1.04, AXIS_Y), axis=AXIS_Y, fade_ends=False)]
    return node('KiShield', parts, CommonValues=common(life), DrawingValues={'Type': 0}), shapes


def area(h):
    core, body, rim = tones(h)
    life = 40
    ground = [
        band('Edge', core, 0.9, 1.0, life, 1.0, 1.0, alpha=230, fade_out=10, emissive=HDR),
        band('Field', body, 0.05, 0.95, life, 1.0, 1.0, alpha=70, fade_out=10, texture=WEB),
        band('Pulse', core, 0.1, 0.22, 24, 0.5, 4.4, alpha=180, fade_out=12, count=2, every=14),
    ]
    rise_r, rise_d = sprite(STREAK, 2, billboard=1, fade_in=2, fade_out=8, color=easing_color(
        color_range(core, 230, 6), color_range(body, 0, 12)))
    parts = [
        node('Ground', ground, CommonValues=common(life), DrawingValues={'Type': 0}, RotationValues=FLAT,
             LocationValues=pva_location(location={'Y': rng(0.06)})),
        node('Pillar', CommonValues=common(life),
             **model(0, body + (95,), 2, FLOW, 10, 10, (512, 1024, 0.2, -18))),
        node('Rise', CommonValues=common(span(14, 24), 14, 2),
             LocationValues=pva_location(location={'Y': span(0.0, 0.6)}, velocity={'Y': span(0.06, 0.12)}),
             ScalingValues=scale_xyz_ease({'X': span(0.05, 0.09), 'Y': span(0.4, 0.8), 'Z': rng(1)},
                                          {'X': span(0.02, 0.04), 'Y': span(0.9, 1.6), 'Z': rng(1)}),
             GenerationLocationValues=circle(0.2, 0.95, axis=AXIS_Y),
             RendererCommonValues=rise_r, DrawingValues=rise_d),
        sparks('Motes', (core, body), 10, 3, (16, 30), {'X': span(-0.01, 0.01), 'Y': span(0.02, 0.05),
                                                       'Z': span(-0.01, 0.01)}, (0.08, 0.14),
               circle(0.1, 1.0, axis=AXIS_Y)),
    ]
    shapes = [lathe('Pillar', [(0.96, 0.0), (0.98, 1.0), (0.98, 2.2), (0.9, 3.4)], axis=AXIS_Y)]
    return node('KiArea', parts, CommonValues=common(life), DrawingValues={'Type': 0}), shapes


PARTS = {'charge': (charge, 54), 'ball': (ball, 22), 'giant': (giant, 30), 'wave_body': (wave_body, BODY_FRAMES),
         'wave_head': (wave_head, 20), 'wave_muzzle': (wave_muzzle, 22), 'laser': (laser, BODY_FRAMES),
         'spiral': (spiral, BODY_FRAMES), 'disc': (disc, 18), 'explosion': (explosion, 150),
         'shield': (shield, 40), 'area': (area, 40), 'impact': (impact, 54)}


def colours():
    only = [c.strip().lower() for c in os.environ.get('KI_COLOURS', '').split(',') if c.strip()]
    return only or [c.lower() for c in v2.shipped_palette()]


EFFECTS = {}
PREVIEW = {}
for _part, (_build, _frames) in PARTS.items():
    for _hex in colours():
        EFFECTS[f'ki_{_part}_{_hex}'] = (lambda b=_build, h=_hex: b(h))
        PREVIEW[f'ki_{_part}_{_hex}'] = _frames


def extra_files(dest: Path):
    """What the game may ask for: the parts, one per line (KiLook is tested against this list). The
    colours are the aura palette's."""
    (dest / 'parts.txt').write_text('\n'.join(PARTS) + '\n', encoding='utf-8')
