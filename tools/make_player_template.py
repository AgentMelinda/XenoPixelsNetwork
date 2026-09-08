"""Generate the Blockbench player template used to author XenoPixels PAL animations.

Why generate it rather than download one
----------------------------------------
PAL's docs mention a player ``.bbmodel`` template but no such file exists in any of the five
PlayerAnimationLibrary GitHub repositories. Rather than guess at a download URL, this builds one
from facts that were verified directly:

* The container schema is copied from the ``animated_entity_model`` template that the **GeckoLib
  Animation Utils** plugin itself embeds (``animation_utils.js``): ``meta.format_version`` 3.2,
  ``box_uv`` true, 64x64 resolution, and the element/outliner shapes used below.
* Bone pivots are ``PlayerAnimationController.BONE_POSITIONS`` from the resolved PAL jar.
* Bone names are PAL's registered bones. ``AnimationLoader.bakeBoneAnimations`` passes every bone
  key through ``UniversalAnimLoader.getCorrectPlayerBoneName``, which is only camelCase ->
  snake_case, so ``right_arm`` and ``rightArm`` both bind; snake_case is used here because it is
  identity-mapped and matches the clips already in the repository.
* The nesting mirrors what PAL does at runtime - ``registerTopPlayerAnimBone`` makes head, arms and
  cape follow ``torso``, and ``torso`` plus the legs follow ``body``. Nesting the groups the same
  way is what makes a rotation previewed in Blockbench match the game.

The cubes are locked reference geometry with ``export: false``: PAL animates the real player model
and never reads this file's geometry, so the boxes exist only to see what is being posed.

Usage
-----
    python tools/make_player_template.py [--out PATH] [--copy-to DIR ...]
"""

from __future__ import annotations

import argparse
import json
import uuid
from pathlib import Path

# (bone, pivot, parent) - pivots from PlayerAnimationController.BONE_POSITIONS.
BONES = [
    ("body", (0, 12, 0), None),
    ("torso", (0, 24, 0), "body"),
    ("head", (0, 24, 0), "torso"),
    ("right_arm", (5, 22, 0), "torso"),
    ("left_arm", (-5, 22, 0), "torso"),
    ("right_leg", (2, 12, 0), "body"),
    ("left_leg", (-2, 12, 0), "body"),
]

# Reference cubes: bone -> (from, to, uv_offset). UVs are the vanilla 64x64 player skin layout.
# `body` carries no cube of its own; it is the root that moves the whole fighter.
CUBES = {
    "head": ((-4, 24, -4), (4, 32, 4), (0, 0)),
    "torso": ((-4, 12, -2), (4, 24, 2), (16, 16)),
    "right_arm": ((4, 12, -2), (8, 24, 2), (40, 16)),
    "left_arm": ((-8, 12, -2), (-4, 24, 2), (32, 48)),
    "right_leg": ((0, 0, -2), (4, 12, 2), (0, 16)),
    "left_leg": ((-4, 0, -2), (0, 12, 2), (16, 48)),
}


def build(name: str) -> dict:
    elements = []
    groups: dict[str, dict] = {}

    for bone, pivot, _ in BONES:
        group = {
            "name": bone,
            "uuid": str(uuid.uuid4()),
            "export": True,
            "isOpen": True,
            "visibility": True,
            "autouv": 0,
            "origin": list(pivot),
            "children": [],
        }
        groups[bone] = group

        if bone in CUBES:
            lo, hi, uv = CUBES[bone]
            cube_uuid = str(uuid.uuid4())
            elements.append({
                # Named so it is obvious the geometry is scenery: PAL poses the real player model,
                # so editing these cubes changes nothing in game.
                "name": "reference_" + bone,
                "from": list(lo),
                "to": list(hi),
                "uv_offset": list(uv),
                "autouv": 0,
                "color": 0,
                "export": False,
                "locked": True,
                "origin": list(pivot),
                "uuid": cube_uuid,
            })
            group["children"].append(cube_uuid)

    # Nest the groups so Blockbench reproduces PAL's runtime hierarchy.
    outliner = []
    for bone, _, parent in BONES:
        if parent is None:
            outliner.append(groups[bone])
        else:
            groups[parent]["children"].append(groups[bone])

    return {
        "meta": {"format_version": "3.2", "model_format": "animated_entity_model", "box_uv": True},
        "name": name,
        "geo_name": name,
        "resolution": {"width": 64, "height": 64},
        "elements": elements,
        "outliner": outliner,
        "textures": [],
    }


def check(model: dict) -> list[str]:
    """Structural sanity checks, so a broken file is caught here and not in Blockbench."""
    problems = []
    if model["meta"]["model_format"] != "animated_entity_model":
        problems.append("model_format must be animated_entity_model for GeckoLib Animation Utils")

    declared = {e["uuid"] for e in model["elements"]}
    seen_groups: set[str] = set()
    referenced: set[str] = set()

    def walk(node):
        seen_groups.add(node["name"])
        for child in node["children"]:
            if isinstance(child, str):
                referenced.add(child)
            else:
                walk(child)

    for root in model["outliner"]:
        walk(root)

    expected = {b for b, _, _ in BONES}
    if seen_groups != expected:
        problems.append("bone set mismatch: %s" % sorted(seen_groups ^ expected))
    if referenced != declared:
        problems.append("cube references do not match declared elements")
    for element in model["elements"]:
        if "uv_offset" not in element and model["meta"]["box_uv"]:
            problems.append("box_uv model needs uv_offset on %s" % element["name"])
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate the XenoPixels PAL player template.")
    ap.add_argument("--name", default="XenoPixelsPlayer")
    ap.add_argument("--out", type=Path,
                    default=Path("tools/xenopixels_player_template.bbmodel"))
    ap.add_argument("--copy-to", type=Path, nargs="*", default=[],
                    help="extra directories to drop a copy into")
    args = ap.parse_args()

    model = build(args.name)
    problems = check(model)
    if problems:
        for p in problems:
            print("PROBLEM: " + p)
        return 1

    text = json.dumps(model, indent=2)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(text, encoding="utf-8")
    print("%s  (%d bones, %d reference cubes)" % (args.out, len(BONES), len(model["elements"])))

    for directory in args.copy_to:
        directory.mkdir(parents=True, exist_ok=True)
        target = directory / args.out.name
        target.write_text(text, encoding="utf-8")
        print("%s" % target)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
