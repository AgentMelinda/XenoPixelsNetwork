"""Generate remaining video-backed Strike choreography in one pass.

Honest scope: unique full-body XYZ clips per remaining occurrence, typed from BT3
archetypes with overshoot allowed. Status = reference_timed_unverified.
Does NOT claim frame-measured 1:1 for every second of the Part 1 MP4 — that needs
per-occurrence visual accept. Frames already extracted for batch1/2 stay linked;
others get typed clips keyed by occurrence id.

Run:
  python tools/author_v3_video_batch_all.py
  python tools/gen_combat_v3_techniques.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TECH = ROOT / "src/main/resources/data/xenopixelsmod/combat_v3/techniques.json"
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
OVERRIDES = ROOT / "docs/combat-v3/choreography.json"


def v(x, y, z):
    return {"vector": [round(float(x), 2), round(float(y), 2), round(float(z), 2)]}


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
    return {
        "tick": tick,
        "duration": duration,
        "position": list(position),
        "look": [0, 0, 0],
        "focus": focus,
        "easing": easing,
    }


def slug(tech_id: str) -> str:
    # V3AnimationCatalog + V3Beat payload cap names at 64 chars (prefix is 18).
    import hashlib
    digest = hashlib.sha1(tech_id.encode("utf-8")).hexdigest()[:10]
    return "a" + digest


# --- typed full-body clips (realistic XYZ, no waist-clipping extremes) -------------

def beam_charge():
    return clip(1.5, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [0, -10, 0], 1.4: [0, -10, 0]}},
        "waist": {"rotation": {0.0: [8, 0, 0], 0.2: [14, -18, -4], 1.4: [14, -18, -4]}},
        "head": {"rotation": {0.0: [-6, 0, 0], 0.2: [-8, 22, 0], 1.4: [-8, 22, 0]}},
        "right_arm": {
            "rotation": {0.0: [-40, -12, 22], 0.2: [-30, -36, 32], 1.4: [-30, -36, 32]},
            "position": {0.0: [0, 0, 0], 0.2: [0.4, 0.3, 0.9], 1.4: [0.4, 0.3, 0.9]},
        },
        "left_arm": {
            "rotation": {0.0: [-40, 12, -22], 0.2: [-48, -28, -28], 1.4: [-48, -28, -28]},
            "position": {0.0: [0, 0, 0], 0.2: [-0.35, 0.25, 0.8], 1.4: [-0.35, 0.25, 0.8]},
        },
        "right_leg": {"rotation": {0.0: [-14, -8, 5], 0.2: [-22, -12, 6], 1.4: [-22, -12, 6]}},
        "left_leg": {"rotation": {0.0: [16, 8, -5], 0.2: [24, 12, -6], 1.4: [24, 12, -6]}},
    }, loop="hold_on_last_frame")


def beam_release():
    return clip(1.0, {
        "root": {"rotation": {0.0: [0, -10, 0], 0.12: [0, 0, 0], 1.0: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [14, -18, -4], 0.12: [12, 4, 0], 1.0: [8, 0, 0]}},
        "head": {"rotation": {0.0: [-8, 22, 0], 0.12: [-10, -4, 0], 1.0: [-6, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-30, -36, 32], 0.12: [-100, -4, 6], 0.85: [-100, -4, 6], 1.0: [-40, -12, 22]},
            "position": {0.0: [0.4, 0.3, 0.9], 0.12: [-0.25, 0.6, -3.8], 0.85: [-0.25, 0.6, -3.8], 1.0: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-48, -28, -28], 0.12: [-100, 4, -6], 0.85: [-100, 4, -6], 1.0: [-40, 12, -22]},
            "position": {0.0: [-0.35, 0.25, 0.8], 0.12: [0.25, 0.6, -3.6], 0.85: [0.25, 0.6, -3.6], 1.0: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [-22, -12, 6], 0.12: [-28, -14, 8], 1.0: [-14, -8, 5]}},
        "left_leg": {"rotation": {0.0: [24, 12, -6], 0.12: [22, 14, -8], 1.0: [16, 8, -5]}},
    })


def melee_combo():
    return clip(1.05, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.15: [6, -12, 0], 0.35: [-5, 10, 0], 0.55: [6, -8, 0], 0.75: [-6, 12, 0], 1.05: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.35: [0, 0.08, -0.4], 0.75: [0, 0.1, -0.55], 1.05: [0, 0, 0]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [10, -20, -6], 0.35: [-8, 18, 6], 0.55: [8, -14, -5], 0.75: [-10, 22, 8], 1.05: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [4, -8, 0], 0.75: [5, -10, 0], 1.05: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 25], 0.15: [-38, -32, 38], 0.35: [-108, 5, 8], 0.55: [-50, -16, 28], 0.75: [-112, 6, 8], 1.05: [-55, -10, 25]},
            "position": {0.0: [0, 0, 0], 0.35: [-0.3, 0.8, -3.6], 0.75: [-0.35, 0.85, -3.9], 1.05: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-60, 12, -28], 0.35: [-68, 16, -26], 0.75: [-55, 18, -24], 1.05: [-60, 12, -28]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-24, 10, 5], 0.75: [-30, 12, 6], 1.05: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.15: [16, 8, -5], 0.55: [14, 6, -4], 0.75: [26, 12, -6], 1.05: [0, 0, 0]}},
    })


def kick_strike():
    return clip(0.95, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.15: [8, -8, -4], 0.35: [-10, 12, 6], 0.95: [0, 0, 0]},
                 "position": {0.0: [0, 0, 0], 0.35: [0, 0.2, -0.35], 0.95: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: [-8, -16, 6], 0.35: [-14, 20, 8], 0.95: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [-8, -8, 4], 0.95: [0, 0, 0]}},
        "right_leg": {
            "rotation": {0.0: [0, 0, 0], 0.15: [-70, -16, 10], 0.35: [-115, 18, 12], 0.6: [-100, 14, 10], 0.95: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.15: [0.3, 1.4, 0.8], 0.35: [-0.2, 1.2, -3.5], 0.6: [-0.15, 0.8, -2.5], 0.95: [0, 0, 0]},
        },
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-28, 22, 8], 0.95: [0, 0, 0]}},
        "right_arm": {"rotation": {0.0: [-55, -12, 28], 0.35: [-58, -16, 32], 0.95: [-55, -12, 28]}},
        "left_arm": {"rotation": {0.0: [-70, 14, -30], 0.35: [-80, 18, -28], 0.95: [-70, 14, -30]}},
    })


def hold_flash():
    return clip(1.0, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [6, 0, 0], 0.8: [4, 0, 0], 1.0: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [10, 0, 0], 0.8: [8, 0, 0], 1.0: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [-28, 0, 0], 0.8: [-24, 0, 0], 1.0: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-50, -12, 22], 0.2: [-100, -10, 28], 0.4: [-165, 4, 16], 0.8: [-160, 4, 14], 1.0: [-50, -12, 22]},
            "position": {0.0: [0, 0, 0], 0.4: [0.45, 4.2, -1.0], 0.8: [0.4, 3.8, -0.8], 1.0: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-50, 12, -22], 0.2: [-100, 10, -28], 0.4: [-165, -4, -16], 0.8: [-160, -4, -14], 1.0: [-50, 12, -22]},
            "position": {0.0: [0, 0, 0], 0.4: [-0.45, 4.2, -1.0], 0.8: [-0.4, 3.8, -0.8], 1.0: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-12, -5, 3], 1.0: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [14, 6, -3], 1.0: [0, 0, 0]}},
    })


def radial_burst():
    return clip(0.95, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.25: [8, 0, 0], 0.45: [-8, 0, 0], 0.95: [0, 0, 0]},
                 "scale": {0.0: [1, 1, 1], 0.45: [1.08, 1.08, 1.08], 0.95: [1, 1, 1]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.25: [10, 0, 0], 0.45: [-6, 0, 0], 0.95: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.45: [-6, 0, 0], 0.95: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-50, -12, 22], 0.25: [-30, -28, 40], 0.45: [-75, 22, 55], 0.95: [-50, -12, 22]},
            "position": {0.0: [0, 0, 0], 0.45: [1.8, 1.0, -1.0], 0.95: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-50, 12, -22], 0.25: [-30, 28, -40], 0.45: [-75, -22, -55], 0.95: [-50, 12, -22]},
            "position": {0.0: [0, 0, 0], 0.45: [-1.8, 1.0, -1.0], 0.95: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [-16, -10, 6], 0.95: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [-16, 10, -6], 0.95: [0, 0, 0]}},
    })


def timelines(typ: str, clip_base: str):
    charge = f"{clip_base}_charge"
    release = f"{clip_base}_release"
    pose = f"{clip_base}_pose"
    if typ in ("beam", "laser", "disc", "ball", "giant_ball", "volley"):
        duration = 64 if typ != "laser" else 36
        if typ == "volley":
            duration = 96
        if typ == "giant_ball":
            duration = 72
        clips = {charge: beam_charge(), release: beam_release()}
        beats = [
            beat("POSE", 0, charge),
            beat("KI_CHARGE", 3),
            beat("KI_HOLD", max(20, duration // 2 - 4)),
            beat("POSE", max(20, duration // 2 - 4), release),
            beat("KI_RELEASE", max(24, duration // 2)),
            beat("END", duration),
        ]
        cam = [
            shot(0, max(24, duration // 2), [2.8, 1.1, -4.4], 0.25, "CUT"),
            shot(max(24, duration // 2), duration - max(24, duration // 2), [3.2, 1.3, -5.0], 0.4),
        ]
        return duration, clips, beats, cam
    if typ == "hold":
        clips = {pose: hold_flash()}
        beats = [beat("APPROACH", 0, duration=6), beat("POSE", 2, pose), beat("HOLD_TARGET", 10, duration=36), beat("END", 40)]
        cam = [shot(0, 10, [2.0, 1.3, -3.2], 0.2, "CUT"), shot(10, 30, [4.5, 1.8, -5.0], 0.5)]
        return 40, clips, beats, cam
    if typ == "radial":
        clips = {pose: radial_burst()}
        beats = [beat("APPROACH", 0, duration=6), beat("POSE", 2, pose), beat("RADIAL", 16, "10", 1.5), beat("END", 40)]
        cam = [shot(0, 10, [1.8, 1.4, -2.8], 0.15, "CUT"), shot(10, 30, [5.5, 2.2, -6.0], 0.55)]
        return 40, clips, beats, cam
    if typ == "melee_energy":
        clips = {pose: melee_combo(), release: beam_release()}
        beats = [
            beat("APPROACH", 0, duration=8), beat("POSE", 2, pose),
            beat("STRIKE", 12, value=0.6), beat("STRIKE", 18, value=0.85),
            beat("SHOVE", 20, "FORWARD"), beat("KI_CHARGE", 22), beat("POSE", 30, release),
            beat("KI_RELEASE", 34), beat("END", 56),
        ]
        cam = [shot(0, 12, [1.5, 1.3, -2.5], 0.15, "CUT"), shot(12, 44, [5.0, 2.0, -5.5], 0.5)]
        return 56, clips, beats, cam
    if typ == "grab":
        clips = {pose: melee_combo()}
        beats = [beat("APPROACH", 0, duration=8), beat("POSE", 2, pose), beat("HOLD_TARGET", 12, duration=16),
                 beat("STRIKE", 26, value=1.4), beat("SHOVE", 26, "FORWARD"), beat("END", 42)]
        cam = [shot(0, 12, [1.5, 1.3, -2.5], 0.15, "CUT"), shot(12, 30, [5.0, 2.0, -5.5], 0.5)]
        return 42, clips, beats, cam
    # melee default — alternate punch/kick flavored pose
    clips = {pose: melee_combo() if hash(clip_base) % 2 == 0 else kick_strike()}
    beats = [
        beat("APPROACH", 0, duration=8), beat("POSE", 2, pose),
        beat("STRIKE", 12, value=0.7), beat("STRIKE", 18, value=0.95), beat("STRIKE", 24, value=1.35),
        beat("SHOVE", 24, "FORWARD"), beat("END", 44),
    ]
    cam = [shot(0, 10, [1.5, 1.4, -2.5], 0.15, "CUT"), shot(10, 34, [5.2, 2.1, -5.8], 0.55)]
    return 44, clips, beats, cam


def main():
    tech_data = json.loads(TECH.read_text(encoding="utf-8"))
    techs = tech_data["techniques"]
    resource = json.loads(RESOURCE.read_text(encoding="utf-8")) if RESOURCE.exists() else {"format_version": "1.8.0", "animations": {}}
    authored = json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {"schema": 1, "entries": []}
    by_id = {e["id"]: e for e in authored.get("entries", [])}

    written = 0
    preserved = 0
    for tech in techs:
        tid = tech["id"]
        existing = by_id.get(tid)
        # Keep already video-batched reference_timed entries (batch1/2) unless they lack unique clips
        if existing and existing.get("animationStatus") == "reference_timed_unverified":
            poses = [b.get("payload") for b in existing.get("beats", []) if b.get("kind") == "POSE"]
            if poses and all(p and p in resource.get("animations", {}) for p in poses if p):
                preserved += 1
                continue
        base = "combat.xeno_bt3_v3_all_" + slug(tid)
        duration, clips, beats, camera = timelines(tech.get("type") or "melee", base)
        for name, anim in clips.items():
            resource.setdefault("animations", {})[name] = anim
        by_id[tid] = {
            "id": tid,
            "sourceStartsMs": list(tech.get("sourceStartsMs") or [0]),
            "animationStatus": "reference_timed_unverified",
            "durationTicks": duration,
            "beats": beats,
            "camera": camera,
            "unverified": [
                "Bulk video-backed typed full-body XYZ clips (author_v3_video_batch_all.py).",
                "Not frame-measured 1:1 for every Part 1 second; needs in-game accept for reference_compared.",
                "Camera hold also extended globally via v3.strikeCameraHoldTicks.",
            ],
        }
        written += 1

    RESOURCE.write_text(json.dumps(resource, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    authored["schema"] = 1
    authored["entries"] = sorted(by_id.values(), key=lambda e: e["id"])
    OVERRIDES.write_text(json.dumps(authored, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"written={written} preserved={preserved} entries={len(authored['entries'])} anims={len(resource['animations'])}")


if __name__ == "__main__":
    main()
