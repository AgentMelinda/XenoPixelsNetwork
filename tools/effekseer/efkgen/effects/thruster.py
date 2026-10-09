"""Rocket plumes, modelled on a real solid/liquid motor's exhaust rather than vanilla flames.

Each effect is a short pulse the game re-sends every 8 ticks. The flame (cones, diamonds, glow)
follows its emitter; the flame puffs, sparks and smoke are released into the world when they are
born (LocationEffectType 1, "when creating"), so a moving missile or ship leaves a real trail.

  missile_thruster  bound to the missile entity: AAA moves it with the missile every frame and turns
                    its local +Z along the missile's velocity. The plume therefore points along -Z
                    from a nozzle 1.25 units behind the entity's centre (the game scales the effect by
                    0.4 x missile length, so that is the tail). White-hot core, orange-red flame cone,
                    Mach diamonds, flame puffs, sparks and a long reddish-grey smoke trail.
  ship_thruster     positional, for thruster blocks and ship missiles: authored along +Z from the
                    nozzle at the origin (AAA's rotationFromForward maps +Z onto the exhaust
                    direction the game sends). Shorter jet, denser smoke; scaled by throttle.
"""
from pathlib import Path

from .. import textures as tx
from ..project import (T, color_range, common, easing_color, fixed_color, lathe, model, node, pva_location,
                       random_color, rng, scale_ease, scale_fixed, scale_single, span, sphere, spin, sprite,
                       turbulence)

NAME = 'thruster'

# Exhaust palette (RGB 0-255), hottest to coolest, and the smoke.
WHITE_HOT = (255, 248, 232)
YELLOW = (255, 214, 130)
ORANGE = (255, 138, 42)
RED = (236, 62, 22)
DARK_RED = (150, 26, 10)
SMOKE_WARM = (150, 102, 92)   # smoke lit red by the flame it leaves
SMOKE_COOL = (96, 90, 88)

AXIS_Z = 2  # lathe axis: the plume runs along local +Z


def textures(tex: Path):
    tex.mkdir(parents=True, exist_ok=True)
    tx.streaks(tex / 'plume_flame.png', 61, streak_x=8.0, streak_y=0.35, gamma=1.3, bias=0.3)
    tx.mask(tex / 'plume_mask.png', 71, lo=0.2, span=0.5)
    tx.distortion(tex / 'plume_distort.png', 81, strength=8.0)
    tx.shock_diamond(tex / 'plume_shock.png')
    tx.smoke_puff(tex / 'plume_smoke.png', 91)
    tx.glow(tex / 'plume_glow.png')
    tx.mote(tex / 'plume_spark.png', core_width=0.12)


RELEASE = {'LocationEffectType': 1, 'RotationEffectType': 1}  # stay in the world once born
MISSILE_NOZZLE = 1.25  # effect units behind the missile's centre: half its length at 0.4 x length
# The missile plume's size, built in (2026-09-29 owner: 5). Scaling the whole bound effect at run
# time would also move the nozzle off the tail, so the size sits on a node under the nozzle.
MISSILE_PLUME_SIZE = 5.0


def released(common_values):
    common_values.update(RELEASE)
    return common_values


# The game re-sends a plume every 8 ticks = 24 frames (AAA runs Effekseer at 60 frames a second).
# The flame fades in over 24 frames and out over 24, so two copies overlap and their summed
# brightness stays constant: a steady flame instead of a pulse. Spawn rates are set for two copies.
PULSE = 24


def plume(name, length, width, smoke_every, smoke_scale, smoke_life, diamonds, life=2 * PULSE, nozzle=None,
          size=1.0):
    """One pulse of exhaust; length and width in blocks at scale 1.

    nozzle: None for a plume along +Z from the origin; a distance to hang it behind the origin
    and point it along -Z (the bound missile plume).
    """
    L, W = length, width
    parts = []
    # The visible flame: an expanding then necking cone of streaky fire, scrolling away fast.
    parts.append(node('FlameCone', CommonValues=common(life),
                      **model(0, ORANGE + (230,), 2, T + 'plume_flame.png', PULSE, PULSE, (256, 512, 0.0, -22),
                              alpha_tex=T + 'plume_mask.png', distort=(T + 'plume_distort.png', 0.12),
                              cutoff=(0.12, 0.06, RED))))
    parts.append(node('RedSheath', CommonValues=common(life),
                      **model(1, RED + (150,), 2, T + 'plume_flame.png', PULSE, PULSE, (256, 512, 0.5, -16),
                              alpha_tex=T + 'plume_mask.png')))
    parts.append(node('HotCore', CommonValues=common(life),
                      **model(2, WHITE_HOT + (255,), 2, T + 'plume_flame.png', PULSE, PULSE, (256, 512, 0.0, -30))))
    # Mach diamonds: bright shock discs standing in the jet, smaller and dimmer downstream.
    for i in range(diamonds):
        z = L * (0.16 + 0.17 * i)
        size = W * (1.15 - 0.2 * i)
        dr, dd = sprite(T + 'plume_shock.png', 2, fade_in=1, fade_out=2,
                        color=random_color(YELLOW, 235 - 40 * i, 20))
        parts.append(node(f'Diamond{i + 1}', CommonValues=common(span(3, 5), 'inf', 4),
                          LocationValues=pva_location(location={'Z': rng(z)}),
                          ScalingValues=scale_single(span(size * 0.8, size)),
                          RendererCommonValues=dr, DrawingValues=dd))
    # Flame puffs streaming out of the nozzle, cooling from yellow to red as they go.
    fr, fd = sprite(T + 'plume_glow.png', 2, fade_in=1, fade_out=8, color=easing_color(
        color_range(YELLOW, 230, 15), color_range(DARK_RED, 0, 10)))
    parts.append(node('FlamePuffs', CommonValues=released(common(span(10, 18), 'inf', 0.8)),
                      LocationValues=pva_location(
                          velocity={'X': span(-0.006, 0.006), 'Y': span(-0.006, 0.006),
                                    'Z': span(L * 0.045, L * 0.075)}),
                      ScalingValues=scale_ease(span(W * 0.9, W * 1.3), span(W * 2.2, W * 3.0)),
                      GenerationLocationValues=sphere(0.0, W * 0.3),
                      RendererCommonValues=fr, DrawingValues=fd))
    # Sparks and burning particles thrown out with the exhaust.
    sr, sd = sprite(T + 'plume_spark.png', 2, fade_out=10, color=easing_color(
        color_range(WHITE_HOT, 255), color_range(RED, 0, 15)))
    parts.append(node('Sparks', CommonValues=released(common(span(18, 32), 'inf', 2.4)),
                      LocationValues=pva_location(
                          velocity={'X': span(-0.02, 0.02), 'Y': span(-0.02, 0.02),
                                    'Z': span(L * 0.05, L * 0.1)}),
                      ScalingValues=scale_ease(span(0.03, 0.06), rng(0.0)),
                      LocationAbsValues=turbulence(0.04, 3, 21),
                      RendererCommonValues=sr, DrawingValues=sd))
    # The glow the flame throws on its surroundings.
    gr, gd = sprite(T + 'plume_glow.png', 2, fade_in=4, fade_out=10, color=random_color(ORANGE, 120, 20))
    parts.append(node('Glow', CommonValues=common(span(6, 10), 'inf', 8),
                      LocationValues=pva_location(location={'Z': rng(L * 0.25)}),
                      ScalingValues=scale_single(span(W * 6.0, W * 7.5)),
                      RendererCommonValues=gr, DrawingValues=gd))
    # Smoke: born at the flame's tail, reddish where the flame lights it, cooling to grey;
    # slows (drag), swells and drifts with turbulence. Outlives the pulse to leave a trail.
    mr, md = sprite(T + 'plume_smoke.png', 1, fade_in=6, fade_out=40, color=easing_color(
        color_range(SMOKE_WARM, 170, 12), color_range(SMOKE_COOL, 0, 8)))
    parts.append(node('Smoke', CommonValues=released(common(span(smoke_life - 30, smoke_life + 20), 'inf',
                                                            smoke_every * 1.6)),
                      LocationValues=pva_location(
                          location={'Z': span(L * 0.7, L * 1.0)},
                          velocity={'X': span(-0.006, 0.006), 'Y': span(-0.004, 0.008),
                                    'Z': span(L * 0.012, L * 0.024)},
                          accel={'Y': rng(0.0002), 'Z': rng(-L * 0.00012)}),
                      RotationValues=spin((-180, 180), (-1, 1)),
                      ScalingValues=scale_ease(span(W * 2.2, W * 3.0), span(W * smoke_scale * 0.8, W * smoke_scale)),
                      LocationAbsValues=turbulence(0.02, 5, 23),
                      GenerationLocationValues=sphere(0.0, W * 0.5),
                      RendererCommonValues=mr, DrawingValues=md))
    if nozzle is not None:
        # Hang the plume behind the origin and turn it round: +Z (the missile's velocity) becomes
        # the flight direction, the exhaust runs along -Z.
        if size != 1.0:
            parts = [node('Size', parts, CommonValues=common(life), DrawingValues={'Type': 0},
                          ScalingValues=scale_fixed(size, size, size))]
        parts = [node('Nozzle', parts, CommonValues=common(life), DrawingValues={'Type': 0},
                      LocationValues=pva_location(location={'Z': rng(-nozzle)}),
                      RotationValues={'Type': 0, 'Fixed': {'Rotation': {'Y': 180}}})]
    root = node(name, parts, CommonValues=common(life), DrawingValues={'Type': 0})
    cones = [
        lathe('FlameCone', [(W * 0.9, 0.0), (W * 1.6, L * 0.3), (W * 1.1, L * 0.75), (W * 0.2, L)], axis=AXIS_Z),
        lathe('RedSheath', [(W * 1.2, 0.0), (W * 2.2, L * 0.35), (W * 1.6, L * 0.85), (W * 0.4, L * 1.15)],
              axis=AXIS_Z),
        lathe('HotCore', [(W * 0.55, 0.0), (W * 0.8, L * 0.12), (W * 0.45, L * 0.35), (0.01, L * 0.55)],
              axis=AXIS_Z),
    ]
    return root, cones


def missile_thruster():
    return plume('MissilePlume', length=2.6, width=0.12, smoke_every=1.2, smoke_scale=14.0,
                 smoke_life=120, diamonds=4, nozzle=MISSILE_NOZZLE,
                 size=MISSILE_PLUME_SIZE)


def ship_thruster():
    return plume('ShipPlume', length=1.5, width=0.16, smoke_every=0.9, smoke_scale=11.0,
                 smoke_life=90, diamonds=3)


EFFECTS = {'missile_thruster': missile_thruster, 'ship_thruster': ship_thruster}
PREVIEW = {'missile_thruster': 150, 'ship_thruster': 120}
PREVIEW_VIEW = {'missile_thruster': {'zoom': 11, 'frames': 3, 'gap_ms': 400, 'start_ms': 500},
                'ship_thruster': {'zoom': 12, 'frames': 3, 'gap_ms': 400, 'start_ms': 500}}
