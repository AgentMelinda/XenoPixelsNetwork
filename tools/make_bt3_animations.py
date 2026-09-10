"""Author the XenoPixels BT3 melee animations as a DragonMineZ-compatible GeckoLib file.

Why DMZ format and not PAL
--------------------------
PlayerAnimationLibrary cannot draw anything in this modpack. ``PlayerRendererMixin`` in DMZ
injects at the head of ``PlayerRenderer.render`` and calls ``ci.cancel()`` whenever
``DMZRendererCache.getRenderer(player)`` is non-null - i.e. for anybody with a DMZ character - and
draws its own GeckoLib model instead. Every PAL mixin targets ``PlayerRenderer`` / ``PlayerModel``
/ ``HumanoidModel``, so PAL poses a model that is never drawn. Confirmed in game: a clip triggers
successfully and nothing moves. So animations that must be *seen* are authored for DMZ's rig.

Format, taken from ``assets/dragonminez/animations/entity/races/combat.animation.json``:

* ``format_version`` 1.8.0, animations keyed by name under ``"animations"``.
* Bones are ``root, waist, head, right_arm, left_arm, right_leg, left_leg``. ``root`` is the whole
  fighter, ``waist`` the upper body.
* Keyframes are ``{"<seconds>": {"vector": [x, y, z]}}``; rotations in degrees. DMZ writes the
  vector form throughout, so this matches it exactly rather than relying on the loader's shorthand.
* The model faces +Z, so a negative arm X rotation swings forward - vanilla aims a bow with
  ``xRot = -90``.

Names are prefixed ``combat.xeno_`` so they can never collide with DMZ's own 34 animations.

Style is taken from DMZ's own ``combat.one_handed_punch_right``: a strike snaps out over roughly a
third of its length, holds an impact frame, then recovers, with the hips (``root``) driving the
rotation and the off arm staying up as a guard.

Usage
-----
    python tools/make_bt3_animations.py [--out PATH]
    python tools/pal_preview.py <that path>          # to look at the result
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from zipfile import ZipFile

GUARD_R = [-55, 0, 16]      # right arm resting guard
GUARD_L = [-40, 0, -14]     # left arm resting guard
# Boxing high guard: each fist by its own shoulder, elbows in.
BOX_L = [-92, 22, -52]
BOX_R = [-92, -22, 52]


def clip(length: float, bones: dict, *, sound_effects=None, particle_effects=None,
         timeline=None) -> dict:
    """One animation: seconds plus {bone: {channel: {time: [x,y,z]}}}."""
    out = {"animation_length": length, "bones": {}}
    for bone, channels in bones.items():
        out["bones"][bone] = {
            channel: {str(t): {"vector": list(v)} for t, v in frames.items()}
            for channel, frames in channels.items()
        }
    if sound_effects:
        out["sound_effects"] = {str(t): value for t, value in sound_effects.items()}
    if particle_effects:
        out["particle_effects"] = {str(t): value for t, value in particle_effects.items()}
    if timeline:
        out["timeline"] = {str(t): value for t, value in timeline.items()}
    return out


RUSH_PROFILES = {
    "universal": (0, 0, 1.00),
    "saiyan": (8, -3, 1.04),
    "human": (-6, 2, 0.98),
    "namekian": (12, -5, 1.02),
    "majin": (-12, 7, 1.05),
    "arcosian": (16, -8, 1.06),
    "super_saiyan_blue": (6, -7, 1.12),
    "super_saiyan_god": (-7, -9, 1.10),
    "super_saiyan_3": (13, -5, 1.14),
    "super_saiyan_2": (-10, -4, 1.11),
    "super_saiyan": (9, -2, 1.08),
    "golden_arcosian": (-16, -10, 1.14),
    "potential_unleashed": (5, -6, 1.10),
    "orange_namekian": (18, -4, 1.16),
    "giant_namekian": (-18, 3, 1.18),
    "pure_majin": (14, 9, 1.16),
}


def cinematic_rush(profile: str) -> dict:
    """One continuous BT3 rush whose presentation varies by race/form profile."""
    yaw, lean, power = RUSH_PROFILES[profile]
    side = 1 if yaw >= 0 else -1
    root_yaw = abs(yaw)
    impact_scale = 1.0 + (power - 1.0) * 1.8
    bones = {
        "root": {
            "rotation": {
                0.0: [0, 0, 0], 0.10: [-22 + lean, root_yaw * 0.3, 0],
                0.25: [-8, -18 * side + yaw * 0.2, 0], 0.38: [-16, 12 * side, 0],
                0.50: [-5, 28 * side + yaw * 0.25, 0], 0.66: [-24, -10 * side, 0],
                0.80: [-14, -34 * side + yaw * 0.25, 0], 0.97: [8, 46 * side, 0],
                1.15: [-12 + lean * 0.35, -10 * side + yaw * 0.35, 0],
                1.28: [-9, -8 * side, 0], 1.40: [0, 0, 0],
            },
            "position": {
                0.0: [0, 0, 0], 0.10: [0, 0.15, -1.2 * power],
                0.25: [0.3 * side, 0.1, -2.4 * power],
                0.50: [-0.3 * side, 0.25, -2.0 * power],
                0.80: [0.15 * side, 1.1, -2.8 * power],
                1.15: [0, 0.35, -3.2 * power], 1.28: [0, 0.2, -2.4 * power],
                1.40: [0, 0, 0],
            },
            "scale": {
                0.0: [1, 1, 1], 0.25: [1, 1, 1.16 * power],
                0.50: [1, 1, 1.18 * power], 0.80: [1, 1, 1.22 * power],
                1.15: [1.04, 1, 1.34 * impact_scale],
                1.28: [1.02, 1, 1.22 * impact_scale], 1.40: [1, 1, 1],
            },
        },
        "waist": {"rotation": {
            0.0: [0, 0, 0], 0.10: [-14, -8 * side, 0],
            0.25: [-4, -28 * side, 5 * side], 0.50: [8, 34 * side, -8 * side],
            0.80: [-18, -22 * side, 10 * side], 0.97: [12, 44 * side, -8 * side],
            1.15: [-8, -16 * side, 0], 1.40: [0, 0, 0],
        }},
        "right_arm": {
            "rotation": {
                0.0: G3_R, 0.10: [-38, -20, 42], 0.25: [-98, 18, 4],
                0.38: [-56, -12, 26], 0.50: [-72, -18, 44],
                0.80: [24, -14, 36], 0.97: [-28, 58 * side, 66 * side],
                1.15: [-112, -8, 8], 1.28: [-106, -6, 8], 1.40: G3_R,
            },
            "position": {
                0.0: [0, 0, 0], 0.10: [0.5, 0.3, 1.8],
                0.25: [0.2, -0.1, -3.2 * power], 0.38: [0, 0, 0],
                0.97: [1.4 * side, 0.7, 2.2],
                1.15: [0.25, 0, -3.8 * power], 1.28: [0.2, 0, -3.4 * power],
                1.40: [0, 0, 0],
            },
        },
        "left_arm": {
            "rotation": {
                0.0: G3_L, 0.10: [-58, 18, -42], 0.25: [-64, 12, -34],
                0.38: [-42, 54 * side, -76 * side],
                0.50: [-88, -34 * side, -18 * side], 0.66: [-52, 18, -40],
                0.80: [18, 12, -34], 0.97: [-30, -50 * side, -58 * side],
                1.15: [-112, 8, -8], 1.28: [-106, 6, -8], 1.40: G3_L,
            },
            "position": {
                0.0: [0, 0, 0], 0.38: [-1.5 * side, 0.5, 1.4],
                0.50: [-0.8 * side, 0.1, -2.8 * power], 0.66: [0, 0, 0],
                0.97: [-1.2 * side, 0.6, 2.0],
                1.15: [-0.25, 0, -3.8 * power], 1.28: [-0.2, 0, -3.4 * power],
                1.40: [0, 0, 0],
            },
        },
        "right_leg": {
            "rotation": {
                0.0: [0, 0, 0], 0.25: [-18, 0, 0], 0.50: [22, 0, 0],
                0.66: [42, 0, 0], 0.80: [-104, 8 * side, 12 * side],
                0.97: [-68, 4 * side, 8 * side], 1.15: [-12, 0, 0],
                1.40: [0, 0, 0],
            },
            "position": {
                0.0: [0, 0, 0], 0.80: [0.3 * side, 0.4, -3.6 * power],
                0.97: [0.15 * side, 0.2, -2.0], 1.15: [0, 0, 0],
                1.40: [0, 0, 0],
            },
        },
        "left_leg": {"rotation": {
            0.0: [0, 0, 0], 0.25: [18, 0, 0], 0.50: [-20, 0, 0],
            0.66: [-34, 0, 0], 0.80: [38, 0, 0], 0.97: [24, 0, 0],
            1.15: [16, 0, 0], 1.40: [0, 0, 0],
        }},
    }
    impacts = (0.25, 0.50, 0.80, 1.15)
    return clip(
        1.40,
        bones,
        sound_effects={
            time: {"effect": "xeno:rush_finish" if time == 1.15 else "xeno:rush_hit"}
            for time in impacts
        },
        particle_effects={
            time: {
                "effect": "xeno:rush_finish" if time == 1.15 else "xeno:rush_trail",
                "locator": "right_arm" if time in (0.25, 1.15) else "left_arm",
            }
            for time in impacts
        },
        timeline={
            time: "xeno:rush_finish" if time == 1.15 else "xeno:rush_pulse"
            for time in impacts
        },
    )


def dmz_one_handed_punches() -> dict[str, dict]:
    """Read the two real DMZ punch clips so our aliases never approximate their movement."""
    jars = sorted(Path("libs").glob("dragonminez-*.jar"))
    if not jars:
        raise FileNotFoundError("No libs/dragonminez-*.jar available for DMZ punch import")
    resource = "assets/dragonminez/animations/entity/races/combat.animation.json"
    with ZipFile(jars[-1]) as archive:
        animations = json.loads(archive.read(resource))["animations"]
    return {
        side: animations["combat.one_handed_punch_%s" % side]
        for side in ("left", "right")
    }


def _sx(side, xyz):
    """Mirror only X for the left arm. +Z is pull-back / -Z is shoot-forward on both sides."""
    x, y, z = xyz
    return [x, y, z] if side == "right" else [-x, y, z]


def punch(length, side, *, hips, waist, chamber_rot, extend_rot, recoil_rot,
          chamber_pos, extend_pos, other_rot, extra=None, impact_scale=(1.0, 1.0, 1.35)):
    """A punch that actually travels.

    Rotation alone leaves the fist glued to the shoulder pivot, so jab/hook/body/heavy all
    read as the same arm twitch. DragonMineZ's own ``one_handed_punch`` chambers with a
    +Z position (pull back) and hits with a -Z position (shoot forward), plus a root-Z
    squash on impact. That is the silhouette language these clips copy.
    """
    arm = f"{side}_arm"
    off = "left_arm" if side == "right" else "right_arm"
    rest = GUARD_R if side == "right" else GUARD_L
    off_rest = GUARD_L if side == "right" else GUARD_R
    t_ch, t_ex, t_hold = length * 0.14, length * 0.40, length * 0.62
    bones = {
        "root": {
            "rotation": {0.0: [0, 0, 0], t_ch: [0, hips * 0.35, 0], t_ex: [0, hips, 0], length: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], t_ex: list(impact_scale), t_hold: list(impact_scale), length: [1, 1, 1]},
        },
        "waist": {
            "rotation": {
                0.0: [0, 0, 0],
                t_ch: [waist[0] * 0.25, -hips * 0.35, 0],
                t_ex: waist,
                length: [0, 0, 0],
            }
        },
        arm: {
            "rotation": {0.0: rest, t_ch: chamber_rot, t_ex: extend_rot, t_hold: recoil_rot, length: rest},
            "position": {0.0: [0, 0, 0], t_ch: chamber_pos, t_ex: extend_pos, t_hold: extend_pos, length: [0, 0, 0]},
        },
        off: {"rotation": {0.0: off_rest, t_ex: other_rot, length: off_rest}},
    }
    if extra:
        bones.update(extra)
    return clip(length, bones)


def kick(length, leg, wind, strike_pose, recoil, *, hips, waist, arms=None):
    """A kick: small windup, drive through, recover. Support leg braces."""
    other = "right_leg" if leg == "left_leg" else "left_leg"
    t_w, t_s, t_r = length * 0.22, length * 0.45, length * 0.65
    bones = {
        "root": {"rotation": {0.0: [0, 0, 0], t_s: [0, hips, 0], length: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], t_s: waist, length: [0, 0, 0]}},
        leg: {"rotation": {0.0: [0, 0, 0], t_w: wind, t_s: strike_pose, t_r: recoil, length: [0, 0, 0]}},
        other: {"rotation": {0.0: [0, 0, 0], t_s: [8, 0, 0], length: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: GUARD_L, t_s: (arms or (GUARD_L, GUARD_R))[0], length: GUARD_L}},
        "right_arm": {"rotation": {0.0: GUARD_R, t_s: (arms or (GUARD_L, GUARD_R))[1], length: GUARD_R}},
    }
    return clip(length, bones)


def build() -> dict:
    a = {}

    # Jab: a dart. Small hip, fist pulls back then pokes straight. Must not look like a hook.
    for side, hips in (("right", -20), ("left", 20)):
        s = 1 if side == "right" else -1
        a[f"combat.xeno_jab_{side}"] = punch(
            0.28, side, hips=hips, waist=[-4, 14 * s, 0],
            chamber_rot=[-72, 10 * s, 8 * s],
            extend_rot=[-88, 24 * s, 6 * s],
            recoil_rot=[-80, 12 * s, 4 * s],
            chamber_pos=_sx(side, [0.4, 0.7, 3.6]),
            extend_pos=_sx(side, [0.2, -0.2, -2.8]),
            other_rot=[-58, 0, -18 * s],
            extra={"head": {"rotation": {0.0: [0, 0, 0], 0.11: [3, 6 * s, 0], 0.28: [0, 0, 0]}}},
            impact_scale=(1.0, 1.0, 1.22),
        )

    # Hook: a swing. Arm cocks out to the SIDE then whips across the face. Big yaw.
    for side, hips in (("right", -48), ("left", 48)):
        s = 1 if side == "right" else -1
        a[f"combat.xeno_hook_{side}"] = punch(
            0.42, side, hips=hips, waist=[-6, 38 * s, 12 * s],
            chamber_rot=[-35, 58 * s, 88 * s],
            extend_rot=[-78, -28 * s, -18 * s],
            recoil_rot=[-60, -8 * s, 20 * s],
            chamber_pos=_sx(side, [2.4, 0.8, 1.6]),
            extend_pos=_sx(side, [-0.9, 0.3, -2.4]),
            other_rot=[-70, 8 * s, -28 * s],
            extra={"head": {"rotation": {0.0: [0, 0, 0], 0.17: [4, 16 * s, 0], 0.42: [0, 0, 0]}}},
            impact_scale=(1.0, 1.0, 1.28),
        )

    # Body punch: a shovel into the ribs. Torso folds forward, fist stays LOW.
    for side, hips in (("right", -28), ("left", 28)):
        s = 1 if side == "right" else -1
        a[f"combat.xeno_body_punch_{side}"] = punch(
            0.40, side, hips=hips, waist=[38, 22 * s, 0],
            chamber_rot=[-40, 18 * s, 20 * s],
            extend_rot=[-52, 16 * s, 8 * s],
            recoil_rot=[-44, 8 * s, 6 * s],
            chamber_pos=_sx(side, [0.5, 0.4, 2.4]),
            extend_pos=_sx(side, [0.5, -1.8, -2.2]),
            other_rot=[-48, 0, -22 * s],
            extra={
                "head": {"rotation": {0.0: [0, 0, 0], 0.16: [22, 8 * s, 0], 0.40: [0, 0, 0]}},
                "waist": {
                    "rotation": {0.0: [0, 0, 0], 0.16: [38, 22 * s, 0], 0.40: [0, 0, 0]},
                    "position": {0.0: [0, 0, 0], 0.16: [0, -1.4, -0.6], 0.40: [0, 0, 0]},
                },
            },
            impact_scale=(1.0, 1.0, 1.18),
        )

    # Heavy finish: both arms, a step, the biggest turn. Must not read as another jab.
    a["combat.xeno_heavy_finish"] = clip(0.70, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.16: [8, 10, 0], 0.38: [-6, -12, 0], 0.70: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.38: [1.05, 1.0, 1.45], 0.52: [1.05, 1.0, 1.45], 0.70: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.16: [-10, 12, 0], 0.38: [8, -14, 6], 0.70: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.16: [6, 14, 0], 0.38: [10, -16, 0], 0.70: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: GUARD_R, 0.16: [-28, 48, 55], 0.38: [-110, 8, -8], 0.52: [-96, 4, -4], 0.70: GUARD_R},
            "position": {0.0: [0, 0, 0], 0.16: [1.2, 1.2, 4.2], 0.38: [0.3, -0.4, -3.4], 0.52: [0.2, -0.2, -2.6], 0.70: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: GUARD_L, 0.16: [-50, -20, -36], 0.38: [-92, -18, -24], 0.52: [-70, -8, -16], 0.70: GUARD_L},
            "position": {0.0: [0, 0, 0], 0.16: [-0.6, 0.4, 1.8], 0.38: [-0.4, -0.2, -1.6], 0.70: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.16: [18, 8, 0], 0.38: [-22, 0, 0], 0.70: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.16: [-12, 0, 0], 0.38: [16, -8, 0], 0.70: [0, 0, 0]}},
    })

    # --- Beat 6: a jumping kick. Both legs leave the ground, so the support leg tucks.
    a["combat.xeno_flying_kick"] = clip(0.48, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.2: [-14, 0, 0], 0.48: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.2: [0, 4, 0], 0.34: [0, 3, 0], 0.48: [0, 0, 0]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [-10, 0, 0], 0.48: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.14: [30, 0, 0], 0.24: [-88, 0, 0],
                                   0.34: [-64, 0, 0], 0.48: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.2: [42, 0, 0], 0.34: [30, 0, 0], 0.48: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: GUARD_L, 0.22: [-30, 0, -40], 0.48: GUARD_L}},
        "right_arm": {"rotation": {0.0: GUARD_R, 0.22: [-64, 0, 26], 0.48: GUARD_R}},
    })

    # --- Beats 1 and 7 are travel poses. The server owns the movement; these only sell it, so they
    # lean into the direction of travel rather than displacing the fighter.
    a["combat.xeno_step_in_dash"] = clip(0.28, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.12: [-18, 0, 0], 0.28: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.12: [-10, 6, 0], 0.28: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.12: [-34, 0, 0], 0.28: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.12: [26, 0, 0], 0.28: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: GUARD_L, 0.12: [-52, 0, -18], 0.28: GUARD_L}},
        "right_arm": {"rotation": {0.0: GUARD_R, 0.12: [-66, 0, 20], 0.28: GUARD_R}},
    })

    a["combat.xeno_rush_in_chase"] = clip(0.34, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.16: [-30, 0, 0], 0.34: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.16: [-14, 0, 0], 0.34: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.16: [-40, 0, 0], 0.34: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.16: [34, 0, 0], 0.34: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: GUARD_L, 0.16: [-14, 0, -52], 0.34: GUARD_L}},
        "right_arm": {"rotation": {0.0: GUARD_R, 0.16: [-20, 0, 56], 0.34: GUARD_R}},
    })

    # --- Kicks DMZ has no equivalent for.
    a["combat.xeno_knee_right"] = kick(
        0.32, "right_leg", wind=[10, 0, 0], strike_pose=[-96, -10, 0], recoil=[-60, -6, 0],
        hips=-18, waist=[12, 10, 0], arms=([-24, 0, -34], [-30, 0, 40]))

    a["combat.xeno_knee_left"] = kick(
        0.32, "left_leg", wind=[10, 0, 0], strike_pose=[-96, 10, 0], recoil=[-60, 6, 0],
        hips=18, waist=[12, -10, 0], arms=([-30, 0, -40], [-24, 0, 34]))

    a["combat.xeno_high_roundhouse"] = kick(
        0.46, "right_leg", wind=[24, -20, 0], strike_pose=[-70, -48, 0], recoil=[-40, -24, 0],
        hips=-34, waist=[-8, 26, 0], arms=([-16, 0, -50], [-38, 0, 30]))

    a["combat.xeno_spinning_back_kick"] = kick(
        0.54, "left_leg", wind=[26, 30, 0], strike_pose=[-64, 54, 0], recoil=[-36, 28, 0],
        hips=48, waist=[-6, -34, 0], arms=([-20, 0, -46], [-26, 0, 44]))

    # 360 in place, then a strike. Root yaw only during the spin so the fighter stays planted.
    a["combat.xeno_spin_hook_left"] = spin_then_hook("left")
    a["combat.xeno_spin_hook_right"] = spin_then_hook("right")
    a["combat.xeno_spin_uppercut_left"] = spin_then_uppercut("left")
    a["combat.xeno_spin_kick_right"] = spin_then_kick("right")

    # --- Hakai. Not a combo beat: HakaiChannelSystem broadcasts the hold every second while
    # the channel runs and the snap once on commit, so these are named directly by
    # DmzAnimHelper.HAKAI_HOLD / HAKAI_FIRE rather than through an animation intent.
    #
    # These two used to be hand-written into the generated JSON, which meant re-running this
    # script silently deleted them - and it did, once. They belong here.
    a["combat.xeno_hakai_hold"] = clip(1.2, {
        # Palm out at the target and held there. Re-triggered every 20 ticks, so the clip has to
        # still read as a held pose at its own end rather than settling back to neutral.
        "root": {"rotation": {0.0: [0, -8, 0], 0.5: [0, -10, 0], 1.2: [0, -8, 0]}},
        "waist": {"rotation": {0.0: [-4, -10, 0], 0.6: [-6, -12, 0], 1.2: [-4, -10, 0]}},
        "right_arm": {
            "rotation": {0.0: [-96, -12, 4], 0.35: [-100, -10, 6], 0.75: [-97, -13, 3],
                         1.2: [-96, -12, 4]},
            "position": {0.0: [0.2, 0.2, -1.8], 0.35: [0.2, 0.3, -2.1],
                         0.75: [0.2, 0.2, -1.9], 1.2: [0.2, 0.2, -1.8]},
        },
        # Off hand low and back, out of the way of the erasure.
        "left_arm": {"rotation": {0.0: [-14, 10, -26], 0.6: [-18, 12, -28], 1.2: [-14, 10, -26]}},
        "right_leg": {"rotation": {0.0: [-6, 0, 0], 1.2: [-6, 0, 0]}},
        "left_leg": {"rotation": {0.0: [6, 0, 0], 1.2: [6, 0, 0]}},
    })

    a["combat.xeno_hakai_fire"] = clip(0.4, {
        # Draw back a fraction, then a hard palm thrust, held while the target comes apart.
        "root": {
            "rotation": {0.0: [0, -8, 0], 0.08: [0, 6, 0], 0.18: [0, -24, 0], 0.28: [0, -22, 0],
                         0.4: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.18: [1.0, 1.0, 1.35], 0.28: [1.0, 1.0, 1.35],
                      0.4: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [-4, -10, 0], 0.08: [-8, 14, 0], 0.18: [6, -18, 0],
                               0.4: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-96, -12, 4], 0.08: [-70, -16, 22], 0.18: [-108, -6, 0],
                         0.28: [-106, -6, 0], 0.4: GUARD_R},
            "position": {0.0: [0.2, 0.2, -1.8], 0.08: [0.4, 0.4, 1.6], 0.18: [0.2, 0.0, -3.8],
                         0.28: [0.2, 0.0, -3.6], 0.4: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-14, 10, -26], 0.18: [-34, 14, -40], 0.4: GUARD_L}},
        "right_leg": {"rotation": {0.0: [-6, 0, 0], 0.18: [-18, 0, 0], 0.4: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [6, 0, 0], 0.18: [16, 0, 0], 0.4: [0, 0, 0]}},
    })

    # Comparison twins. Original keys stay; *_v2 sits next to them in /xenobt3 list,
    # and *_v3 next to both - three generations shipped side by side so they can be
    # compared in play rather than one quietly replacing another.
    a.update(build_v2_from(a))
    # Cross V1/V2 intentionally use Xeno-owned copies of DMZ's real one-handed punches.
    # Keep both names present so the catalog never falls back to a stock DMZ animation.
    dmz_punches = dmz_one_handed_punches()
    for side in ("left", "right"):
        a["combat.xeno_cross_%s" % side] = dmz_punches[side]
        a["combat.xeno_cross_%s_v2" % side] = dmz_punches[side]
    a.update(build_v3())
    a.update(build_v4())
    for profile in RUSH_PROFILES:
        a["combat.xeno_cinematic_rush_" + profile] = cinematic_rush(profile)
    return {"format_version": "1.8.0", "animations": a}


def _spin_root(strike_yaw):
    """0→360 yaw, then the strike's hip yaw. 360 and 0 are the same pose."""
    return {
        0.0: [0, 0, 0],
        0.10: [0, 90, 0],
        0.20: [0, 180, 0],
        0.30: [0, 270, 0],
        0.40: [0, 360, 0],
        0.52: [0, strike_yaw, 0],
        0.70: [0, 0, 0],
    }


def spin_then_hook(side: str) -> dict:
    s = 1 if side == "right" else -1
    arm = f"{side}_arm"
    off = "left_arm" if side == "right" else "right_arm"
    rest = GUARD_R if side == "right" else GUARD_L
    off_rest = GUARD_L if side == "right" else GUARD_R
    return clip(0.70, {
        "root": {"rotation": _spin_root(-48 * s)},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.40: [0, 0, 0], 0.52: [-6, 38 * s, 12 * s], 0.70: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.20: [4, 20 * s, 0], 0.40: [0, 0, 0], 0.52: [4, 16 * s, 0], 0.70: [0, 0, 0]}},
        arm: {
            "rotation": {
                0.0: rest, 0.40: rest,
                0.46: [-35, 58 * s, 88 * s],
                0.54: [-78, -28 * s, -18 * s],
                0.70: rest,
            },
            "position": {
                0.0: [0, 0, 0], 0.40: [0, 0, 0],
                0.46: _sx(side, [2.4, 0.8, 1.6]),
                0.54: _sx(side, [-0.9, 0.3, -2.4]),
                0.70: [0, 0, 0],
            },
        },
        off: {"rotation": {0.0: off_rest, 0.54: [-70, 8 * s, -28 * s], 0.70: off_rest}},
    })


def spin_then_uppercut(side: str) -> dict:
    s = 1 if side == "right" else -1
    arm = f"{side}_arm"
    off = "left_arm" if side == "right" else "right_arm"
    rest = GUARD_R if side == "right" else GUARD_L
    off_rest = GUARD_L if side == "right" else GUARD_R
    return clip(0.70, {
        "root": {"rotation": _spin_root(22 * s)},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.40: [0, 0, 0], 0.54: [-18, 16 * s, 0], 0.70: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.20: [6, 18 * s, 0], 0.40: [0, 0, 0], 0.54: [-12, 8 * s, 0], 0.70: [0, 0, 0]}},
        arm: {
            "rotation": {
                0.0: rest, 0.40: rest,
                0.46: [-40, 12 * s, 20 * s],
                0.54: [-140, 8 * s, -6 * s],
                0.70: rest,
            },
            "position": {
                0.0: [0, 0, 0], 0.40: [0, 0, 0],
                0.46: _sx(side, [0.4, -0.6, 1.8]),
                0.54: _sx(side, [0.2, 2.4, -1.6]),
                0.70: [0, 0, 0],
            },
        },
        off: {"rotation": {0.0: off_rest, 0.54: [-50, 0, -16 * s], 0.70: off_rest}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.54: [10, 0, 0], 0.70: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.54: [-8, 0, 0], 0.70: [0, 0, 0]}},
    })


def spin_then_kick(side: str) -> dict:
    s = 1 if side == "right" else -1
    leg = f"{side}_leg"
    other = "left_leg" if side == "right" else "right_leg"
    return clip(0.72, {
        "root": {"rotation": _spin_root(-34 * s)},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.40: [0, 0, 0], 0.54: [-8, 26 * s, 0], 0.72: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.20: [4, 22 * s, 0], 0.40: [0, 0, 0], 0.54: [6, 10 * s, 0], 0.72: [0, 0, 0]}},
        leg: {"rotation": {
            0.0: [0, 0, 0], 0.40: [0, 0, 0],
            0.46: [24, -20 * s, 0],
            0.54: [-70, -48 * s, 0],
            0.62: [-40, -24 * s, 0],
            0.72: [0, 0, 0],
        }},
        other: {"rotation": {0.0: [0, 0, 0], 0.54: [8, 0, 0], 0.72: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: GUARD_L, 0.54: [-16, 0, -50], 0.72: GUARD_L}},
        "right_arm": {"rotation": {0.0: GUARD_R, 0.54: [-38, 0, 30], 0.72: GUARD_R}},
    })


def boxing_jab(side: str) -> dict:
    """Both fists at the shoulders; a short poke; return to the same guard."""
    punch = f"{side}_arm"
    other = "right_arm" if side == "left" else "left_arm"
    punch_guard = BOX_L if side == "left" else BOX_R
    other_guard = BOX_R if side == "left" else BOX_L
    s = 1 if side == "right" else -1
    poke = [-102, 10 * s, 24 * s]
    return clip(0.24, {
        punch: {
            "rotation": {0.0: punch_guard, 0.08: poke, 0.14: poke, 0.24: punch_guard},
            "position": {0.0: [0, 0, 0], 0.08: [0, 0.15, -1.15], 0.14: [0, 0.1, -0.9], 0.24: [0, 0, 0]},
        },
        other: {"rotation": {0.0: other_guard, 0.24: other_guard}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.08: [0, 6 * s, 0], 0.24: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.08: [2, 4 * s, 0], 0.24: [0, 0, 0]}},
    })


def yaw_scale(clip_data: dict, factor: float) -> dict:
    """Cut root/waist yaw so v2 reads more forward than the original."""
    bones = {}
    for bone, channels in clip_data.get("bones", {}).items():
        if bone in ("root", "waist") and "rotation" in channels:
            rot = {}
            for t, frame in channels["rotation"].items():
                vec = frame["vector"]
                rot[t] = {"vector": [vec[0], vec[1] * factor, vec[2]]}
            copied = dict(channels)
            copied["rotation"] = rot
            bones[bone] = copied
        else:
            bones[bone] = channels
    return {"animation_length": clip_data["animation_length"], "bones": bones}


def build_v2_from(v1: dict) -> dict:
    """Rewritten twins. Jabs are the boxing-guard version; others keep identity with less side yaw."""
    out = {
        "combat.xeno_jab_left_v2": boxing_jab("left"),
        "combat.xeno_jab_right_v2": boxing_jab("right"),
    }
    for key, clip_data in v1.items():
        if key.endswith("_jab_left") or key.endswith("_jab_right"):
            continue
        # Hakai is a channel pose, not a combo beat, and has no twin.
        if "hakai" in key:
            continue
        out[key + "_v2"] = yaw_scale(clip_data, 0.45)
    return out



# ---------------------------------------------------------------------------
# v3: a real second authoring pass, posed for Budokai Tenkaichi 3.
#
# v2 was not one. ``build_v2_from`` hand-writes two boxing jabs and derives
# everything else by running ``yaw_scale`` over v1, so until now there has only
# ever been one set of poses. v3 is written from scratch against three
# constraints:
#
# 1. Beat length. The held mash fires a beat every six ticks (0.30s) and scales
#    each clip to fit, so a 0.70s spin had to run at 2.4x and read as a smear.
#    v3 clips are authored at 0.22-0.44s, so nothing needs more than about 1.5x
#    and the 360s stay legible.
# 2. Snap and hold. A BT3 strike is mostly impact frame: a short chamber, a fast
#    snap out, then the pose *held* while the hit registers, then a quick
#    recovery. Keying the same pose twice is what freezes it, and that hold is
#    the single biggest difference from v1's even ease-in/ease-out.
# 3. Recovery into guard, never neutral. These chain sixteen deep, so every clip
#    starts and ends on the same ready pose. A string then reads as one flurry
#    instead of sixteen separate moves returning to attention.
#
# Also: no ``head`` channel anywhere in v3. ``DmzMeleeHeadLookMixin`` zeroes the
# head bone for the whole mash window so it follows the body, and any head
# keyframe here would simply be overwritten - the body has to carry the motion.

# Ready stance. The rig has no elbow - each arm is one straight bone - so a real
# boxing guard is not reachable: any pose with the fists near the face leaves the
# whole arm sticking horizontally out of the shoulder, which renders as a zombie
# T-pose. Checked on a contact sheet at -74 and it looked exactly that bad. So the
# stance is a low ready instead: forward, angled down, slightly out from the ribs.
G3_R = [-38, -8, 20]
G3_L = [-38, 8, -20]


def _g3(side):
    return G3_R if side == "right" else G3_L


def v3_strike(length, side, *, hips, waist_turn, chamber, extend, chamber_pos,
              extend_pos, off_chamber, off_impact, legs=None, squash=1.32,
              waist_pitch=0.0):
    """One BT3 strike: chamber, snap, hold the impact, recover to guard.

    ``hips`` is the root yaw at impact - the hip drive every strike rides on -
    and the root coils the opposite way first. ``waist_turn`` counter-rotates on
    the chamber and then whips past it, which is what makes the shoulder arrive
    after the hip rather than with it.
    """
    arm = side + "_arm"
    off = "left_arm" if side == "right" else "right_arm"
    rest = _g3(side)
    off_rest = _g3("left" if side == "right" else "right")

    t_ch = round(length * 0.18, 4)   # chamber - anticipation only, kept short
    t_hit = round(length * 0.42, 4)  # snap out
    t_end = round(length * 0.66, 4)  # ...and hold it here
    impact = [1.0, 1.0, squash]

    bones = {
        "root": {
            "rotation": {
                0.0: [0, 0, 0],
                t_ch: [0, -hips * 0.30, 0],   # coil away before driving through
                t_hit: [0, hips, 0],
                t_end: [0, hips * 0.92, 0],
                length: [0, 0, 0],
            },
            "scale": {0.0: [1, 1, 1], t_hit: impact, t_end: impact, length: [1, 1, 1]},
        },
        "waist": {
            "rotation": {
                0.0: [0, 0, 0],
                t_ch: [waist_pitch * 0.3, -waist_turn * 0.45, 0],
                t_hit: [waist_pitch, waist_turn, 0],
                t_end: [waist_pitch * 0.8, waist_turn * 0.9, 0],
                length: [0, 0, 0],
            }
        },
        arm: {
            "rotation": {0.0: rest, t_ch: chamber, t_hit: extend, t_end: extend, length: rest},
            "position": {0.0: [0, 0, 0], t_ch: chamber_pos, t_hit: extend_pos,
                         t_end: extend_pos, length: [0, 0, 0]},
        },
        off: {
            "rotation": {0.0: off_rest, t_ch: off_chamber, t_hit: off_impact,
                         t_end: off_impact, length: off_rest}
        },
    }
    if legs:
        bones.update(legs)
    else:
        # Weight steps onto the striking side; the back leg extends behind it.
        front = "right_leg" if side == "right" else "left_leg"
        back = "left_leg" if side == "right" else "right_leg"
        bones[front] = {"rotation": {0.0: [0, 0, 0], t_ch: [12, 0, 0], t_hit: [-20, 0, 0],
                                     t_end: [-16, 0, 0], length: [0, 0, 0]}}
        bones[back] = {"rotation": {0.0: [0, 0, 0], t_ch: [-8, 0, 0], t_hit: [16, 0, 0],
                                    t_end: [13, 0, 0], length: [0, 0, 0]}}
    return clip(length, bones)


def v3_kick(length, leg, *, hips, waist_turn, chamber, extend, hold=None,
            arms=None, root_pitch=0.0, squash=1.22):
    """A kick on the same chamber / snap / hold / recover shape as v3_strike."""
    other = "right_leg" if leg == "left_leg" else "left_leg"
    t_ch = round(length * 0.20, 4)
    t_hit = round(length * 0.44, 4)
    t_end = round(length * 0.68, 4)
    hold = hold or extend
    a_l, a_r = arms or (G3_L, G3_R)
    impact = [1.0, 1.0, squash]

    return clip(length, {
        "root": {
            "rotation": {0.0: [0, 0, 0], t_ch: [root_pitch * 0.4, -hips * 0.25, 0],
                         t_hit: [root_pitch, hips, 0],
                         t_end: [root_pitch * 0.8, hips * 0.9, 0], length: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], t_hit: impact, t_end: impact, length: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], t_ch: [0, -waist_turn * 0.4, 0],
                               t_hit: [0, waist_turn, 0], t_end: [0, waist_turn * 0.85, 0],
                               length: [0, 0, 0]}},
        leg: {"rotation": {0.0: [0, 0, 0], t_ch: chamber, t_hit: extend, t_end: hold,
                           length: [0, 0, 0]}},
        # Support leg braces, then straightens as the kick lands.
        other: {"rotation": {0.0: [0, 0, 0], t_ch: [10, 0, 0], t_hit: [-6, 0, 0],
                             t_end: [-4, 0, 0], length: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G3_L, t_ch: G3_L, t_hit: a_l, t_end: a_l, length: G3_L}},
        "right_arm": {"rotation": {0.0: G3_R, t_ch: G3_R, t_hit: a_r, t_end: a_r, length: G3_R}},
    })


def v3_spin(length, side, *, strike_bones, hips):
    """A full 360 on the spot, then the strike. The turn owns the first 55%.

    v1 gave the spin 0.40s of a 0.70s clip, and the mash then ran the whole
    thing at 2.4x, which is why it read as a blur. Here the turn is four
    keyframes across 0.24s of a 0.44s clip and plays at about 1.45x - fast, but
    a turn you can follow. 360 and 0 are the same pose, so the fighter ends
    facing where they started.
    """
    q = round(length * 0.55, 4)
    t_hit = round(length * 0.74, 4)
    t_end = round(length * 0.88, 4)
    spin = {
        0.0: [0, 0, 0],
        round(q * 0.25, 4): [0, 90, 0],
        round(q * 0.50, 4): [0, 180, 0],
        round(q * 0.75, 4): [0, 270, 0],
        q: [0, 360, 0],
        t_hit: [0, 360 + hips, 0],
        t_end: [0, 360 + hips * 0.9, 0],
        length: [0, 360, 0],
    }

    bones = {
        "root": {
            "rotation": spin,
            "scale": {0.0: [1, 1, 1], t_hit: [1.0, 1.0, 1.28], t_end: [1.0, 1.0, 1.28],
                      length: [1, 1, 1]},
        },
        # Arms tuck in through the turn - a spinning fighter pulls the mass in.
        "left_arm": {"rotation": {0.0: G3_L, q: [-84, 26, -46], length: G3_L}},
        "right_arm": {"rotation": {0.0: G3_R, q: [-84, -26, 46], length: G3_R}},
        "waist": {"rotation": {0.0: [0, 0, 0], q: [-6, 0, 0], t_hit: [8, 0, 0],
                               length: [0, 0, 0]}},
    }
    for bone, channels in strike_bones(q, t_hit, t_end, length).items():
        target = bones.setdefault(bone, {})
        for channel, frames in channels.items():
            target[channel] = {**target.get(channel, {}), **frames}
    return clip(length, bones)


def build_v3() -> dict:
    """Every v3 clip. Names are the v1 key plus ``_v3``, so all three coexist."""
    a = {}

    # --- Straight punches -------------------------------------------------
    # Jab: the fastest thing in the set. Almost no chamber, a dart, a held
    # frame barely long enough to see, straight back to guard.
    for side, hips in (("right", -16), ("left", 16)):
        s = 1 if side == "right" else -1
        a["combat.xeno_jab_%s_v3" % side] = v3_strike(
            0.24, side, hips=hips, waist_turn=14 * s, waist_pitch=-3,
            chamber=[-78, 12 * s, 34 * s],
            extend=[-94, 20 * s, 8 * s],
            chamber_pos=_sx(side, [0.3, 0.4, 2.6]),
            extend_pos=_sx(side, [0.2, -0.2, -3.4]),
            off_chamber=_g3("left" if side == "right" else "right"),
            off_impact=[-80, -10 * s, -44 * s],
            squash=1.18)

    # Cross: the mash's bread and butter, and new in v3 - beats 1-5 used to run
    # DragonMineZ's 0.5s one_handed_punch at 1.67x. Bigger hip than the jab,
    # rear shoulder driving all the way through.
    for side, hips in (("right", -30), ("left", 30)):
        s = 1 if side == "right" else -1
        a["combat.xeno_cross_%s_v3" % side] = v3_strike(
            0.28, side, hips=hips, waist_turn=26 * s, waist_pitch=-5,
            chamber=[-64, 16 * s, 46 * s],
            extend=[-98, 26 * s, 4 * s],
            chamber_pos=_sx(side, [0.6, 0.6, 3.4]),
            extend_pos=_sx(side, [0.3, -0.3, -4.2]),
            off_chamber=[-78, -12 * s, -42 * s],
            off_impact=[-86, -18 * s, -50 * s],
            squash=1.34)

    # Hook: travels around rather than through. Big hip, arm swings on Y.
    for side, hips in (("right", -38), ("left", 38)):
        s = 1 if side == "right" else -1
        a["combat.xeno_hook_%s_v3" % side] = v3_strike(
            0.30, side, hips=hips, waist_turn=32 * s, waist_pitch=-4,
            chamber=[-72, -30 * s, 58 * s],
            extend=[-84, 54 * s, 26 * s],
            chamber_pos=_sx(side, [1.2, 0.5, 2.2]),
            extend_pos=_sx(side, [-1.6, -0.2, -2.6]),
            off_chamber=[-80, -14 * s, -44 * s],
            off_impact=[-88, -22 * s, -52 * s],
            squash=1.26)

    # Uppercut: drives up from the legs. Also new in v3 for the same reason as
    # the cross. Waist pitches back, arm rises through the target.
    for side, hips in (("right", -22), ("left", 22)):
        s = 1 if side == "right" else -1
        a["combat.xeno_uppercut_%s_v3" % side] = v3_strike(
            0.30, side, hips=hips, waist_turn=18 * s, waist_pitch=-16,
            chamber=[-34, 8 * s, 52 * s],
            extend=[-128, 14 * s, 20 * s],
            chamber_pos=_sx(side, [0.4, -1.6, 1.4]),
            extend_pos=_sx(side, [0.2, 2.0, -2.2]),
            off_chamber=[-76, -10 * s, -42 * s],
            off_impact=[-92, -16 * s, -48 * s],
            squash=1.20)

    # Body blow: the fighter drops under the guard line, so the waist pitches
    # forward hard and the arm comes in low and flat.
    for side, hips in (("right", -26), ("left", 26)):
        s = 1 if side == "right" else -1
        a["combat.xeno_body_punch_%s_v3" % side] = v3_strike(
            0.28, side, hips=hips, waist_turn=22 * s, waist_pitch=30,
            chamber=[-54, 14 * s, 48 * s],
            extend=[-62, 26 * s, 10 * s],
            chamber_pos=_sx(side, [0.5, 0.2, 3.0]),
            extend_pos=_sx(side, [0.2, -0.6, -3.6]),
            off_chamber=[-74, -12 * s, -44 * s],
            off_impact=[-84, -18 * s, -50 * s],
            squash=1.24)

    # --- Heavy finish -----------------------------------------------------
    # The string-ender: both arms, a real step, the deepest squash and the
    # longest hold in the set. It should look like it costs something.
    a["combat.xeno_heavy_finish_v3"] = clip(0.42, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.08: [10, 20, 0], 0.18: [-10, -34, 0],
                         0.30: [-8, -30, 0], 0.42: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.18: [1.06, 0.96, 1.50], 0.30: [1.06, 0.96, 1.50],
                      0.42: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.08: [-14, 20, 0], 0.18: [12, -26, 8],
                               0.30: [10, -22, 6], 0.42: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: G3_R, 0.08: [-30, 42, 62], 0.18: [-116, 10, -6],
                         0.30: [-112, 8, -4], 0.42: G3_R},
            "position": {0.0: [0, 0, 0], 0.08: [1.4, 1.0, 4.4], 0.18: [0.3, -0.4, -4.6],
                         0.30: [0.3, -0.4, -4.4], 0.42: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: G3_L, 0.08: [-56, -24, -40], 0.18: [-100, -20, -22],
                         0.30: [-96, -18, -20], 0.42: G3_L},
            "position": {0.0: [0, 0, 0], 0.08: [-0.8, 0.4, 2.0], 0.18: [-0.5, -0.3, -2.2],
                         0.42: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [22, 8, 0], 0.18: [-30, 0, 0],
                                   0.30: [-26, 0, 0], 0.42: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [-16, 0, 0], 0.18: [22, -10, 0],
                                  0.30: [20, -8, 0], 0.42: [0, 0, 0]}},
    })

    # --- Kicks ------------------------------------------------------------
    # Flying kick: the one clip that leaves the floor, so root position lifts
    # and the support leg tucks under instead of bracing.
    a["combat.xeno_flying_kick_v3"] = clip(0.36, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.10: [12, 0, 0], 0.18: [-20, 0, 0],
                         0.26: [-18, 0, 0], 0.36: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.10: [0, 1.5, 0], 0.18: [0, 5.0, 0],
                         0.26: [0, 4.2, 0], 0.36: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.18: [1.0, 1.0, 1.26], 0.26: [1.0, 1.0, 1.26],
                      0.36: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.18: [-14, 0, 0], 0.36: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [34, 0, 0], 0.18: [-104, 0, 0],
                                   0.26: [-98, 0, 0], 0.36: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [16, 0, 0], 0.18: [52, 0, 0],
                                  0.26: [46, 0, 0], 0.36: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G3_L, 0.18: [-38, 16, -56], 0.26: [-38, 16, -56],
                                  0.36: G3_L}},
        "right_arm": {"rotation": {0.0: G3_R, 0.18: [-70, -16, 34], 0.26: [-70, -16, 34],
                                   0.36: G3_R}},
    })

    a["combat.xeno_knee_right_v3"] = v3_kick(
        0.26, "right_leg", hips=-20, waist_turn=14, root_pitch=14,
        chamber=[16, -6, 0], extend=[-108, -12, 0], hold=[-102, -10, 0],
        arms=([-40, 18, -60], [-46, -18, 56]), squash=1.24)

    a["combat.xeno_knee_left_v3"] = v3_kick(
        0.26, "left_leg", hips=20, waist_turn=-14, root_pitch=14,
        chamber=[16, 6, 0], extend=[-108, 12, 0], hold=[-102, 10, 0],
        arms=([-46, 18, -56], [-40, -18, 60]), squash=1.24)

    a["combat.xeno_high_roundhouse_v3"] = v3_kick(
        0.34, "right_leg", hips=-42, waist_turn=-14, root_pitch=-6,
        chamber=[28, -26, 0], extend=[-78, -58, 0], hold=[-72, -54, 0],
        arms=([-30, 20, -64], [-52, -8, 30]), squash=1.30)

    a["combat.xeno_spinning_back_kick_v3"] = v3_kick(
        0.38, "left_leg", hips=56, waist_turn=-40, root_pitch=-4,
        chamber=[30, 34, 0], extend=[-72, 62, 0], hold=[-66, 58, 0],
        arms=([-34, 22, -58], [-40, -22, 58]), squash=1.30)

    # --- Travel poses -----------------------------------------------------
    # The server owns the movement; these only sell it. Lean into the travel,
    # arms trailing, and get back to guard fast because a strike follows.
    a["combat.xeno_step_in_dash_v3"] = clip(0.22, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.08: [-24, 0, 0], 0.14: [-22, 0, 0],
                              0.22: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.08: [-12, 8, 0], 0.22: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [-44, 0, 0], 0.14: [-38, 0, 0],
                                   0.22: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [32, 0, 0], 0.22: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G3_L, 0.08: [-58, 18, -50], 0.22: G3_L}},
        "right_arm": {"rotation": {0.0: G3_R, 0.08: [-70, -18, 48], 0.22: G3_R}},
    })

    a["combat.xeno_rush_in_chase_v3"] = clip(0.26, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.10: [-38, 0, 0], 0.18: [-34, 0, 0],
                              0.26: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.10: [-16, 0, 0], 0.26: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [-50, 0, 0], 0.18: [-44, 0, 0],
                                   0.26: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [42, 0, 0], 0.26: [0, 0, 0]}},
        # Arms swept back behind the shoulders - the Z-burst silhouette.
        "left_arm": {"rotation": {0.0: G3_L, 0.10: [26, 10, -30], 0.18: [24, 10, -28],
                                  0.26: G3_L}},
        "right_arm": {"rotation": {0.0: G3_R, 0.10: [26, -10, 30], 0.18: [24, -10, 28],
                                   0.26: G3_R}},
    })

    # --- 360s -------------------------------------------------------------
    a["combat.xeno_spin_hook_left_v3"] = v3_spin(
        0.44, "left", strike_bones=_v3_hook_after("left"), hips=34)
    a["combat.xeno_spin_hook_right_v3"] = v3_spin(
        0.44, "right", strike_bones=_v3_hook_after("right"), hips=-34)
    a["combat.xeno_spin_uppercut_left_v3"] = v3_spin(
        0.44, "left", strike_bones=_v3_uppercut_after("left"), hips=22)
    a["combat.xeno_spin_kick_right_v3"] = v3_spin(
        0.44, "right", strike_bones=_v3_kick_after("right"), hips=-44)

    return a


def _v3_hook_after(side):
    """The strike half of a spin-hook, keyed against the spin's own timings."""
    s = 1 if side == "right" else -1
    arm = side + "_arm"
    off = "left_arm" if side == "right" else "right_arm"

    def bones(q, t_hit, t_end, length):
        return {
            arm: {
                "rotation": {q: [-70, -28 * s, 56 * s], t_hit: [-86, 56 * s, 24 * s],
                             t_end: [-84, 52 * s, 22 * s], length: _g3(side)},
                "position": {q: _sx(side, [1.0, 0.4, 1.8]),
                             t_hit: _sx(side, [-1.8, -0.2, -2.8]),
                             t_end: _sx(side, [-1.6, -0.2, -2.6]), length: [0, 0, 0]},
            },
            off: {"rotation": {t_hit: [-88, -20 * s, -50 * s],
                               t_end: [-86, -18 * s, -48 * s],
                               length: _g3("left" if side == "right" else "right")}},
        }
    return bones


def _v3_uppercut_after(side):
    s = 1 if side == "right" else -1
    arm = side + "_arm"
    back_leg = "right_leg" if side == "left" else "left_leg"

    def bones(q, t_hit, t_end, length):
        return {
            arm: {
                "rotation": {q: [-30, 10 * s, 50 * s], t_hit: [-132, 16 * s, 18 * s],
                             t_end: [-126, 14 * s, 16 * s], length: _g3(side)},
                "position": {q: _sx(side, [0.4, -1.8, 1.2]),
                             t_hit: _sx(side, [0.2, 2.2, -2.4]),
                             t_end: _sx(side, [0.2, 2.0, -2.2]), length: [0, 0, 0]},
            },
            back_leg: {"rotation": {q: [0, 0, 0], t_hit: [-14, 0, 0], length: [0, 0, 0]}},
        }
    return bones


def _v3_kick_after(side):
    leg = side + "_leg"
    other = "left_leg" if side == "right" else "right_leg"
    s = 1 if side == "right" else -1

    def bones(q, t_hit, t_end, length):
        return {
            leg: {"rotation": {q: [24, -22 * s, 0], t_hit: [-82, -60 * s, 0],
                               t_end: [-76, -56 * s, 0], length: [0, 0, 0]}},
            other: {"rotation": {q: [8, 0, 0], t_hit: [-8, 0, 0], length: [0, 0, 0]}},
        }
    return bones


# ---------------------------------------------------------------------------
# v4: forward-centred BT3 rush combat.
#
# V3 drove ordinary strikes with large root yaw. On the DMZ player rig that makes the whole
# fighter glance left/right even though the hit target is straight ahead. V4 keeps root yaw at
# zero for jabs, crosses, hooks, uppercuts, body shots and non-spinning kicks. Shoulder/waist
# rotation still sells weight, but every fist and foot travels primarily down model-forward -Z.
# Named spin attacks deliberately own a complete 0 -> 360 turn before the impact pose.

G4_R = [-54, -6, 22]
G4_L = [-54, 6, -22]


def _g4(side):
    return G4_R if side == "right" else G4_L


def v4_punch(length, side, kind):
    s = 1 if side == "right" else -1
    arm = side + "_arm"
    off = "left_arm" if side == "right" else "right_arm"
    rest = _g4(side)
    off_rest = _g4("left" if side == "right" else "right")
    t_ch = round(length * 0.18, 4)
    t_hit = round(length * 0.43, 4)
    t_hold = round(length * 0.68, 4)

    poses = {
        "jab": ([-62, -10 * s, 24 * s], [-96, 2 * s, 8 * s],
                [0, 0.10, 1.0], [0, 0.10, -2.8], 0, 3 * s),
        "cross": ([-34, -22 * s, 48 * s], [-106, 4 * s, 12 * s],
                  [0, 0.15, 1.7], [0, 0.15, -3.5], -3, 7 * s),
        "body": ([-48, -16 * s, 38 * s], [-116, 3 * s, 10 * s],
                 [0, -0.35, 1.4], [0, -0.65, -3.2], 10, 5 * s),
        "hook": ([-68, -50 * s, 68 * s], [-90, 58 * s, 28 * s],
                 [0.55 * s, 0.15, 1.6], [0.55 * s, -0.05, -2.25], 2, 10 * s),
        "uppercut": ([-28, 10 * s, 54 * s], [-144, 4 * s, 18 * s],
                     [0, -1.4, 1.0], [0, 2.4, -2.6], 14, 4 * s),
    }
    chamber, impact, chamber_pos, impact_pos, waist_pitch, waist_yaw = poses[kind]
    front = "right_leg" if side == "right" else "left_leg"
    back = "left_leg" if side == "right" else "right_leg"
    return clip(length, {
        "root": {
            "rotation": {0.0: [0, 0, 0], t_ch: [2, 0, 0], t_hit: [-3, 0, 0],
                         t_hold: [-2, 0, 0], length: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], t_hit: [1, 1, 1.26],
                      t_hold: [1, 1, 1.22], length: [1, 1, 1]},
        },
        "waist": {"rotation": {
            0.0: [0, 0, 0], t_ch: [waist_pitch * 0.25, -waist_yaw * 0.35, 0],
            t_hit: [waist_pitch, waist_yaw, 0], t_hold: [waist_pitch * 0.8, waist_yaw * 0.8, 0],
            length: [0, 0, 0],
        }},
        arm: {
            "rotation": {0.0: rest, t_ch: chamber, t_hit: impact, t_hold: impact, length: rest},
            "position": {0.0: [0, 0, 0], t_ch: chamber_pos, t_hit: impact_pos,
                         t_hold: impact_pos, length: [0, 0, 0]},
        },
        off: {"rotation": {0.0: off_rest, t_ch: [-76, -12 * s, -42 * s],
                           t_hit: [-82, -10 * s, -46 * s],
                           t_hold: [-82, -10 * s, -46 * s], length: off_rest}},
        front: {"rotation": {0.0: [0, 0, 0], t_ch: [10, 0, 0], t_hit: [-15, 0, 0],
                            t_hold: [-12, 0, 0], length: [0, 0, 0]}},
        back: {"rotation": {0.0: [0, 0, 0], t_ch: [-7, 0, 0], t_hit: [12, 0, 0],
                           t_hold: [10, 0, 0], length: [0, 0, 0]}},
    })


def v4_kick(length, side, kind):
    s = 1 if side == "right" else -1
    leg = side + "_leg"
    support = "left_leg" if side == "right" else "right_leg"
    t_ch = round(length * 0.20, 4)
    t_hit = round(length * 0.46, 4)
    t_hold = round(length * 0.70, 4)
    poses = {
        "low": ([30, 0, 4 * s], [-68, 0, 5 * s], [0, -0.35, -1.9], 8),
        "mid": ([36, 0, 5 * s], [-92, 0, 7 * s], [0, 0.15, -2.6], 2),
        "knee": ([18, 0, 3 * s], [-58, 0, 5 * s], [0, 1.15, -1.55], -8),
        "roundhouse": ([42, -18 * s, 8 * s], [-82, 38 * s, 12 * s],
                       [0.35 * s, 0.65, -2.75], -5),
    }
    chamber, impact, impact_pos, root_pitch = poses[kind]
    return clip(length, {
        "root": {
            "rotation": {0.0: [0, 0, 0], t_ch: [root_pitch * 0.3, 0, 0],
                         t_hit: [root_pitch, 0, 0], t_hold: [root_pitch * 0.8, 0, 0],
                         length: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], t_hit: [1, 1, 1.20],
                      t_hold: [1, 1, 1.16], length: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], t_ch: [4, -5 * s, 0],
                               t_hit: [-8, 9 * s, 0], t_hold: [-6, 7 * s, 0],
                               length: [0, 0, 0]}},
        leg: {
            "rotation": {0.0: [0, 0, 0], t_ch: chamber, t_hit: impact,
                         t_hold: impact, length: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], t_ch: [0, 0.35, 0.65], t_hit: impact_pos,
                         t_hold: impact_pos, length: [0, 0, 0]},
        },
        support: {"rotation": {0.0: [0, 0, 0], t_ch: [12, 0, 0],
                              t_hit: [-8, 0, 0], t_hold: [-6, 0, 0], length: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, t_hit: [-70, 12, -48],
                                   t_hold: [-68, 10, -44], length: G4_L}},
        "right_arm": {"rotation": {0.0: G4_R, t_hit: [-70, -12, 48],
                                    t_hold: [-68, -10, 44], length: G4_R}},
    })


def v4_spin(length, strike):
    t1 = round(length * 0.14, 4)
    t2 = round(length * 0.28, 4)
    t3 = round(length * 0.42, 4)
    t4 = round(length * 0.56, 4)
    t_hit = round(length * 0.72, 4)
    t_hold = round(length * 0.84, 4)
    bones = {
        "root": {
            "rotation": {0.0: [0, 0, 0], t1: [0, 90, 0], t2: [0, 180, 0],
                         t3: [0, 270, 0], t4: [0, 360, 0],
                         t_hit: [0, 360, 0], t_hold: [0, 360, 0], length: [0, 360, 0]},
            "scale": {0.0: [1, 1, 1], t_hit: [1, 1, 1.28],
                      t_hold: [1, 1, 1.22], length: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], t2: [-6, 0, 0],
                               t4: [6, 0, 0], t_hit: strike.get("waist", [0, 0, 0]),
                               t_hold: strike.get("waist", [0, 0, 0]), length: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, t2: [-30, 20, -52], t4: [-66, 12, -34],
                                   t_hit: strike.get("left_arm", G4_L),
                                   t_hold: strike.get("left_arm", G4_L), length: G4_L}},
        "right_arm": {"rotation": {0.0: G4_R, t2: [-30, -20, 52], t4: [-66, -12, 34],
                                    t_hit: strike.get("right_arm", G4_R),
                                    t_hold: strike.get("right_arm", G4_R), length: G4_R}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], t2: [14, 0, 0], t4: [-10, 0, 0],
                                   t_hit: strike.get("left_leg", [0, 0, 0]),
                                   t_hold: strike.get("left_leg", [0, 0, 0]), length: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], t2: [-10, 0, 0], t4: [14, 0, 0],
                                    t_hit: strike.get("right_leg", [0, 0, 0]),
                                    t_hold: strike.get("right_leg", [0, 0, 0]), length: [0, 0, 0]}},
    }
    for bone in ("left_arm", "right_arm", "left_leg", "right_leg"):
        position = strike.get(bone + "_position")
        if position is not None:
            bones[bone]["position"] = {0.0: [0, 0, 0], t4: [0, 0, 0],
                                       t_hit: position, t_hold: position, length: [0, 0, 0]}
    return clip(length, bones)


def build_v4():
    a = {}
    dmz_punches = dmz_one_handed_punches()
    for side in ("left", "right"):
        a["combat.xeno_dmz_punch_%s_v4" % side] = dmz_punches[side]
        a["combat.xeno_cross_%s_v4" % side] = v4_punch(0.28, side, "cross")
        a["combat.xeno_body_punch_%s_v4" % side] = v4_punch(0.28, side, "body")
        a["combat.xeno_hook_%s_v4" % side] = v4_punch(0.30, side, "hook")
        a["combat.xeno_uppercut_%s_v4" % side] = v4_punch(0.30, side, "uppercut")
        a["combat.xeno_low_kick_%s_v4" % side] = v4_kick(0.32, side, "low")
        a["combat.xeno_mid_kick_%s_v4" % side] = v4_kick(0.34, side, "mid")
        a["combat.xeno_knee_%s_v4" % side] = v4_kick(0.28, side, "knee")

    a["combat.xeno_high_roundhouse_v4"] = v4_kick(0.40, "right", "roundhouse")
    a["combat.xeno_spinning_back_kick_v4"] = v4_spin(0.52, {
        "waist": [-8, 0, 0], "right_leg": [-88, 0, 8],
        "right_leg_position": [0, 0.45, -3.2], "left_arm": [-76, 14, -50],
        "right_arm": [-70, -12, 44],
    })
    a["combat.xeno_flying_kick_v4"] = clip(0.38, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.10: [-28, 0, 0], 0.25: [-20, 0, 0],
                              0.38: [0, 0, 0]},
                 "scale": {0.0: [1, 1, 1], 0.15: [1, 1, 1.25], 0.28: [1, 1, 1.2],
                           0.38: [1, 1, 1]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [28, 0, 0],
                                    0.18: [-96, 0, 0], 0.28: [-92, 0, 0], 0.38: [0, 0, 0]},
                      "position": {0.0: [0, 0, 0], 0.18: [0, 0.35, -3.4],
                                   0.28: [0, 0.35, -3.2], 0.38: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.18: [34, 0, 0], 0.38: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, 0.18: [20, 10, -32], 0.38: G4_L}},
        "right_arm": {"rotation": {0.0: G4_R, 0.18: [18, -10, 32], 0.38: G4_R}},
    })
    a["combat.xeno_heavy_finish_v4"] = clip(0.42, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.08: [5, 0, 0], 0.19: [-8, 0, 0],
                              0.30: [-7, 0, 0], 0.42: [0, 0, 0]},
                 "scale": {0.0: [1, 1, 1], 0.19: [1, 1, 1.38],
                           0.30: [1, 1, 1.32], 0.42: [1, 1, 1]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.08: [8, 0, 0],
                               0.19: [-12, 0, 0], 0.42: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, 0.08: [-28, 18, -54],
                                   0.19: [-104, 0, -8], 0.30: [-102, 0, -8], 0.42: G4_L},
                     "position": {0.0: [0, 0, 0], 0.08: [0, 0, 1.5],
                                  0.19: [0, 0.1, -3.6], 0.30: [0, 0.1, -3.4], 0.42: [0, 0, 0]}},
        "right_arm": {"rotation": {0.0: G4_R, 0.08: [-28, -18, 54],
                                    0.19: [-104, 0, 8], 0.30: [-102, 0, 8], 0.42: G4_R},
                      "position": {0.0: [0, 0, 0], 0.08: [0, 0, 1.5],
                                   0.19: [0, 0.1, -3.6], 0.30: [0, 0.1, -3.4], 0.42: [0, 0, 0]}},
    })
    a["combat.xeno_step_in_dash_v4"] = clip(0.24, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.08: [-22, 0, 0],
                              0.16: [-20, 0, 0], 0.24: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.08: [-10, 0, 0], 0.24: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, 0.08: [16, 8, -28], 0.24: G4_L}},
        "right_arm": {"rotation": {0.0: G4_R, 0.08: [16, -8, 28], 0.24: G4_R}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [30, 0, 0], 0.24: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.08: [-40, 0, 0], 0.24: [0, 0, 0]}},
    })
    a["combat.xeno_rush_in_chase_v4"] = clip(0.28, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.10: [-42, 0, 0],
                              0.20: [-38, 0, 0], 0.28: [0, 0, 0]},
                 "scale": {0.0: [1, 1, 1], 0.10: [1, 1, 1.28],
                           0.20: [1, 1, 1.24], 0.28: [1, 1, 1]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.10: [-18, 0, 0], 0.28: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: G4_L, 0.10: [30, 8, -28], 0.28: G4_L}},
        "right_arm": {"rotation": {0.0: G4_R, 0.10: [30, -8, 28], 0.28: G4_R}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [42, 0, 0], 0.28: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [-52, 0, 0], 0.28: [0, 0, 0]}},
    })
    a["combat.xeno_spin_hook_left_v4"] = v4_spin(0.52, {
        "waist": [0, -8, 0], "left_arm": [-92, -58, -28],
        "left_arm_position": [-0.55, 0, -2.7], "right_arm": [-82, 10, 46],
    })
    a["combat.xeno_spin_hook_right_v4"] = v4_spin(0.52, {
        "waist": [0, 8, 0], "right_arm": [-92, 58, 28],
        "right_arm_position": [0.55, 0, -2.7], "left_arm": [-82, -10, -46],
    })
    a["combat.xeno_spin_uppercut_left_v4"] = v4_spin(0.52, {
        "waist": [12, -4, 0], "left_arm": [-144, -4, -18],
        "left_arm_position": [0, 2.4, -2.6], "right_arm": [-82, 10, 46],
    })
    a["combat.xeno_spin_kick_right_v4"] = v4_spin(0.54, {
        "waist": [-8, 0, 0], "right_leg": [-94, 0, 8],
        "right_leg_position": [0, 0.55, -3.3], "left_arm": [-76, 14, -50],
        "right_arm": [-70, -12, 44],
    })
    return a

def main() -> int:
    ap = argparse.ArgumentParser(description="Generate the BT3 DMZ-format animation file.")
    ap.add_argument("--out", type=Path, default=Path(
        "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json"))
    args = ap.parse_args()

    model = build()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print("%s  (%d animations)" % (args.out, len(model["animations"])))
    for name in sorted(model["animations"]):
        print("   " + name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
