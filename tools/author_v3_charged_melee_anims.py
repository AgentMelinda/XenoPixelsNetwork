"""Rewrite charged punch/kick hold+fire clips as full-body series-style strikes.

Owner 2026-03-22: realistic charged kick/punch with bone rotation XYZ, angles out of
the body, full-body moving — like Dragon Ball / DMZ combat, not a shoulder twitch.

Owner 2026-03-22 (waist fix): striking-limb rotation overshoot is allowed; hip/shoulder
socket must stay attached. Cap leg/arm position Y so mesh does not yank out of the waist.

Edits only:
  combat.xeno_charge_punch_hold / _fire
  combat.xeno_charge_kick_hold / _fire
inside src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json

Run from repo root: python tools/author_v3_charged_melee_anims.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json"

# Compact front-side guard for the off hand (still readable; striking limb goes wild).
GUARD_L = [-62, 10, -28]
GUARD_R = [-62, -10, 28]


def v(x, y, z):
    return {"vector": [round(x, 3), round(y, 3), round(z, 3)]}


def channel(frames: dict[float, list[float]]):
    return {str(t): v(*xyz) for t, xyz in frames.items()}


def clip(length: float, bones: dict, *, loop: bool = False) -> dict:
    out = {"animation_length": length, "bones": {}}
    if loop:
        out["loop"] = True
    for bone, channels in bones.items():
        entry = {}
        for name, frames in channels.items():
            if name == "scale":
                continue
            if bone in ("right_arm", "left_arm"):
                # Keep both fists in front of the shoulder in charge and release.
                frames = {time: [min(x, 0) if name == "rotation" else x,
                                 y, min(z, 0) if name == "position" else z]
                          for time, (x, y, z) in frames.items()}
            entry[name] = channel(frames)
        out["bones"][bone] = entry
    return out


def charge_punch_hold() -> dict:
    """Coil: hips load back, rear foot plants, fist chambers behind the shoulder."""
    return clip(0.55, {
        "root": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [8, -18, -6],
                0.55: [10, -22, -8],
            },
            "position": {
                0.0: [0, 0, 0],
                0.18: [0.15, -0.05, 0.35],
                0.55: [0.2, -0.08, 0.45],
            },
        },
        "waist": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [12, -28, -10],
                0.55: [14, -36, -12],
            },
        },
        "head": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-4, 18, 4],
                0.55: [-6, 22, 5],
            },
        },
        "right_arm": {
            "rotation": {
                0.0: GUARD_R,
                0.18: [-28, -48, 55],
                0.55: [-18, -58, 62],
            },
            # Chamber behind shoulder via rotation; keep socket Y small.
            "position": {
                0.0: [0, 0, 0],
                0.18: [0.6, 0.25, 1.4],
                0.55: [0.75, 0.35, 1.8],
            },
        },
        "left_arm": {
            "rotation": {
                0.0: GUARD_L,
                0.18: [-95, 16, -38],
                0.55: [-100, 18, -42],
            },
        },
        "right_leg": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [28, -18, -8],
                0.55: [32, -22, -10],
            },
            "position": {
                0.0: [0, 0, 0],
                0.18: [0.15, 0, 0.4],
                0.55: [0.2, 0, 0.5],
            },
        },
        "left_leg": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-22, 14, 6],
                0.55: [-26, 16, 8],
            },
        },
    }, loop=True)


def charge_punch_fire() -> dict:
    """Explosive hip snap → fist overshoots out of the body → recovery."""
    return clip(0.95, {
        "root": {
            "rotation": {
                0.0: [10, -22, -8],
                0.10: [10, -22, -8],
                0.22: [-16, 28, 10],
                0.38: [-14, 32, 12],
                0.58: [-6, 18, 6],
                0.95: [0, 0, 0],
            },
            "position": {
                0.0: [0.2, -0.08, 0.45],
                0.10: [0.2, -0.08, 0.45],
                0.22: [-0.15, 0.12, -0.55],
                0.38: [-0.1, 0.08, -0.4],
                0.58: [0, 0, -0.1],
                0.95: [0, 0, 0],
            },
            "scale": {
                0.0: [1, 1, 1],
                0.22: [1.04, 1.04, 1.14],
                0.38: [1.02, 1.02, 1.1],
                0.95: [1, 1, 1],
            },
        },
        "waist": {
            "rotation": {
                0.0: [14, -36, -12],
                0.10: [14, -36, -12],
                0.22: [-22, 36, 14],
                0.38: [-18, 32, 12],
                0.58: [-8, 18, 6],
                0.95: [0, 0, 0],
            },
        },
        "head": {
            "rotation": {
                0.0: [-6, 22, 5],
                0.10: [-6, 22, 5],
                0.22: [8, -20, -6],
                0.38: [6, -16, -4],
                0.95: [0, 0, 0],
            },
        },
        "right_arm": {
            "rotation": {
                0.0: [-18, -58, 62],
                0.10: [-18, -58, 62],
                0.22: [-125, 8, 6],
                0.38: [-118, 6, 4],
                0.58: [-72, -12, 28],
                0.95: GUARD_R,
            },
            # Forward overshoot on Z; keep Y low so shoulder/waist stay closed.
            "position": {
                0.0: [0.75, 0.35, 1.8],
                0.10: [0.75, 0.35, 1.8],
                0.22: [-0.4, 0.45, -4.2],
                0.38: [-0.3, 0.35, -3.6],
                0.58: [0.15, 0.15, -1.0],
                0.95: [0, 0, 0],
            },
        },
        "left_arm": {
            "rotation": {
                0.0: [-100, 18, -42],
                0.10: [-100, 18, -42],
                0.22: [-70, 22, -36],
                0.38: [-68, 20, -34],
                0.95: GUARD_L,
            },
        },
        "right_leg": {
            "rotation": {
                0.0: [32, -22, -10],
                0.10: [32, -22, -10],
                0.22: [-36, 28, 8],
                0.38: [-28, 22, 6],
                0.95: [0, 0, 0],
            },
            "position": {
                0.0: [0.2, 0, 0.5],
                0.22: [-0.1, 0, -0.4],
                0.95: [0, 0, 0],
            },
        },
        "left_leg": {
            "rotation": {
                0.0: [-26, 16, 8],
                0.10: [-26, 16, 8],
                0.22: [22, 24, -8],
                0.38: [16, 18, -6],
                0.95: [0, 0, 0],
            },
        },
    })


def charge_kick_hold() -> dict:
    """Chamber: knee tucked high, hips open, body leans — DBZ flying/charged kick windup."""
    return clip(0.55, {
        "root": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [14, -12, -10],
                0.55: [18, -16, -12],
            },
            "position": {
                0.0: [0, 0, 0],
                0.18: [0.1, 0.15, 0.2],
                0.55: [0.12, 0.22, 0.25],
            },
        },
        "waist": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-10, -24, 10],
                0.55: [-12, -28, 12],
            },
        },
        "head": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-8, 14, 4],
                0.55: [-10, 16, 5],
            },
        },
        "right_leg": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-78, -22, 14],
                0.55: [-92, -28, 18],
            },
            # Knee chamber is rotation-led; Y must stay hip-socketed (was 2.2 = waist pop).
            "position": {
                0.0: [0, 0, 0],
                0.18: [0.25, 0.25, 0.7],
                0.55: [0.3, 0.35, 0.9],
            },
        },
        "left_leg": {
            "rotation": {
                0.0: [0, 0, 0],
                0.18: [-18, 18, 10],
                0.55: [-22, 22, 12],
            },
            "position": {
                0.0: [0, 0, 0],
                0.18: [0, -0.05, 0.2],
                0.55: [0, -0.08, 0.25],
            },
        },
        "right_arm": {
            "rotation": {
                0.0: GUARD_R,
                0.18: [-58, -16, 36],
                0.55: [-55, -18, 40],
            },
        },
        "left_arm": {
            "rotation": {
                0.0: GUARD_L,
                0.18: [-98, 18, -36],
                0.55: [-105, 22, -40],
            },
        },
    }, loop=True)


def charge_kick_fire() -> dict:
    """Snap kick: hips whip, leg extends past bind with XYZ overshoot, full-body follow-through."""
    return clip(1.05, {
        "root": {
            "rotation": {
                0.0: [18, -16, -12],
                0.10: [18, -16, -12],
                0.26: [-22, 24, 14],
                0.42: [-18, 28, 16],
                0.62: [-8, 14, 8],
                1.05: [0, 0, 0],
            },
            "position": {
                0.0: [0.12, 0.22, 0.25],
                0.10: [0.12, 0.22, 0.25],
                0.26: [-0.2, 0.55, -0.8],
                0.42: [-0.15, 0.4, -0.55],
                0.62: [0, 0.1, -0.15],
                1.05: [0, 0, 0],
            },
            "scale": {
                0.0: [1, 1, 1],
                0.26: [1.04, 1.04, 1.14],
                0.42: [1.02, 1.02, 1.1],
                1.05: [1, 1, 1],
            },
        },
        "waist": {
            "rotation": {
                0.0: [-12, -28, 12],
                0.10: [-12, -28, 12],
                0.26: [-28, 32, 16],
                0.42: [-22, 28, 12],
                0.62: [-10, 14, 6],
                1.05: [0, 0, 0],
            },
        },
        "head": {
            "rotation": {
                0.0: [-10, 16, 5],
                0.10: [-10, 16, 5],
                0.26: [-16, -18, 8],
                0.42: [-12, -14, 6],
                1.05: [0, 0, 0],
            },
        },
        "right_leg": {
            "rotation": {
                0.0: [-92, -28, 18],
                0.10: [-92, -28, 18],
                0.26: [-135, 36, 22],
                0.42: [-128, 30, 18],
                0.62: [-70, 12, 10],
                1.05: [0, 0, 0],
            },
            # Extension overshoot on Z; Y stays near hip (was 1.8/2.2 = waist pop).
            "position": {
                0.0: [0.3, 0.35, 0.9],
                0.10: [0.3, 0.35, 0.9],
                0.26: [-0.25, 0.4, -3.8],
                0.42: [-0.2, 0.3, -3.2],
                0.62: [0.05, 0.15, -1.2],
                1.05: [0, 0, 0],
            },
        },
        "left_leg": {
            "rotation": {
                0.0: [-22, 22, 12],
                0.10: [-22, 22, 12],
                0.26: [-38, 40, 16],
                0.42: [-30, 32, 12],
                0.62: [-12, 14, 6],
                1.05: [0, 0, 0],
            },
            "position": {
                0.0: [0, -0.08, 0.25],
                0.26: [0.1, -0.1, 0.45],
                1.05: [0, 0, 0],
            },
        },
        "right_arm": {
            "rotation": {
                0.0: [-55, -18, 40],
                0.10: [-55, -18, 40],
                0.26: [-58, -22, 44],
                0.42: [-58, -20, 42],
                1.05: GUARD_R,
            },
        },
        "left_arm": {
            "rotation": {
                0.0: [-105, 22, -40],
                0.10: [-105, 22, -40],
                0.26: [-78, 26, -38],
                0.42: [-74, 22, -36],
                1.05: GUARD_L,
            },
        },
    })


def main():
    data = json.loads(RESOURCE.read_text(encoding="utf-8"))
    anims = data.setdefault("animations", {})
    anims["combat.xeno_charge_punch_hold"] = charge_punch_hold()
    anims["combat.xeno_charge_punch_fire"] = charge_punch_fire()
    anims["combat.xeno_charge_kick_hold"] = charge_kick_hold()
    anims["combat.xeno_charge_kick_fire"] = charge_kick_fire()
    RESOURCE.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for name in (
        "combat.xeno_charge_punch_hold",
        "combat.xeno_charge_punch_fire",
        "combat.xeno_charge_kick_hold",
        "combat.xeno_charge_kick_fire",
    ):
        a = anims[name]
        print(name, "len", a["animation_length"], "loop", a.get("loop"), "bones", list(a["bones"]))


if __name__ == "__main__":
    main()
