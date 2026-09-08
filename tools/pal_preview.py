"""Render a PlayerAnimationLibrary clip to PNG contact sheets.

Why this exists
---------------
PAL animations are hand-edited JSON keyframes. Nothing in the build pipeline shows what they
actually look like, so a bad rotation sign or a limp timing curve is only discovered by launching
the game and watching. This renders the same skeleton PAL poses, from the same JSON, so a clip can
be inspected and iterated on without a client.

It is a *preview*, not a pixel-exact reproduction of the game renderer: no texture, no shading, no
bend channel, and item/elytra bones are ignored.

Conventions, all verified against the resolved PAL 1.1.6+mc.1.21.1 jar rather than assumed:

* Times in the JSON are seconds; PAL multiplies by 20 for ticks (``AnimationLoader``).
* Rotation values are degrees and become ``ModelPart.xRot/yRot/zRot`` (``RenderUtil``).
* Those combine as ``rotateZYX(z, y, x)``, i.e. Rz then Ry then Rx (``RenderUtil``).
* Bone pivots are ``PlayerAnimationController.BONE_POSITIONS``, in a Y-up space where the head
  pivot sits at y=24 and the hips at y=12.
* ``registerBones``/``registerTopPlayerAnimBone`` make head, arms and cape follow ``torso``, while
  ``torso`` and the legs follow ``body`` - which is the hierarchy applied here.

Usage
-----
    python tools/pal_preview.py <animation.json> [--name CLIP] [--frames 8] [--out DIR]

Writes ``<out>/<clip>.png``: one row per camera (front-three-quarter and side), one column per
sampled frame, labelled with the time in seconds.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

# --- Player model -----------------------------------------------------------------------------
# Pivots from PlayerAnimationController.BONE_POSITIONS. Boxes are the standard player model in the
# same Y-up space: (min_xyz, max_xyz), used only to draw something recognisable.
PIVOTS = {
    "body": (0.0, 12.0, 0.0),
    "torso": (0.0, 24.0, 0.0),
    "head": (0.0, 24.0, 0.0),
    "right_arm": (5.0, 22.0, 0.0),
    "left_arm": (-5.0, 22.0, 0.0),
    "right_leg": (2.0, 12.0, 0.0),
    "left_leg": (-2.0, 12.0, 0.0),
}

# DragonMineZ's GeckoLib rig names the same joints differently. Aliasing them here means one tool
# previews both a PAL clip and a DMZ combat.animation.json clip, which matters because DMZ cancels
# the vanilla player render outright - so animations that have to be *seen* in this modpack are
# authored against the DMZ rig, not PAL's.
ALIASES = {"root": "body", "waist": "torso"}

# MoLang expressions seen while parsing, reported once so it is clear why an axis looks static.
MOLANG_AXES: set[str] = set()

# Parent of each bone. body is the root; head/arms ride the torso (PAL's "top bones").
PARENTS = {
    "body": None,
    "torso": "body",
    "head": "torso",
    "right_arm": "torso",
    "left_arm": "torso",
    "right_leg": "body",
    "left_leg": "body",
}

# The box each bone draws, and its colour. torso draws the chest; body draws nothing itself.
BOXES = {
    "head": ((-4, 24, -4), (4, 32, 4), (232, 190, 150)),
    "torso": ((-4, 12, -2), (4, 24, 2), (110, 160, 220)),
    "right_arm": ((4, 12, -2), (8, 24, 2), (236, 176, 130)),
    "left_arm": ((-8, 12, -2), (-4, 24, 2), (206, 146, 100)),
    "right_leg": ((0, 0, -2), (4, 12, 2), (80, 90, 170)),
    "left_leg": ((-4, 0, -2), (0, 12, 2), (60, 70, 150)),
}

CUBE_FACES = [
    (0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1),
    (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3),
]

# A "nose" on the head so which way the fighter faces is never in doubt. The player model faces
# +Z: vanilla sets rightArm.xRot = -PI/2 to aim a bow straight at the target, and head.xRot =
# headPitch with positive meaning looking down - both are only true with the face on +Z. Reading a
# punch as backwards because the preview had no facing cue is exactly the mistake this prevents.
NOSE = ((-1.0, 26.0, 4.0), (1.0, 28.0, 5.0), (200, 60, 60))


def corners(lo, hi):
    return np.array([[x, y, z] for x in (lo[0], hi[0])
                     for y in (lo[1], hi[1])
                     for z in (lo[2], hi[2])], dtype=float)


# --- Animation sampling -----------------------------------------------------------------------
def channel_at(channel: dict[float, list[float]], t: float) -> np.ndarray:
    """Linearly interpolate a keyframe channel at time ``t`` (seconds).

    Linear is PAL's default easing (``EasingType.LINEAR`` in ``AnimationLoader``); custom easings
    in a clip are previewed as linear, which shifts the in-between frames but not the key poses.
    """
    if not channel:
        return np.zeros(3)
    times = sorted(channel)
    if t <= times[0]:
        return np.array(channel[times[0]], dtype=float)
    if t >= times[-1]:
        return np.array(channel[times[-1]], dtype=float)
    for a, b in zip(times, times[1:]):
        if a <= t <= b:
            span = b - a
            f = 0.0 if span == 0 else (t - a) / span
            va = np.array(channel[a], dtype=float)
            vb = np.array(channel[b], dtype=float)
            return va + (vb - va) * f
    return np.array(channel[times[-1]], dtype=float)


def parse_channel(raw) -> dict[float, list[float]]:
    """Accept both ``{"0.1": [x,y,z]}`` and the Bedrock ``{"0.1": {"vector": [...]}}`` shapes."""
    out = {}
    if not isinstance(raw, dict):
        return out
    for key, value in raw.items():
        try:
            time = float(key)
        except ValueError:
            continue
        if isinstance(value, dict):
            value = value.get("vector") or value.get("post") or value.get("pre")
        if isinstance(value, list) and len(value) >= 3:
            # Values may be MoLang expressions rather than numbers - DragonMineZ uses things like
            # "-query.head_x_rotation*0.25" to make a bone track where the player is looking. There
            # is no MoLang engine here, so such an axis previews as 0: the authored motion still
            # shows, the runtime-driven part does not.
            axes = []
            for v in value[:3]:
                try:
                    axes.append(float(v))
                except (TypeError, ValueError):
                    axes.append(0.0)
                    MOLANG_AXES.add(str(v))
            out[time] = axes
    return out


def rot_zyx(deg: np.ndarray) -> np.ndarray:
    """Rz @ Ry @ Rx, matching RenderUtil.rotateZYX(rotZ, rotY, rotX)."""
    x, y, z = np.radians(deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def bone_world(bones: dict, t: float, name: str, cache: dict):
    """(rotation, translation) placing ``name``'s local space into model space at time ``t``."""
    if name in cache:
        return cache[name]
    parent = PARENTS.get(name)
    p_rot, p_trn = (np.eye(3), np.zeros(3)) if parent is None else bone_world(bones, t, parent, cache)

    data = bones.get(name, {})
    rot = rot_zyx(channel_at(data.get("rotation", {}), t))
    pos = channel_at(data.get("position", {}), t)
    pivot = np.array(PIVOTS[name])

    # Rotate about the pivot, then apply any positional offset, then the parent's transform.
    world_rot = p_rot @ rot
    world_trn = p_rot @ (pivot + pos - rot @ pivot) + p_trn
    cache[name] = (world_rot, world_trn)
    return cache[name]


# --- Drawing ----------------------------------------------------------------------------------
def project(points: np.ndarray, yaw: float, pitch: float, scale: float, size: int):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    v = points @ ry.T @ rx.T
    sx = size / 2 + v[:, 0] * scale
    sy_ = size * 0.92 - v[:, 1] * scale
    return np.stack([sx, sy_], axis=1), v[:, 2]


def render_frame(bones: dict, t: float, yaw: float, pitch: float, size: int) -> Image.Image:
    img = Image.new("RGB", (size, size), (250, 250, 252))
    draw = ImageDraw.Draw(img)
    draw.line([(0, size * 0.92), (size, size * 0.92)], fill=(215, 215, 225), width=1)

    cache: dict = {}
    faces = []
    parts = list(BOXES.items()) + [("head", NOSE[:2] + (NOSE[2],))]
    for name, (lo, hi, colour) in parts:
        rot, trn = bone_world(bones, t, name, cache)
        pts = corners(lo, hi) @ rot.T + trn
        flat, depth = project(pts, yaw, pitch, size / 52.0, size)
        for face in CUBE_FACES:
            faces.append((float(np.mean(depth[list(face)])),
                          [tuple(flat[i]) for i in face], colour))

    for _, poly, colour in sorted(faces, key=lambda f: f[0]):
        draw.polygon(poly, fill=colour, outline=(35, 35, 45))

    # Ground arrow pointing the way the fighter faces (+Z), so forward is readable at a glance.
    arrow = np.array([[0, 0.2, 6.0], [-2.0, 0.2, 2.0], [2.0, 0.2, 2.0]], dtype=float)
    flat, _ = project(arrow, yaw, pitch, size / 52.0, size)
    draw.polygon([tuple(p) for p in flat], fill=(215, 90, 90))
    return img


def build(anim: dict, name: str, frames: int, size: int) -> Image.Image:
    length = float(anim.get("animation_length", 0.5))
    bones = {ALIASES.get(b, b): {c: parse_channel(v) for c, v in ch.items()}
             for b, ch in anim.get("bones", {}).items()}
    unknown = sorted(set(bones) - set(PIVOTS))
    if unknown:
        print(f"  note: bones not previewed (no pivot known): {', '.join(unknown)}", file=sys.stderr)
    if MOLANG_AXES:
        shown = ", ".join(sorted(MOLANG_AXES)[:3])
        print(f"  note: {len(MOLANG_AXES)} MoLang axes previewed as 0 (e.g. {shown})", file=sys.stderr)
        MOLANG_AXES.clear()

    times = [length * i / max(1, frames - 1) for i in range(frames)]
    views = [("3/4", 34.0, 12.0), ("side", 88.0, 6.0)]

    sheet = Image.new("RGB", (size * frames, size * len(views) + 20), (255, 255, 255))
    label = ImageDraw.Draw(sheet)
    for row, (view_name, yaw, pitch) in enumerate(views):
        for col, t in enumerate(times):
            sheet.paste(render_frame(bones, t, yaw, pitch, size), (col * size, row * size + 20))
            if row == 0:
                label.text((col * size + 6, 6), f"{t:.2f}s", fill=(20, 20, 30))
        label.text((4, row * size + 24), view_name, fill=(120, 120, 130))
    label.text((size * frames - 120, 6), f"{name}  {length:.2f}s", fill=(20, 20, 30))
    return sheet


def main() -> int:
    ap = argparse.ArgumentParser(description="Render a PAL animation clip to a contact sheet.")
    ap.add_argument("source", type=Path, help="PAL animation .json")
    ap.add_argument("--name", help="clip name inside the file; default renders every clip")
    ap.add_argument("--frames", type=int, default=8)
    ap.add_argument("--size", type=int, default=180)
    ap.add_argument("--out", type=Path, default=Path("build/pal-preview"))
    args = ap.parse_args()

    data = json.loads(args.source.read_text(encoding="utf-8"))
    clips = data.get("animations", {"__root__": data})
    if args.name:
        if args.name not in clips:
            print(f"no clip '{args.name}' in {args.source}; have: {', '.join(clips)}", file=sys.stderr)
            return 1
        clips = {args.name: clips[args.name]}

    args.out.mkdir(parents=True, exist_ok=True)
    for name, anim in clips.items():
        sheet = build(anim, name, args.frames, args.size)
        path = args.out / f"{name}.png"
        sheet.save(path)
        print(f"{path}  ({sheet.width}x{sheet.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
