"""Full-302 BT3 Part 1 video choreography pipeline.

Does three things:
  1) Extracts evidence frames for every technique from the local Part 1 MP4
     (start, +0.35s, +0.70s) into docs/combat-v3/video-pipeline-302/frames/
  2) Authors a unique full-body seven-bone XYZ clip set per technique
     (rotation + position overshoot allowed; hip Y capped so waist stays socketed)
  3) Writes choreography.json overrides with durationTicks large enough to cover
     every POSE clip, then expects: python tools/gen_combat_v3_techniques.py

Honest status: animationStatus = reference_timed_unverified.
This is the scripted full-catalog pass the owner asked for; chunked frame review
still decides reference_compared. Batch1/2 hand entries are preserved when present.

Run from repo root:
  python tools/author_v3_video_pipeline_302.py              # author + frames index
  python tools/author_v3_video_pipeline_302.py --extract    # also ffmpeg all frames
  python tools/gen_combat_v3_techniques.py
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TECH = ROOT / "src/main/resources/data/xenopixelsmod/combat_v3/techniques.json"
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
OVERRIDES = ROOT / "docs/combat-v3/choreography.json"
EVIDENCE = ROOT / "docs/combat-v3/video-pipeline-302"
FRAMES = EVIDENCE / "frames"
REVIEW_INDEX = EVIDENCE / "REVIEW_INDEX.md"
MP4_DEFAULT = Path(
    r"C:\Users\Admin\Downloads\Dragon Ball Budokai Tenkaichi 3 - All Attacks And Transformations [Part 1] - simorollo91 simorollo91 (720p).mp4"
)
PREFIX = "combat.xeno_bt3_v3_"
BONES = ["root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg"]


def v(x, y, z):
    return {"vector": [round(float(x), 2), round(float(y), 2), round(float(z), 2)]}


def ch(frames):
    return {str(t): v(*xyz) for t, xyz in frames.items()}


def clip(length, bones, loop=False):
    out = {"animation_length": float(length), "bones": {}}
    if loop:
        out["loop"] = loop
    for bone, channels in bones.items():
        entry = {}
        for name, frames in channels.items():
            entry[name] = ch(frames)
        out["bones"][bone] = entry
    return out


def beat(kind, tick, payload="", duration=0, value=0.0):
    return {
        "kind": kind,
        "tick": int(tick),
        "duration": int(duration),
        "payload": payload,
        "value": float(value),
    }


def shot(tick, duration, position, focus=0.4, easing="SMOOTH"):
    return {
        "tick": int(tick),
        "duration": int(duration),
        "position": [float(x) for x in position],
        "look": [0.0, 0.0, 0.0],
        "focus": float(focus),
        "easing": easing,
    }


def slug(tech_id: str) -> str:
    """Keep clip names under 64 with PREFIX (18) + 'a' + 10 hex + optional suffix."""
    digest = hashlib.sha1(tech_id.encode("utf-8")).hexdigest()[:10]
    return "a" + digest


def salt(tech_id: str) -> int:
    return int(hashlib.sha1(tech_id.encode("utf-8")).hexdigest()[:8], 16)


def ticks(seconds: float) -> int:
    return max(1, int(math.ceil(seconds * 20.0 - 1.0e-6)))


def add(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def scale_vec(a, s):
    return [a[0] * s, a[1] * s, a[2] * s]


def vary(tech_id: str, base, amount=1.0):
    """Deterministic per-technique bone angle jitter so every clip is unique."""
    s = salt(tech_id)
    out = []
    for i, x in enumerate(base):
        sign = 1 if ((s >> (i * 3)) & 1) == 0 else -1
        mag = ((s >> (i * 5)) & 7) / 7.0
        out.append(round(x + sign * mag * amount, 2))
    return out


# --- unique full-body XYZ families (hip Y capped; striking limb Z overshoot OK) ---

def beam_charge(tech_id: str):
    lean = vary(tech_id, [14, -18, -4], 6)
    arms_r = vary(tech_id, [-30, -36, 32], 10)
    arms_l = vary(tech_id, [-48, -28, -28], 10)
    return clip(1.5, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: vary(tech_id, [0, -10, 0], 4), 1.4: vary(tech_id, [0, -10, 0], 4)}},
        "waist": {"rotation": {0.0: [8, 0, 0], 0.2: lean, 1.4: lean}},
        "head": {"rotation": {0.0: [-6, 0, 0], 0.2: vary(tech_id, [-8, 22, 0], 6), 1.4: vary(tech_id, [-8, 22, 0], 6)}},
        "right_arm": {
            "rotation": {0.0: [-40, -12, 22], 0.2: arms_r, 1.4: arms_r},
            "position": {0.0: [0, 0, 0], 0.2: [0.45, 0.3, 1.1], 1.4: [0.45, 0.3, 1.1]},
        },
        "left_arm": {
            "rotation": {0.0: [-40, 12, -22], 0.2: arms_l, 1.4: arms_l},
            "position": {0.0: [0, 0, 0], 0.2: [-0.4, 0.25, 1.0], 1.4: [-0.4, 0.25, 1.0]},
        },
        "right_leg": {"rotation": {0.0: [-14, -8, 5], 0.2: vary(tech_id, [-22, -12, 6], 5), 1.4: vary(tech_id, [-22, -12, 6], 5)}},
        "left_leg": {"rotation": {0.0: [16, 8, -5], 0.2: vary(tech_id, [24, 12, -6], 5), 1.4: vary(tech_id, [24, 12, -6], 5)}},
    }, loop="hold_on_last_frame")


def beam_release(tech_id: str):
    # Arms overshoot forward on Z (out of body); Y stays shoulder-safe.
    push = vary(tech_id, [-110, -4, 6], 12)
    push_l = vary(tech_id, [-110, 4, -6], 12)
    return clip(1.05, {
        "root": {"rotation": {0.0: vary(tech_id, [0, -10, 0], 3), 0.12: [0, 0, 0], 1.05: [0, 0, 0]}},
        "waist": {"rotation": {0.0: vary(tech_id, [14, -18, -4], 5), 0.12: [12, 4, 0], 1.05: [8, 0, 0]}},
        "head": {"rotation": {0.0: vary(tech_id, [-8, 22, 0], 5), 0.12: [-10, -4, 0], 1.05: [-6, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: vary(tech_id, [-30, -36, 32], 8), 0.12: push, 0.9: push, 1.05: [-40, -12, 22]},
            "position": {0.0: [0.45, 0.3, 1.1], 0.12: [-0.3, 0.55, -4.2], 0.9: [-0.3, 0.55, -4.2], 1.05: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: vary(tech_id, [-48, -28, -28], 8), 0.12: push_l, 0.9: push_l, 1.05: [-40, 12, -22]},
            "position": {0.0: [-0.4, 0.25, 1.0], 0.12: [0.3, 0.55, -4.0], 0.9: [0.3, 0.55, -4.0], 1.05: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [-22, -12, 6], 0.12: [-28, -14, 8], 1.05: [-14, -8, 5]}},
        "left_leg": {"rotation": {0.0: [24, 12, -6], 0.12: [22, 14, -8], 1.05: [16, 8, -5]}},
    })


def melee_combo(tech_id: str):
    punch = vary(tech_id, [-125, 8, 6], 14)
    return clip(1.1, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.15: vary(tech_id, [8, -16, -6], 4), 0.35: vary(tech_id, [-12, 20, 8], 5), 1.1: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.35: [0, 0.05, -0.45], 1.1: [0, 0, 0]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.15: vary(tech_id, [12, -24, -8], 6), 0.35: vary(tech_id, [-16, 28, 10], 6), 1.1: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.35: [6, -10, 0], 1.1: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 30], 0.15: [-40, -40, 48], 0.35: punch, 0.7: [-95, 4, 6], 1.1: [-55, -10, 30]},
            "position": {0.0: [0, 0, 0], 0.15: [0.7, 0.35, 1.5], 0.35: [-0.4, 0.5, -4.0], 0.7: [-0.25, 0.35, -2.5], 1.1: [0, 0, 0]},
        },
        "left_arm": {"rotation": {0.0: [-70, 15, -35], 0.35: [-55, 22, -30], 1.1: [-70, 15, -35]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [-28, 14, 6], 1.1: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.35: [18, 10, -5], 1.1: [0, 0, 0]}},
    })


def kick_strike(tech_id: str):
    kick = vary(tech_id, [-130, 28, 16], 12)
    return clip(1.15, {
        "root": {
            "rotation": {0.0: [0, 0, 0], 0.18: vary(tech_id, [14, -14, -8], 4), 0.4: vary(tech_id, [-18, 22, 10], 5), 1.15: [0, 0, 0]},
            "position": {0.0: [0, 0, 0], 0.4: [0, 0.08, -0.5], 1.15: [0, 0, 0]},
        },
        "waist": {"rotation": {0.0: [0, 0, 0], 0.18: vary(tech_id, [-10, -24, 10], 5), 0.4: vary(tech_id, [-24, 28, 12], 6), 1.15: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.4: [-12, -10, 4], 1.15: [0, 0, 0]}},
        "right_leg": {
            "rotation": {0.0: [0, 0, 0], 0.18: [-85, -22, 14], 0.4: kick, 0.75: [-80, 18, 10], 1.15: [0, 0, 0]},
            # Hip-safe Y; Z overshoot out of body allowed.
            "position": {0.0: [0, 0, 0], 0.18: [0.25, 0.3, 0.8], 0.4: [-0.2, 0.35, -3.6], 0.75: [-0.1, 0.2, -2.0], 1.15: [0, 0, 0]},
        },
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.4: [-30, 28, 10], 1.15: [0, 0, 0]},
                     "position": {0.0: [0, 0, 0], 0.4: [0.1, -0.08, 0.35], 1.15: [0, 0, 0]}},
        "right_arm": {"rotation": {0.0: [-55, -12, 28], 0.4: [-58, -20, 40], 1.15: [-55, -12, 28]}},
        "left_arm": {"rotation": {0.0: [-70, 14, -32], 0.4: [-95, 20, -38], 1.15: [-70, 14, -32]}},
    })


def hold_flash(tech_id: str):
    up = vary(tech_id, [-160, 8, 10], 10)
    up_l = vary(tech_id, [-160, -8, -10], 10)
    return clip(1.0, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.2: [6, 0, 0], 1.0: [6, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.2: [8, 0, 0], 1.0: [8, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.2: [-18, 0, 0], 1.0: [-18, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-55, -10, 28], 0.25: up, 1.0: up},
            "position": {0.0: [0, 0, 0], 0.25: [0.2, 0.9, 0.4], 1.0: [0.2, 0.9, 0.4]},
        },
        "left_arm": {
            "rotation": {0.0: [-55, 10, -28], 0.25: up_l, 1.0: up_l},
            "position": {0.0: [0, 0, 0], 0.25: [-0.2, 0.9, 0.4], 1.0: [-0.2, 0.9, 0.4]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.25: [-10, -6, 4], 1.0: [-10, -6, 4]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.25: [12, 6, -4], 1.0: [12, 6, -4]}},
    }, loop="hold_on_last_frame")


def radial_burst(tech_id: str):
    return clip(1.0, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.25: [8, 0, 0], 0.45: [-8, 0, 0], 1.0: [0, 0, 0]},
                 "scale": {0.0: [1, 1, 1], 0.45: [1.06, 1.06, 1.06], 1.0: [1, 1, 1]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.25: [10, 0, 0], 0.45: [-6, 0, 0], 1.0: [0, 0, 0]}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.45: [-6, 0, 0], 1.0: [0, 0, 0]}},
        "right_arm": {
            "rotation": {0.0: [-50, -12, 22], 0.25: [-30, -28, 40], 0.45: vary(tech_id, [-80, 22, 55], 8), 1.0: [-50, -12, 22]},
            "position": {0.0: [0, 0, 0], 0.45: [1.6, 0.8, -1.0], 1.0: [0, 0, 0]},
        },
        "left_arm": {
            "rotation": {0.0: [-50, 12, -22], 0.25: [-30, 28, -40], 0.45: vary(tech_id, [-80, -22, -55], 8), 1.0: [-50, 12, -22]},
            "position": {0.0: [0, 0, 0], 0.45: [-1.6, 0.8, -1.0], 1.0: [0, 0, 0]},
        },
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [-16, -10, 6], 1.0: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.45: [-16, 10, -6], 1.0: [0, 0, 0]}},
    })


def ensure_duration(duration: int, clips: dict, beats: list) -> int:
    """Grow END so every POSE payload fits its animation_length in ticks."""
    need = duration
    for b in beats:
        if b["kind"] != "POSE":
            continue
        name = b.get("payload") or ""
        anim = clips.get(name)
        if not anim:
            continue
        clip_ticks = ticks(anim["animation_length"])
        need = max(need, b["tick"] + clip_ticks)
    if need > duration:
        # Push END beat.
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = need
        # Stretch last camera shot if present — caller adjusts camera separately.
        duration = need
    return duration


def timelines(typ: str, tech_id: str, clip_base: str):
    charge = f"{clip_base}_c"
    release = f"{clip_base}_r"
    pose = f"{clip_base}_p"
    assert len(charge) <= 64 and len(release) <= 64 and len(pose) <= 64

    if typ in ("beam", "laser", "disc", "ball", "giant_ball", "volley"):
        charge_clip = beam_charge(tech_id)
        release_clip = beam_release(tech_id)
        clips = {charge: charge_clip, release: release_clip}
        # Laser was too short vs 1.5s charge — floor by clip coverage.
        if typ == "laser":
            duration = 56
            hold_at = 28
            release_at = 32
        elif typ == "volley":
            duration = 96
            hold_at = 40
            release_at = 44
        elif typ == "giant_ball":
            duration = 80
            hold_at = 36
            release_at = 40
        else:
            duration = 72
            hold_at = 32
            release_at = 36
        beats = [
            beat("POSE", 0, charge),
            beat("KI_CHARGE", 3),
            beat("KI_HOLD", hold_at),
            beat("POSE", hold_at, release),
            beat("KI_RELEASE", release_at),
            beat("END", duration),
        ]
        duration = ensure_duration(duration, clips, beats)
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = duration
        mid = max(24, duration // 2)
        cam = [
            shot(0, mid, [2.8, 1.1, -4.4], 0.25, "CUT"),
            shot(mid, duration - mid, [3.2, 1.3, -5.0], 0.4),
        ]
        return duration, clips, beats, cam

    if typ == "hold":
        clips = {pose: hold_flash(tech_id)}
        beats = [
            beat("APPROACH", 0, duration=6),
            beat("POSE", 2, pose),
            beat("HOLD_TARGET", 10, duration=36),
            beat("END", 48),
        ]
        duration = ensure_duration(48, clips, beats)
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = duration
        cam = [shot(0, 12, [2.0, 1.3, -3.2], 0.2, "CUT"), shot(12, duration - 12, [4.5, 1.8, -5.0], 0.5)]
        return duration, clips, beats, cam

    if typ == "radial":
        clips = {pose: radial_burst(tech_id)}
        beats = [
            beat("APPROACH", 0, duration=6),
            beat("POSE", 2, pose),
            beat("RADIAL", 16, "10", value=1.5),
            beat("END", 44),
        ]
        duration = ensure_duration(44, clips, beats)
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = duration
        cam = [shot(0, 12, [1.8, 1.4, -2.8], 0.15, "CUT"), shot(12, duration - 12, [5.5, 2.2, -6.0], 0.55)]
        return duration, clips, beats, cam

    if typ == "melee_energy":
        clips = {pose: melee_combo(tech_id), release: beam_release(tech_id)}
        beats = [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, pose),
            beat("STRIKE", 12, value=0.6),
            beat("STRIKE", 18, value=0.85),
            beat("SHOVE", 20, "FORWARD"),
            beat("KI_CHARGE", 22),
            beat("POSE", 30, release),
            beat("KI_RELEASE", 34),
            beat("END", 64),
        ]
        duration = ensure_duration(64, clips, beats)
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = duration
        cam = [shot(0, 14, [1.5, 1.3, -2.5], 0.15, "CUT"), shot(14, duration - 14, [5.0, 2.0, -5.5], 0.5)]
        return duration, clips, beats, cam

    if typ == "grab":
        clips = {pose: melee_combo(tech_id)}
        beats = [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, pose),
            beat("HOLD_TARGET", 12, duration=16),
            beat("STRIKE", 26, value=1.4),
            beat("SHOVE", 26, "FORWARD"),
            beat("END", 48),
        ]
        duration = ensure_duration(48, clips, beats)
        for b in beats:
            if b["kind"] == "END":
                b["tick"] = duration
        cam = [shot(0, 12, [1.5, 1.3, -2.5], 0.15, "CUT"), shot(12, duration - 12, [5.0, 2.0, -5.5], 0.5)]
        return duration, clips, beats, cam

    # melee — alternate punch/kick by salt
    use_kick = (salt(tech_id) % 2) == 1
    clips = {pose: kick_strike(tech_id) if use_kick else melee_combo(tech_id)}
    beats = [
        beat("APPROACH", 0, duration=8),
        beat("POSE", 2, pose),
        beat("STRIKE", 12, value=0.7),
        beat("STRIKE", 18, value=0.95),
        beat("STRIKE", 24, value=1.35),
        beat("SHOVE", 24, "FORWARD"),
        beat("END", 48),
    ]
    duration = ensure_duration(48, clips, beats)
    for b in beats:
        if b["kind"] == "END":
            b["tick"] = duration
    cam = [shot(0, 12, [1.5, 1.4, -2.5], 0.15, "CUT"), shot(12, duration - 12, [5.2, 2.1, -5.8], 0.55)]
    return duration, clips, beats, cam


def extract_frames(techs, mp4: Path):
    FRAMES.mkdir(parents=True, exist_ok=True)
    done = 0
    skipped = 0
    for tech in techs:
        starts = tech.get("sourceStartsMs") or [0]
        sec = float(starts[0]) / 1000.0
        stem = f"t{int(starts[0]):06d}"
        for label, offset in (("", 0.0), ("_p35", 0.35), ("_p70", 0.70)):
            out = FRAMES / f"frame_{stem}{label}.jpg"
            if out.exists() and out.stat().st_size > 1000:
                skipped += 1
                continue
            ss = max(0.0, sec + offset)
            cmd = [
                "ffmpeg", "-y", "-ss", f"{ss:.3f}", "-i", str(mp4),
                "-frames:v", "1", "-q:v", "2", str(out),
            ]
            subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            done += 1
            if done % 50 == 0:
                print(f"extracted {done} (skipped existing {skipped})", flush=True)
    print(f"frame extract done new={done} skipped={skipped} dir={FRAMES}")


def write_review_index(techs):
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Video pipeline 302 — chunked frame review",
        "",
        "Status of authored clips: `reference_timed_unverified`.",
        "Promote to `reference_compared` only after in-game accept per technique.",
        "",
        "Local MP4 only. Frames: `docs/combat-v3/video-pipeline-302/frames/`.",
        "",
        "| # | id | name | type | start_s | frames |",
        "|---|----|------|------|---------|--------|",
    ]
    for i, tech in enumerate(techs, 1):
        starts = tech.get("sourceStartsMs") or [0]
        sec = float(starts[0]) / 1000.0
        stem = f"t{int(starts[0]):06d}"
        frames = ", ".join(
            f"`frame_{stem}{sfx}.jpg`" for sfx in ("", "_p35", "_p70")
        )
        lines.append(
            f"| {i} | `{tech['id']}` | {tech.get('name','')} | {tech.get('type','')} | {sec:.3f} | {frames} |"
        )
        if i % 30 == 0:
            lines.append("")
            lines.append(f"## Chunk review checkpoint after #{i}")
            lines.append("")
    REVIEW_INDEX.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {REVIEW_INDEX}")


def main():
    do_extract = "--extract" in sys.argv
    force = "--force" in sys.argv
    mp4 = Path(os.environ.get("BT3_PART1_MP4", MP4_DEFAULT))
    tech_data = json.loads(TECH.read_text(encoding="utf-8"))
    techs = tech_data["techniques"]
    resource = (
        json.loads(RESOURCE.read_text(encoding="utf-8"))
        if RESOURCE.exists()
        else {"format_version": "1.8.0", "animations": {}}
    )
    authored = (
        json.loads(OVERRIDES.read_text(encoding="utf-8"))
        if OVERRIDES.exists()
        else {"schema": 1, "entries": []}
    )
    by_id = {e["id"]: e for e in authored.get("entries", [])}

    written = 0
    preserved = 0
    for tech in techs:
        tid = tech["id"]
        existing = by_id.get(tid)
        # Preserve only hand batch1/2 evidence entries unless --force rewrites everything.
        if (
            not force
            and existing
            and existing.get("animationStatus") == "reference_timed_unverified"
            and any(
                str(x).startswith("docs/combat-v3/video-batch")
                for x in (existing.get("evidence") or [])
            )
        ):
            poses = [b.get("payload") for b in existing.get("beats", []) if b.get("kind") == "POSE"]
            if poses and all(
                (not p) or p in resource.get("animations", {}) for p in poses
            ):
                dur = int(existing.get("durationTicks") or 0)
                need = dur
                for b in existing.get("beats", []):
                    if b.get("kind") != "POSE":
                        continue
                    anim = resource["animations"].get(b.get("payload") or "")
                    if anim:
                        need = max(need, int(b.get("tick") or 0) + ticks(anim["animation_length"]))
                if need > dur:
                    existing["durationTicks"] = need
                    for b in existing.get("beats", []):
                        if b.get("kind") == "END":
                            b["tick"] = need
                preserved += 1
                continue

        base = PREFIX + slug(tid)
        duration, clips, beats, camera = timelines(tech.get("type") or "melee", tid, base)
        for name, anim in clips.items():
            if len(name) > 64:
                raise SystemExit(f"clip name too long: {name} ({len(name)})")
            resource.setdefault("animations", {})[name] = anim
        by_id[tid] = {
            "id": tid,
            "sourceStartsMs": list(tech.get("sourceStartsMs") or [0]),
            "animationStatus": "reference_timed_unverified",
            "durationTicks": duration,
            "beats": beats,
            "camera": camera,
            "evidence": [f"docs/combat-v3/video-pipeline-302/frames/frame_t{int((tech.get('sourceStartsMs') or [0])[0]):06d}s.jpg"],
            "unverified": [
                "Full-302 pipeline: unique full-body XYZ per occurrence (author_v3_video_pipeline_302.py).",
                "Frames extracted from local Part 1 MP4 for chunked review; not yet in-game accepted.",
                "Opening cinematic length also tunable via v3.strikeCinematicCameraHoldTicks.",
            ],
        }
        written += 1

    # Drop orphan long-name all_* clips from the failed pre-hash pass when unused.
    used = set()
    for e in by_id.values():
        for b in e.get("beats", []):
            if b.get("kind") == "POSE" and b.get("payload"):
                used.add(b["payload"])
    anims = resource.setdefault("animations", {})
    removed = 0
    for name in list(anims.keys()):
        if name.startswith(PREFIX + "all_") and name not in used:
            del anims[name]
            removed += 1

    RESOURCE.write_text(json.dumps(resource, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    authored["schema"] = 1
    authored["entries"] = sorted(by_id.values(), key=lambda e: e["id"])
    OVERRIDES.write_text(json.dumps(authored, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_review_index(techs)
    print(
        f"written={written} preserved={preserved} entries={len(authored['entries'])} "
        f"anims={len(anims)} removed_orphan_all={removed}"
    )

    if do_extract:
        if not mp4.exists():
            raise SystemExit(f"MP4 missing: {mp4}")
        extract_frames(techs, mp4)


if __name__ == "__main__":
    main()
