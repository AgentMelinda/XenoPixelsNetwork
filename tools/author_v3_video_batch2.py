"""Video-backed choreography batch 2 — next 10 Part 1 occurrences.

Frames in docs/combat-v3/video-batch2/. Unique full-body clips; status reference_timed_unverified.
Run: python tools/author_v3_video_batch2.py && python tools/gen_combat_v3_techniques.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
OVERRIDES = ROOT / "docs/combat-v3/choreography.json"
EVIDENCE = "docs/combat-v3/video-batch2"


def v(x, y, z):
    return {"vector": [round(x, 2), round(y, 2), round(z, 2)]}


def ch(frames):
    return {str(t): v(*xyz) for t, xyz in frames.items()}


def clip(length, bones, loop=False):
    out = {"animation_length": length, "bones": {}}
    if loop:
        out["loop"] = loop
    for bone, channels in bones.items():
        entry = {}
        for name, frames in channels.items():
            entry[name] = ch(frames)
        out["bones"][bone] = entry
    return out


def beat(kind, tick, payload="", duration=0, value=0.0):
    return {"kind": kind, "tick": tick, "duration": duration, "payload": payload, "value": float(value)}


def shot(tick, duration, position, focus=0.4, easing="SMOOTH"):
    return {"tick": tick, "duration": duration, "position": list(position), "look": [0, 0, 0],
            "focus": focus, "easing": easing}


def kame_charge():
    return clip(1.6, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [0, -12, 0], 1.5: [0, -12, 0]}},
        "waist": {"rotation": {0.0: [10, 0, 0], 0.2: [18, -22, -4], 1.5: [18, -22, -4]}},
        "head": {"rotation": {0.0: [-6, 0, 0], 0.2: [-8, 30, 0], 1.5: [-8, 30, 0]}},
        "right_arm": {"rotation": {0.0: [-40, -15, 25], 0.2: [-28, -42, 35], 1.5: [-28, -42, 35]},
                      "position": {0.0: [0, 0, 0], 0.2: [0.5, 0.3, 1.0], 1.5: [0.5, 0.3, 1.0]}},
        "left_arm": {"rotation": {0.0: [-40, 15, -25], 0.2: [-50, -30, -30], 1.5: [-50, -30, -30]},
                     "position": {0.0: [0, 0, 0], 0.2: [-0.4, 0.3, 0.9], 1.5: [-0.4, 0.3, 0.9]}},
        "right_leg": {"rotation": {0.0: [-15, -10, 6], 0.2: [-24, -14, 8], 1.5: [-24, -14, 8]}},
        "left_leg": {"rotation": {0.0: [18, 10, -6], 0.2: [28, 16, -8], 1.5: [28, 16, -8]}},
    }, loop="hold_on_last_frame")


def kame_release():
    return clip(1.1, {
        "root": {"rotation": {0.0: [0, -12, 0], 0.1: [0, 0, 0], 1.1: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [18, -22, -4], 0.1: [16, 4, 0], 1.1: [10, 0, 0]}},
        "head": {"rotation": {0.0: [-8, 30, 0], 0.1: [-12, -4, 0], 1.1: [-6, 0, 0]}},
        "right_arm": {"rotation": {0.0: [-28, -42, 35], 0.1: [-95, -4, 6], 0.9: [-95, -4, 6], 1.1: [-40, -15, 25]},
                      "position": {0.0: [0.5, 0.3, 1.0], 0.1: [-0.3, 0.7, -4.8], 0.9: [-0.3, 0.7, -4.8], 1.1: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: [-50, -30, -30], 0.1: [-95, 4, -6], 0.9: [-95, 4, -6], 1.1: [-40, 15, -25]},
                     "position": {0.0: [-0.4, 0.3, 0.9], 0.1: [0.3, 0.7, -4.5], 0.9: [0.3, 0.7, -4.5], 1.1: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [-24, -14, 8], 0.1: [-32, -16, 10], 1.1: [-15, -10, 6]}},
        "left_leg": {"rotation": {0.0: [28, 16, -8], 0.1: [24, 18, -10], 1.1: [18, 10, -6]}},
    })


def meteor_combo():
    return clip(1.2, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.15: [8, -15, 0], 0.35: [-6, 12, 0], 0.55: [8, -10, 0], 0.8: [-8, 15, 0], 1.2: [0, 0, 0]},
                 "position": {0.0: [0, 0, 0], 0.35: [0, 0.1, -0.5], 0.8: [0, 0.15, -0.8], 1.2: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [12, -25, -8], 0.35: [-10, 22, 8], 0.55: [10, -18, -6], 0.8: [-12, 28, 10], 1.2: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [4, -10, 0], 0.8: [6, -12, 0], 1.2: [0, 0, 0]}},
        "right_arm": {"rotation": {0.0: [-55, -12, 28], 0.15: [-40, -40, 45], 0.35: [-115, 6, 10], 0.55: [-50, -20, 30], 0.8: [-120, 8, 8], 1.2: [-55, -12, 28]},
                      "position": {0.0: [0, 0, 0], 0.35: [-0.4, 1.0, -5.5], 0.8: [-0.5, 1.1, -6.0], 1.2: [0, 0, 0]}},
        "left_arm": {"rotation": {0.0: [-60, 14, -30], 0.35: [-70, 18, -28], 0.8: [-55, 20, -25], 1.2: [-60, 14, -30]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-28, 12, 6], 0.8: [-35, 16, 8], 1.2: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.15: [20, 10, -6], 0.55: [16, 8, -4], 0.8: [30, 14, -8], 1.2: [0, 0, 0]}},
    })


BATCH = [
    ("xenopixelsmod:bt3_early_goku_early_kamehameha_60", [60000], "Kamehameha", "beam",
     {"combat.xeno_bt3_v3_batch2_kame_60_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_kame_60_release": kame_release()},
     64, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_kame_60_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 36), beat("POSE", 36, "combat.xeno_bt3_v3_batch2_kame_60_release"),
         beat("KI_RELEASE", 40), beat("END", 64),
     ], [shot(0, 40, [2.8, 1.0, -4.5], 0.25, "CUT"), shot(40, 24, [2.5, 0.9, -4.0], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_early_kamehameha_77", [77000], "Kamehameha", "beam",
     {"combat.xeno_bt3_v3_batch2_kame_77_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_kame_77_release": kame_release()},
     64, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_kame_77_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 36), beat("POSE", 36, "combat.xeno_bt3_v3_batch2_kame_77_release"),
         beat("KI_RELEASE", 40), beat("END", 64),
     ], [shot(0, 40, [2.8, 1.0, -4.5], 0.25, "CUT"), shot(40, 24, [2.5, 0.9, -4.0], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_torn_shirt_kamehameha_kaiohken_x20_99", [99000], "Kamehameha Kaiohken x20", "beam",
     {"combat.xeno_bt3_v3_batch2_kame_kk_99_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_kame_kk_99_release": kame_release()},
     68, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_kame_kk_99_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 38), beat("POSE", 38, "combat.xeno_bt3_v3_batch2_kame_kk_99_release"),
         beat("KI_RELEASE", 42), beat("END", 68),
     ], [shot(0, 42, [3.0, 1.1, -4.8], 0.25, "CUT"), shot(42, 26, [2.6, 1.0, -4.2], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_torn_shirt_combinaz__di_meteoriti_116", [116000], "Meteor Combination", "melee",
     {"combat.xeno_bt3_v3_batch2_meteor_116": meteor_combo()},
     48, [
         beat("APPROACH", 0, duration=8), beat("POSE", 2, "combat.xeno_bt3_v3_batch2_meteor_116"),
         beat("STRIKE", 12, value=0.7), beat("STRIKE", 20, value=0.9), beat("STRIKE", 28, value=1.3),
         beat("SHOVE", 28, "FORWARD"), beat("END", 48),
     ], [shot(0, 10, [1.5, 1.4, -2.5], 0.15, "CUT"), shot(10, 38, [5.2, 2.2, -5.8], 0.55)]),
    ("xenopixelsmod:bt3_early_goku_torn_shirt_kamehameha_kaiohken_x20_127", [127000], "Kamehameha Kaiohken x20", "beam",
     {"combat.xeno_bt3_v3_batch2_kame_kk_127_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_kame_kk_127_release": kame_release()},
     68, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_kame_kk_127_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 38), beat("POSE", 38, "combat.xeno_bt3_v3_batch2_kame_kk_127_release"),
         beat("KI_RELEASE", 42), beat("END", 68),
     ], [shot(0, 42, [3.0, 1.1, -4.8], 0.25, "CUT"), shot(42, 26, [2.6, 1.0, -4.2], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_torn_shirt_sfera_genkidama_142", [142000], "Spirit Bomb", "giant_ball",
     {"combat.xeno_bt3_v3_batch2_spirit_142_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_spirit_142_release": kame_release()},
     72, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_spirit_142_charge"), beat("KI_CHARGE", 4),
         beat("POSE", 44, "combat.xeno_bt3_v3_batch2_spirit_142_release"), beat("KI_RELEASE", 48), beat("END", 72),
     ], [shot(0, 48, [3.2, 1.6, -5.0], 0.2, "CUT"), shot(48, 24, [3.0, 1.4, -4.5], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_super_saiyan_super_kamehameha_151", [151000], "Super Kamehameha", "beam",
     {"combat.xeno_bt3_v3_batch2_skame_151_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_skame_151_release": kame_release()},
     70, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_skame_151_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 40), beat("POSE", 40, "combat.xeno_bt3_v3_batch2_skame_151_release"),
         beat("KI_RELEASE", 44), beat("END", 70),
     ], [shot(0, 44, [3.2, 1.2, -5.0], 0.25, "CUT"), shot(44, 26, [2.8, 1.0, -4.4], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_super_saiyan_attacco_di_meteoriti_163", [163000], "Meteor Attack", "melee",
     {"combat.xeno_bt3_v3_batch2_meteor_163": meteor_combo()},
     48, [
         beat("APPROACH", 0, duration=8), beat("POSE", 2, "combat.xeno_bt3_v3_batch2_meteor_163"),
         beat("STRIKE", 12, value=0.75), beat("STRIKE", 20, value=1.0), beat("STRIKE", 28, value=1.4),
         beat("SHOVE", 28, "FORWARD"), beat("END", 48),
     ], [shot(0, 10, [1.5, 1.4, -2.5], 0.15, "CUT"), shot(10, 38, [5.2, 2.2, -5.8], 0.55)]),
    ("xenopixelsmod:bt3_early_goku_super_saiyan_kamehameha_furiosa_179", [179000], "Angry Kamehameha", "beam",
     {"combat.xeno_bt3_v3_batch2_angry_179_charge": kame_charge(), "combat.xeno_bt3_v3_batch2_angry_179_release": kame_release()},
     66, [
         beat("POSE", 0, "combat.xeno_bt3_v3_batch2_angry_179_charge"), beat("KI_CHARGE", 3),
         beat("KI_HOLD", 38), beat("POSE", 38, "combat.xeno_bt3_v3_batch2_angry_179_release"),
         beat("KI_RELEASE", 42), beat("END", 66),
     ], [shot(0, 42, [3.0, 1.1, -4.8], 0.25, "CUT"), shot(42, 24, [2.6, 1.0, -4.2], 0.35)]),
    ("xenopixelsmod:bt3_early_goku_base_attacco_di_meteoriti_213", [213000], "Meteor Attack", "melee",
     {"combat.xeno_bt3_v3_batch2_meteor_213": meteor_combo()},
     48, [
         beat("APPROACH", 0, duration=8), beat("POSE", 2, "combat.xeno_bt3_v3_batch2_meteor_213"),
         beat("STRIKE", 12, value=0.75), beat("STRIKE", 20, value=1.0), beat("STRIKE", 28, value=1.4),
         beat("SHOVE", 28, "FORWARD"), beat("END", 48),
     ], [shot(0, 10, [1.5, 1.4, -2.5], 0.15, "CUT"), shot(10, 38, [5.2, 2.2, -5.8], 0.55)]),
]


def main():
    resource = json.loads(RESOURCE.read_text(encoding="utf-8")) if RESOURCE.exists() else {"format_version": "1.8.0", "animations": {}}
    authored = json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {"schema": 1, "entries": []}
    by_id = {e["id"]: e for e in authored.get("entries", [])}
    for tid, starts, name, typ, clips, duration, beats, camera in BATCH:
        for cname, anim in clips.items():
            resource["animations"][cname] = anim
        by_id[tid] = {
            "id": tid, "sourceStartsMs": starts, "animationStatus": "reference_timed_unverified",
            "durationTicks": duration, "beats": beats, "camera": camera, "evidence": EVIDENCE,
            "unverified": [f"Batch2 video-backed {name} ({typ})", "Hand-retargeted XYZ bones from Part 1 frames",
                           "Camera hold also extended globally via v3.strikeCameraHoldTicks"],
        }
        print("authored", tid)
    RESOURCE.write_text(json.dumps(resource, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    authored["schema"] = 1
    authored["entries"] = sorted(by_id.values(), key=lambda e: e["id"])
    OVERRIDES.write_text(json.dumps(authored, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("entries", len(authored["entries"]))


if __name__ == "__main__":
    main()
