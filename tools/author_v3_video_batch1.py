"""Video-backed choreography batch 1 — 10 Strike occurrences from local BT3 Part 1 MP4.

Frames extracted to docs/combat-v3/video-batch1/. Bone angles are hand-retargeted from those
frames onto DMZ's seven-bone rig with XYZ overshoot allowed. Status stays
reference_timed_unverified / authored_gameplay_unverified until in-game comparison.

Run from repo root:
  python tools/author_v3_video_batch1.py
  python tools/gen_combat_v3_techniques.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
OVERRIDES = ROOT / "docs/combat-v3/choreography.json"
EVIDENCE_DIR = "docs/combat-v3/video-batch1"

BONES = ["root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg"]


def v(x, y, z):
    return {"vector": [round(x, 2), round(y, 2), round(z, 2)]}


def rot_channel(frames: dict):
    return {str(t): v(*xyz) for t, xyz in frames.items()}


def pos_channel(frames: dict):
    return {str(t): v(*xyz) for t, xyz in frames.items()}


def clip(length: float, bone_channels: dict, *, loop=False) -> dict:
    out = {"animation_length": length, "bones": {}}
    if loop:
        out["loop"] = loop
    for bone, channels in bone_channels.items():
        entry = {}
        if "rotation" in channels:
            entry["rotation"] = rot_channel(channels["rotation"])
        if "position" in channels:
            entry["position"] = pos_channel(channels["position"])
        if "scale" in channels:
            entry["scale"] = rot_channel(channels["scale"])
        out["bones"][bone] = entry
    return out


def beat(kind, tick, payload="", duration=0, value=0.0):
    return {"kind": kind, "tick": tick, "duration": duration, "payload": payload, "value": float(value)}


def shot(tick, duration, position, focus=0.4, easing="SMOOTH"):
    return {
        "tick": tick,
        "duration": duration,
        "position": list(position),
        "look": [0, 0, 0],
        "focus": focus,
        "easing": easing,
    }


# --- Unique clips (video-informed) -------------------------------------------------

def kame_charge():
    # Existing kid kame timing preserved; keep as charge hold.
    return clip(1.8, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.15: [0, -15, 0], 1.75: [0, -15, 0]}},
        "waist": {"rotation": {0.0: [14, 8, 0], 0.15: [22, -28, -5], 1.75: [22, -28, -5]}},
        "head": {"rotation": {0.0: [-8, -8, 0], 0.15: [-10, 38, 0], 1.75: [-10, 38, 0]}},
        "right_arm": {
            "rotation": {0.0: [-42, -18, 28], 0.15: [-30, -48, 38], 1.75: [-30, -48, 38]},
            "position": {0.0: [0, 0, 0], 0.15: [0.6, 0.4, 1.2], 1.75: [0.6, 0.4, 1.2]},
        },
        "left_arm": {
            "rotation": {0.0: [-38, 18, -28], 0.15: [-56, -38, -35], 1.75: [-56, -38, -35]},
            "position": {0.0: [0, 0, 0], 0.15: [-0.5, 0.3, 1.0], 1.75: [-0.5, 0.3, 1.0]},
        },
        "right_leg": {"rotation": {0.0: [-18, -12, 8], 0.15: [-28, -18, 10], 1.75: [-28, -18, 10]}},
        "left_leg": {"rotation": {0.0: [20, 12, -8], 0.15: [34, 20, -10], 1.75: [34, 20, -10]}},
    }, loop="hold_on_last_frame")


def kame_release():
    return clip(1.25, {
        "root": {"rotation": {0.0: [0, -15, 0], 0.1: [0, 0, 0], 1.15: [0, 0, 0], 1.25: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [22, -28, -5], 0.1: [20, 5, 0], 1.15: [20, 5, 0], 1.25: [14, 8, 0]}},
        "head": {"rotation": {0.0: [-10, 38, 0], 0.1: [-15, -5, 0], 1.25: [-8, -8, 0]}},
        "right_arm": {
            "rotation": {0.0: [-30, -48, 38], 0.1: [-92, -6, 6], 1.15: [-92, -6, 6], 1.25: [-42, -18, 28]},
            "position": {0.0: [0.6, 0.4, 1.2], 0.1: [-0.4, 0.8, -4.5], 1.15: [-0.4, 0.8, -4.5], 1.25: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-56, -38, -35], 0.1: [-92, 6, -6], 1.15: [-92, 6, -6], 1.25: [-38, 18, -28]},
            "position": {0.0: [-0.5, 0.3, 1.0], 0.1: [0.3, 0.7, -4.2], 1.15: [0.3, 0.7, -4.2], 1.25: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [-28, -18, 10], 0.1: [-36, -20, 12], 1.25: [-18, -12, 8]}},
        "left_leg": {"rotation": {0.0: [34, 20, -10], 0.1: [26, 22, -12], 1.25: [20, 12, -8]}},
    })


def penetrate():
    # frame_0026: straight arm thrust, forward lean, rear foot drive
    return clip(0.9, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.12: [8, -12, -4], 0.28: [-10, 18, 6], 0.55: [-6, 10, 4], 0.9: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.28: [0, 0.05, -0.6], 0.9: [0, 0, 0]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.12: [12, -22, -8], 0.28: [-14, 28, 10], 0.9: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.28: [6, -12, 0], 0.9: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 30], 0.12: [-40, -35, 45], 0.28: [-118, 5, 8], 0.55: [-100, 4, 6], 0.9: [-55, -10, 30]},
            "position": {0.0: [0, 0, 0], 0.12: [0.8, 0.5, 1.5], 0.28: [-0.5, 1.0, -5.8], 0.55: [-0.3, 0.7, -4.0], 0.9: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-70, 15, -35], 0.28: [-55, 22, -30], 0.9: [-70, 15, -35]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.28: [-32, 18, 6], 0.9: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.12: [18, 10, -6], 0.28: [28, 16, -8], 0.9: [0, 0, 0]}},
    })


def kaioken_rush():
    # frame_0044: both fists forward, lean-in rush
    return clip(1.0, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.15: [12, 0, 0], 0.35: [-8, 0, 0], 0.7: [-4, 0, 0], 1.0: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.35: [0, 0.1, -0.9], 1.0: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.35: [1.04, 1.04, 1.25], 1.0: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [16, 0, 0], 0.35: [-12, 0, 0], 1.0: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [-10, 0, 0], 1.0: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-60, -12, 28], 0.15: [-75, -8, 20], 0.35: [-110, 4, 10], 0.7: [-95, 2, 8], 1.0: [-60, -12, 28]},
            "position": {0.0: [0, 0, 0], 0.35: [-0.3, 0.9, -5.2], 1.0: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-60, 12, -28], 0.15: [-75, 8, -20], 0.35: [-110, -4, -10], 0.7: [-95, -2, -8], 1.0: [-60, 12, -28]},
            "position": {0.0: [0, 0, 0], 0.35: [0.3, 0.9, -5.2], 1.0: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-25, -10, 6], 1.0: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [30, 12, -8], 1.0: [0, 0, 0]}},
    })


def solar_flare():
    # Taiyoken: both palms shoot straight overhead (high Y position overshoot) then flash hold.
    return clip(1.1, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.15: [8, 0, 0], 0.4: [4, 0, 0], 0.85: [4, 0, 0], 1.1: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [12, 0, 0], 0.4: [8, 0, 0], 0.85: [8, 0, 0], 1.1: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.4: [-35, 0, 0], 0.85: [-30, 0, 0], 1.1: [0, 0, 0]}},
        "right_arm": {
            "rotation": {
                0.0: [-50, -15, 25],
                0.12: [-90, -20, 35],
                0.35: [-175, 5, 20],
                0.55: [-178, 8, 18],
                0.9: [-170, 6, 16],
                1.1: [-50, -15, 25],
            },
            "position": {
                0.0: [0, 0, 0],
                0.12: [0.4, 1.5, -0.5],
                0.35: [0.6, 4.8, -1.2],
                0.55: [0.7, 5.2, -1.4],
                0.9: [0.5, 4.5, -1.0],
                1.1: [0, 0, 0],
            },
        },
        "left_arm": {
            "rotation": {
                0.0: [-50, 15, -25],
                0.12: [-90, 20, -35],
                0.35: [-175, -5, -20],
                0.55: [-178, -8, -18],
                0.9: [-170, -6, -16],
                1.1: [-50, 15, -25],
            },
            "position": {
                0.0: [0, 0, 0],
                0.12: [-0.4, 1.5, -0.5],
                0.35: [-0.6, 4.8, -1.2],
                0.55: [-0.7, 5.2, -1.4],
                0.9: [-0.5, 4.5, -1.0],
                1.1: [0, 0, 0],
            },
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-14, -6, 4], 1.1: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [16, 8, -4], 1.1: [0, 0, 0]}},
    })


def spirit_bomb_charge():
    return clip(2.0, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.3: [8, 0, 0], 1.8: [10, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.3: [12, 0, 0], 1.8: [14, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.3: [-25, 0, 0], 1.8: [-30, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 25], 0.3: [-150, 10, 20], 1.8: [-155, 12, 22]},
            "position": {0.0: [0, 0, 0], 0.3: [0.4, 3.5, -1.5], 1.8: [0.5, 4.0, -1.8]},
        },
        "left_arm": {
            "rotation": {0.0: [-55, 10, -25], 0.3: [-150, -10, -20], 1.8: [-155, -12, -22]},
            "position": {0.0: [0, 0, 0], 0.3: [-0.4, 3.5, -1.5], 1.8: [-0.5, 4.0, -1.8]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.3: [-20, -8, 6], 1.8: [-22, -10, 6]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.3: [22, 10, -6], 1.8: [24, 12, -6]}},
    }, loop="hold_on_last_frame")


def spirit_bomb_release():
    return clip(1.2, {
        "root": {"rotation": {0.0: [10, 0, 0], 0.15: [-8, 0, 0], 1.0: [-4, 0, 0], 1.2: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [14, 0, 0], 0.15: [-10, 0, 0], 1.2: [0, 0, 0]}},
        "head": {"rotation": {0.0: [-30, 0, 0], 0.15: [8, 0, 0], 1.2: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-155, 12, 22], 0.15: [-95, 5, 10], 1.0: [-90, 4, 8], 1.2: [-55, -10, 25]},
            "position": {0.0: [0.5, 4.0, -1.8], 0.15: [0.2, 1.2, -4.5], 1.2: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-155, -12, -22], 0.15: [-95, -5, -10], 1.0: [-90, -4, -8], 1.2: [-55, 10, -25]},
            "position": {0.0: [-0.5, 4.0, -1.8], 0.15: [-0.2, 1.2, -4.5], 1.2: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [-22, -10, 6], 0.15: [-30, -12, 8], 1.2: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [24, 12, -6], 0.15: [32, 14, -8], 1.2: [0, 0, 0]}},
    })


def energy_volley():
    return clip(1.1, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [6, -10, 0], 0.45: [4, 10, 0], 0.7: [6, -8, 0], 1.1: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [8, -18, -6], 0.45: [6, 16, 6], 0.7: [8, -14, -4], 1.1: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.45: [-6, 0, 0], 1.1: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -12, 28], 0.2: [-100, 0, 12], 0.45: [-55, -12, 28], 0.7: [-105, 2, 10], 1.1: [-55, -12, 28]},
            "position": {0.0: [0, 0, 0], 0.2: [-0.3, 0.8, -4.8], 0.45: [0, 0, 0], 0.7: [-0.3, 0.8, -5.0], 1.1: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-70, 14, -32], 0.45: [-85, 10, -28], 1.1: [-70, 14, -32]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [-18, 8, 4], 1.1: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [20, 10, -4], 1.1: [0, 0, 0]}},
    })


def explosive_wave():
    return clip(1.0, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.2: [10, 0, 0], 0.4: [-12, 0, 0], 1.0: [0, 0, 0]},
            "scale": {0.0: [1, 1, 1], 0.4: [1.08, 1.08, 1.08], 1.0: [1, 1, 1]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [14, 0, 0], 0.4: [-8, 0, 0], 1.0: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.4: [-8, 0, 0], 1.0: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-50, -15, 25], 0.2: [-30, -40, 50], 0.4: [-80, 30, 70], 1.0: [-50, -15, 25]},
            "position": {0.0: [0, 0, 0], 0.4: [2.5, 1.5, -1.5], 1.0: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-50, 15, -25], 0.2: [-30, 40, -50], 0.4: [-80, -30, -70], 1.0: [-50, 15, -25]},
            "position": {0.0: [0, 0, 0], 0.4: [-2.5, 1.5, -1.5], 1.0: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-20, -15, 10], 1.0: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-20, 15, -10], 1.0: [0, 0, 0]}},
    })


def big_bang():
    return clip(1.05, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [8, -15, -4], 0.4: [-6, 12, 4], 1.05: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [12, -28, -8], 0.4: [-10, 22, 8], 1.05: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.4: [4, -10, 0], 1.05: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -12, 30], 0.2: [-35, -45, 48], 0.4: [-115, 8, 12], 0.75: [-100, 6, 10], 1.05: [-55, -12, 30]},
            "position": {0.0: [0, 0, 0], 0.2: [1.0, 0.6, 1.8], 0.4: [-0.4, 1.1, -5.5], 1.05: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-70, 14, -32], 0.4: [-60, 20, -28], 1.05: [-70, 14, -32]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-28, 14, 6], 1.05: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.2: [18, 10, -6], 0.4: [26, 14, -8], 1.05: [0, 0, 0]}},
    })


def kienzan():
    # frame_1667: arms raised, disc overhead
    return clip(1.15, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.25: [6, 0, 0], 0.55: [-4, 0, 0], 1.15: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.25: [10, 0, 0], 0.55: [4, 0, 0], 1.15: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.25: [-22, 0, 0], 0.55: [-8, 0, 0], 1.15: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 25], 0.25: [-160, 15, 25], 0.55: [-100, 5, 15], 1.15: [-55, -10, 25]},
            "position": {0.0: [0, 0, 0], 0.25: [0.6, 3.8, -1.2], 0.55: [0.3, 1.0, -4.0], 1.15: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-55, 10, -25], 0.25: [-160, -15, -25], 0.55: [-100, -5, -15], 1.15: [-55, 10, -25]},
            "position": {0.0: [0, 0, 0], 0.25: [-0.6, 3.8, -1.2], 0.55: [-0.3, 1.0, -4.0], 1.15: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.25: [-16, -8, 4], 1.15: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.25: [18, 10, -4], 1.15: [0, 0, 0]}},
    })


def dodon_ray():
    # frame_1788: one-finger forward beam pose
    return clip(0.95, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.15: [4, -8, 0], 0.35: [-2, 6, 0], 0.95: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [8, -16, -4], 0.35: [-4, 12, 4], 0.95: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [2, -8, 0], 0.95: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -12, 28], 0.15: [-40, -30, 40], 0.35: [-105, 2, 8], 0.7: [-98, 2, 6], 0.95: [-55, -12, 28]},
            "position": {0.0: [0, 0, 0], 0.15: [0.7, 0.4, 1.2], 0.35: [-0.2, 0.9, -5.6], 0.95: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-70, 14, -32], 0.35: [-65, 18, -28], 0.95: [-70, 14, -32]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-22, 10, 4], 0.95: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.15: [14, 8, -4], 0.35: [22, 12, -6], 0.95: [0, 0, 0]}},
    })


BATCH = [
    {
        "id": "xenopixelsmod:bt3_early_kid_goku_kamehameha_15",
        "starts": [15000],
        "status": "reference_timed_unverified",
        "clips": {
            "combat.xeno_bt3_v3_batch1_kame_15_charge": kame_charge(),
            "combat.xeno_bt3_v3_batch1_kame_15_release": kame_release(),
        },
        "duration": 61,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_kame_15_charge"),
            beat("KI_CHARGE", 3),
            beat("KI_HOLD", 36),
            beat("POSE", 36, "combat.xeno_bt3_v3_batch1_kame_15_release"),
            beat("KI_RELEASE", 39),
            beat("END", 61),
        ],
        "camera": [
            shot(0, 39, [2.8, 1.0, -4.5], focus=0.25, easing="CUT"),
            shot(39, 22, [2.4, 0.8, -3.8], focus=0.3),
        ],
        "note": "Kid Goku Kamehameha — charge cup then forward push from 15s PTS frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_kid_goku_penetra_26",
        "starts": [26000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_penetra_26": penetrate()},
        "duration": 42,
        "beats": [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, "combat.xeno_bt3_v3_batch1_penetra_26"),
            beat("STRIKE", 14, value=0.7),
            beat("KI_CHARGE", 16),
            beat("KI_RELEASE", 24),
            beat("SHOVE", 24, "FORWARD"),
            beat("END", 42),
        ],
        "camera": [shot(0, 10, [1.4, 1.3, -2.4], focus=0.15, easing="CUT"), shot(10, 32, [5.0, 2.0, -5.5], focus=0.55)],
        "note": "Penetrate — straight-arm thrust lean from 26s frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_goku_early_attacco_di_kaiohken_44",
        "starts": [44000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_kaioken_44": kaioken_rush()},
        "duration": 40,
        "beats": [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, "combat.xeno_bt3_v3_batch1_kaioken_44"),
            beat("STRIKE", 12, value=0.8),
            beat("STRIKE", 18, value=1.1),
            beat("STRIKE", 24, value=1.4),
            beat("SHOVE", 24, "FORWARD"),
            beat("END", 40),
        ],
        "camera": [shot(0, 10, [1.5, 1.4, -2.6], focus=0.15, easing="CUT"), shot(10, 30, [5.2, 2.1, -5.8], focus=0.55)],
        "note": "Kaioken Attack — dual-fist rush lean from 44s frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_goku_early_esplosione_solare_69",
        "starts": [69000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_solar_69": solar_flare()},
        "duration": 28,
        "beats": [
            beat("APPROACH", 0, duration=6),
            beat("POSE", 2, "combat.xeno_bt3_v3_batch1_solar_69"),
            beat("HOLD_TARGET", 10, duration=24),
            beat("END", 28),
        ],
        "camera": [shot(0, 8, [2.0, 1.2, -3.0], focus=0.2, easing="CUT"), shot(8, 20, [4.5, 1.8, -5.0], focus=0.5)],
        "note": "Solar Flare — arms up flash from 69s frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_goku_early_sfera_genkidama_84",
        "starts": [84000],
        "status": "reference_timed_unverified",
        "clips": {
            "combat.xeno_bt3_v3_batch1_spirit_84_charge": spirit_bomb_charge(),
            "combat.xeno_bt3_v3_batch1_spirit_84_release": spirit_bomb_release(),
        },
        "duration": 72,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_spirit_84_charge"),
            beat("KI_CHARGE", 4),
            beat("POSE", 40, "combat.xeno_bt3_v3_batch1_spirit_84_release"),
            beat("KI_RELEASE", 44),
            beat("END", 72),
        ],
        "camera": [shot(0, 44, [3.2, 1.6, -5.0], focus=0.2, easing="CUT"), shot(44, 28, [3.0, 1.4, -4.5], focus=0.35)],
        "note": "Spirit Bomb — overhead gather then throw from 84s frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_goku_super_saiyan_s__raffica_onda_energ_282",
        "starts": [282000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_volley_282": energy_volley()},
        # Camera must cover the whole fire window until projectiles finish (owner 2026-03-22).
        "duration": 96,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_volley_282"),
            beat("KI_CHARGE", 4),
            beat("KI_RELEASE", 16),
            beat("END", 96),
        ],
        "camera": [
            shot(0, 16, [2.6, 1.2, -4.2], focus=0.25, easing="CUT"),
            shot(16, 80, [3.4, 1.5, -5.4], focus=0.45),
        ],
        "note": "Super Energy Wave Volley — alternating arm fire from 282s frames; camera held through fire",
    },
    {
        "id": "xenopixelsmod:bt3_early_goku_super_saiyan_3_onda_super_esplosiva_345",
        "starts": [345000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_explode_345": explosive_wave()},
        "duration": 36,
        "beats": [
            beat("APPROACH", 0, duration=6),
            beat("POSE", 2, "combat.xeno_bt3_v3_batch1_explode_345"),
            beat("RADIAL", 16, "10", value=1.6),
            beat("END", 36),
        ],
        "camera": [shot(0, 10, [1.8, 1.5, -2.8], focus=0.15, easing="CUT"), shot(10, 26, [6.0, 2.4, -6.5], focus=0.55)],
        "note": "Super Explosive Wave — radial expand arms from 345s frames",
    },
    {
        "id": "xenopixelsmod:bt3_early_vegito_base_attacco_big_bang_428",
        "starts": [428000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_bigbang_428": big_bang()},
        "duration": 40,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_bigbang_428"),
            beat("KI_CHARGE", 4),
            beat("KI_RELEASE", 18),
            beat("END", 40),
        ],
        "camera": [shot(0, 18, [2.5, 1.1, -4.0], focus=0.25, easing="CUT"), shot(18, 22, [3.0, 1.3, -4.8], focus=0.4)],
        "note": "Big Bang Attack — palm blast chamber then fire from 428s frames",
    },
    {
        "id": "xenopixelsmod:bt3_expanding_energy_wave_kienzan_1667",
        "starts": [1667000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_kienzan_1667": kienzan()},
        "duration": 38,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_kienzan_1667"),
            beat("KI_CHARGE", 4),
            beat("KI_RELEASE", 18),
            beat("END", 38),
        ],
        "camera": [shot(0, 18, [2.4, 1.5, -3.8], focus=0.2, easing="CUT"), shot(18, 20, [3.0, 1.4, -4.6], focus=0.4)],
        "note": "Kienzan — overhead disc from 1667s frames",
    },
    {
        "id": "xenopixelsmod:bt3_tien_dodonpa_1788",
        "starts": [1788000],
        "status": "reference_timed_unverified",
        "clips": {"combat.xeno_bt3_v3_batch1_dodon_1788": dodon_ray()},
        "duration": 28,
        "beats": [
            beat("POSE", 0, "combat.xeno_bt3_v3_batch1_dodon_1788"),
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 10),
            beat("END", 28),
        ],
        "camera": [shot(0, 12, [2.2, 1.1, -3.6], focus=0.25, easing="CUT"), shot(12, 16, [2.8, 1.2, -4.2], focus=0.4)],
        "note": "Dodon Ray — one-finger forward beam from 1788s frames",
    },
]


def main():
    resource = json.loads(RESOURCE.read_text(encoding="utf-8")) if RESOURCE.exists() else {
        "format_version": "1.8.0",
        "animations": {},
    }
    authored = json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {"schema": 1, "entries": []}
    by_id = {e["id"]: e for e in authored.get("entries", [])}

    for item in BATCH:
        for name, anim in item["clips"].items():
            resource["animations"][name] = anim
        entry = {
            "id": item["id"],
            "sourceStartsMs": item["starts"],
            "animationStatus": item["status"],
            "durationTicks": item["duration"],
            "beats": item["beats"],
            "camera": item["camera"],
            "evidence": EVIDENCE_DIR,
            "unverified": [
                item["note"],
                "Bone XYZ angles hand-retargeted from extracted Part 1 frames; not pixel-measured mocap.",
                "Camera distances are Minecraft estimates from BT3 framing.",
                "Requires fresh client comparison before reference_compared.",
            ],
        }
        by_id[item["id"]] = entry
        print("authored", item["id"], "clips", list(item["clips"]))

    RESOURCE.parent.mkdir(parents=True, exist_ok=True)
    RESOURCE.write_text(json.dumps(resource, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    authored["schema"] = 1
    authored["entries"] = sorted(by_id.values(), key=lambda e: e["id"])
    OVERRIDES.write_text(json.dumps(authored, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("resource", RESOURCE)
    print("overrides", len(authored["entries"]))


if __name__ == "__main__":
    main()
