"""Author the Meteor Combination at 116 s from the local BT3 Part 1 footage.

The reference has a low airborne wind-up, opening kicks, a fast close-range
combination, an inverted camera beat, then a second flurry ending at 19 hits.
Keyframe angles remain authored estimates until a fresh in-game comparison.
Run this after the bulk pipeline and before gen_combat_v3_techniques.py.
"""
from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TECHNIQUE_ID = "xenopixelsmod:bt3_early_goku_torn_shirt_combinaz__di_meteoriti_116"
CLIP_ID = "combat.xeno_bt3_v3_batch2_meteor_116"
ANIMATIONS = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
CHOREOGRAPHY = ROOT / "docs/combat-v3/choreography.json"
EVIDENCE = "docs/combat-v3/video-pipeline-302/meteor-combination-116"
BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")
HITS = (3.0, 3.3, 3.6, 3.75, 3.9, 4.05, 4.25, 4.55,
        7.1, 7.25, 7.4, 7.55, 7.7, 7.85, 8.0, 8.15, 8.35, 8.6, 8.9)


def _vector(value: tuple[float, float, float]) -> dict:
    return {"vector": [round(float(n), 2) for n in value]}


def _clip() -> dict:
    bones: dict[str, dict[str, dict[str, dict]]] = {
        name: {"rotation": {}, "position": {}} for name in BONES
    }

    def pose(time: float, bone: str, rotation=None, position=None) -> None:
        key = f"{time:.3f}".rstrip("0").rstrip(".")
        if rotation is not None:
            bones[bone]["rotation"][key] = _vector(rotation)
        if position is not None:
            bones[bone]["position"][key] = _vector((0, 0, 0) if bone == "root" else position)

    for bone in BONES:
        pose(0.0, bone, (0, 0, 0), (0, 0, 0))

    # 117-118 s: Goku hangs low with both hands spread, then drives at the target.
    pose(1.0, "root", (18, -8, 0), (0, 0.18, -0.3))
    pose(1.0, "waist", (28, -10, 0))
    pose(1.0, "head", (-14, 8, 0))
    for side, sign in (("right", 1), ("left", -1)):
        pose(1.0, f"{side}_arm", (-55, sign * 8, sign * 22), (sign * 1.1, -0.7, -1.0))
        pose(1.0, f"{side}_leg", (-26, sign * 8, sign * 5), (sign * 0.2, 0, 0.2))
    pose(2.4, "root", (34, -16, 4), (0, 0.35, -1.0))
    pose(2.4, "waist", (30, -20, 5))
    pose(2.4, "head", (-20, 14, -3))

    for index, hit in enumerate(HITS):
        kick = index in (0, 1, 6)
        side = "right" if index % 2 == 0 else "left"
        other = "left" if side == "right" else "right"
        sign = 1 if side == "right" else -1
        pre = hit - (0.12 if index == 0 or hit - HITS[index - 1] > 0.16 else 0.05)
        pose(pre, "root", (10, -sign * 9, sign * 4), (0, 0.1, -0.55))
        pose(pre, "waist", (12, -sign * 20, sign * 7))
        pose(pre, "head", (-5, sign * 8, 0))
        if kick:
            pose(pre, f"{side}_leg", (-60, sign * 12, sign * 10), (sign * 0.2, 0.25, 0.55))
            pose(hit, f"{side}_leg", (-122, sign * 22, sign * 16),
                 (sign * 0.25, 0.35, -3.8))
            pose(hit, f"{other}_leg", (-24, -sign * 12, -sign * 4),
                 (-sign * 0.15, -0.08, 0.2))
            for arm, arm_sign in (("right_arm", 1), ("left_arm", -1)):
                pose(hit, arm, (-70, arm_sign * 16, arm_sign * 20),
                     (arm_sign * 0.2, 0.1, -0.3))
        else:
            pose(pre, f"{side}_arm", (-65, -sign * 25, sign * 20),
                 (sign * 0.2, 0.15, 0.35))
            pose(hit, f"{side}_arm", (-122, sign * 6, sign * 8),
                 (-sign * 0.35, 0.35, -4.0))
            # The opposite fist stays by the ribs instead of swinging behind the body.
            pose(hit, f"{other}_arm", (-68, -sign * 16, -sign * 18),
                 (-sign * 0.15, 0.12, -0.35))
            pose(hit, "right_leg", (-18, 8, 3), (0.12, -0.08, 0.15))
            pose(hit, "left_leg", (16, -8, -3), (-0.12, -0.08, 0.15))
        pose(hit, "root", (-8, sign * 14, -sign * 6), (0, 0.18, -0.95))
        pose(hit, "waist", (-16, sign * 28, -sign * 10))
        pose(hit, "head", (5, -sign * 7, 0))

    # 122-123 s: a suspended inverted turn divides the two close-range flurries.
    pose(5.6, "root", (148, -18, 12), (0, 0.5, -0.35))
    pose(5.6, "waist", (24, -10, 0))
    pose(5.6, "head", (-24, 6, 0))
    pose(5.6, "right_arm", (-55, -12, 25), (0.8, -0.75, -0.8))
    pose(5.6, "left_arm", (-55, 12, -25), (-0.8, -0.75, -0.8))
    pose(5.6, "right_leg", (-25, 8, 5), (0.25, 0.15, 0.15))
    pose(5.6, "left_leg", (25, -8, -5), (-0.25, 0.15, 0.15))
    pose(6.7, "root", (30, 15, -8), (0, 0.3, -0.7))
    pose(6.7, "waist", (22, 12, 0))

    # 125-126 s: the last hit launches the victim; both hands return to guard.
    for bone in BONES:
        pose(9.55, bone, (0, 0, 0), (0, 0, 0))
    pose(9.55, "right_arm", (-55, -12, 22), (0.15, 0, -0.2))
    pose(9.55, "left_arm", (-55, 12, -22), (-0.15, 0, -0.2))
    for channels in bones.values():
        for name, frames in channels.items():
            channels[name] = dict(sorted(frames.items(), key=lambda item: float(item[0])))
    return {"animation_length": 9.8, "bones": bones}


def _beat(kind: str, tick: int, payload: str = "", duration: int = 0,
          value: float = 0.0) -> dict:
    return {"kind": kind, "tick": tick, "duration": duration,
            "payload": payload, "value": float(value)}


def _shot(tick: int, duration: int, position: tuple[float, float, float],
          focus: float, easing: str) -> dict:
    return {"tick": tick, "duration": duration, "position": list(position),
            "look": [0, 0, 0], "focus": focus, "easing": easing}


def main() -> None:
    resource = json.loads(ANIMATIONS.read_text(encoding="utf-8"))
    authored = json.loads(CHOREOGRAPHY.read_text(encoding="utf-8"))
    matches = [entry for entry in authored["entries"] if entry["id"] == TECHNIQUE_ID]
    if len(matches) != 1:
        raise ValueError(f"Expected one choreography entry for {TECHNIQUE_ID}, found {len(matches)}")
    resource["animations"][CLIP_ID] = _clip()
    row = matches[0]
    row["durationTicks"] = 200
    row["beats"] = [_beat("APPROACH", 0, duration=55), _beat("POSE", 0, CLIP_ID)]
    for index, hit in enumerate(HITS):
        row["beats"].append(_beat("STRIKE", round(hit * 20),
                                  value=1.2 if index == len(HITS) - 1 else 0.23))
    row["beats"] += [_beat("SHOVE", 182, "FORWARD"), _beat("END", 200)]
    row["camera"] = [
        _shot(0, 48, (2.4, 1.2, -3.2), 0.18, "CUT"),
        _shot(48, 64, (4.6, 1.8, -5.4), 0.52, "SMOOTH"),
        _shot(112, 24, (2.2, 3.4, -4.1), 0.35, "CUT"),
        _shot(136, 64, (5.4, 2.0, -5.8), 0.58, "SMOOTH"),
    ]
    row["evidence"] = EVIDENCE
    ANIMATIONS.write_text(json.dumps(resource, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    CHOREOGRAPHY.write_text(json.dumps(authored, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Authored {TECHNIQUE_ID}: {len(HITS)} strikes, 200 ticks, seven XYZ bones")


if __name__ == "__main__":
    main()
