"""Generate the flight controller GeckoLib geometry.

Writes ``assets/xenopixelsmod/geo/flight_controller.geo.json``.

Geometry is generated rather than hand-authored because the detail that makes the block read
as a machine — chamfers, louvre slats, conduit greebles — is repetitive cube maths: tedious by
hand, three lines in a loop.

Run: ``python tools/gen_flight_controller_model.py``
"""
from __future__ import annotations

import json
from pathlib import Path

from flight_controller_layout import SIZE, box_uv

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/xenopixelsmod/geo/flight_controller.geo.json"
ANIM = ROOT / "src/main/resources/assets/xenopixelsmod/animations/flight_controller.animation.json"
HITBOX_OUT = ROOT / "src/main/resources/data/xenopixelsmod/aero/flight_controller_hitboxes.json"

# Interactive controls: bone name -> action id consumed by AeroHitRegions on the Java side.
# Click regions are derived from these bones' own cubes, so there are no hand-tuned
# percentages anywhere — moving a button in the generator moves its hitbox with it.
INTERACTIVE = {
    "button_mode": "cycle_mode",
    "button_engage": "toggle_flight",
    "button_map": "toggle_map",
    "lever_throttle": "step_throttle",
    "lever_attitude": "reset_attitude",
}

# Clicking a 3x0.8 button is fiddly, so regions are inflated slightly. One number, stated
# once, rather than magic offsets scattered through the hit-test.
CLICK_PADDING = 0.75

# Bones the animation JSON and the Phase 5 hit-testing depend on. Renaming any of these
# silently breaks animation resolution, so the script asserts on them before writing.
REQUIRED_BONES = {
    "housing", "deck", "console", "screen",
    "lever_throttle", "lever_attitude",
    "button_mode", "button_engage", "button_map",
}


def cube(origin, size, region):
    return {"origin": list(origin), "size": list(size), "uv": box_uv(region, tuple(size))}


def bone(name, pivot, parent=None, rotation=None, cubes=None):
    data = {"name": name, "pivot": list(pivot)}
    if parent:
        data["parent"] = parent
    if rotation:
        data["rotation"] = list(rotation)
    if cubes:
        data["cubes"] = cubes
    return data


def housing_cubes():
    """Base block with a chamfered top edge so the silhouette catches light."""
    cubes = [cube((-8, 0, -8), (16, 3, 16), "housing")]
    # Chamfer ring: a slightly inset band above the base, reading as a bevel.
    cubes.append(cube((-7.5, 3, -7.5), (15, 1, 15), "trim"))
    return cubes


def vent_cubes():
    """Louvre slats down both flanks."""
    cubes = []
    for i in range(4):
        z = -5.5 + i * 3.0
        cubes.append(cube((-8.05, 0.75, z), (0.4, 1.6, 2.0), "vent"))
        cubes.append(cube((7.65, 0.75, z), (0.4, 1.6, 2.0), "vent"))
    return cubes


def conduit_cubes():
    """Cable runs along the rear face."""
    cubes = []
    for i in range(3):
        x = -4.5 + i * 4.5
        cubes.append(cube((x, 0.5, 7.6), (1.2, 3.0, 0.6), "conduit"))
    return cubes


def console_cubes():
    """Angled panel plus a bezel frame; the screen sits recessed inside it."""
    cubes = [cube((-7, 5, 4), (14, 8, 2), "console")]
    # Bezel: four thin strips framing the screen recess.
    cubes.append(cube((-6.5, 5.5, 3.4), (13, 0.6, 0.6), "trim"))
    cubes.append(cube((-6.5, 12, 3.4), (13, 0.6, 0.6), "trim"))
    cubes.append(cube((-6.9, 5.5, 3.4), (0.6, 7, 0.6), "trim"))
    cubes.append(cube((6.3, 5.5, 3.4), (0.6, 7, 0.6), "trim"))
    return cubes


def build():
    bones = [
        bone("root", (0, 0, 0)),
        bone("housing", (0, 0, 0), "root", cubes=housing_cubes() + vent_cubes() + conduit_cubes()),
        bone("deck", (0, 4, 0), "housing", cubes=[cube((-7, 4, -7), (14, 1, 14), "deck")]),
        bone("console", (0, 5, 5), "deck", rotation=(-20, 0, 0), cubes=console_cubes()),
        # Recessed 0.5px behind the bezel so the emissive glow has an edge to sit behind.
        bone("screen", (0, 9, 4), "console", cubes=[cube((-6, 6, 3.9), (12, 6, 0.4), "screen")]),
        bone("lever_throttle", (-4, 5, -2), "deck", cubes=[
            cube((-4.4, 5, -2.4), (0.8, 4.5, 0.8), "lever"),
            cube((-4.8, 9.5, -2.8), (1.6, 1.0, 1.6), "lever"),
        ]),
        bone("lever_attitude", (4, 5, -2), "deck", cubes=[
            cube((3.6, 5, -2.4), (0.8, 4.5, 0.8), "lever"),
            cube((3.2, 9.5, -2.8), (1.6, 1.0, 1.6), "lever"),
        ]),
        bone("button_mode", (-3.5, 5, -4.5), "deck",
             cubes=[cube((-5, 5, -6), (3, 0.8, 3), "btn_mode")]),
        bone("button_engage", (0.5, 5, -4.5), "deck",
             cubes=[cube((-1, 5, -6), (3, 0.8, 3), "btn_engage")]),
        bone("button_map", (4.5, 5, -4.5), "deck",
             cubes=[cube((3, 5, -6), (3, 0.8, 3), "btn_map")]),
    ]

    names = {b["name"] for b in bones}
    missing = REQUIRED_BONES - names
    if missing:
        raise SystemExit(f"generator would drop required bones: {sorted(missing)}")

    # Cross-check against what the animation file actually references, so a rename in either
    # file is caught here rather than as a silently missing animation in-game.
    if ANIM.exists():
        anim = json.loads(ANIM.read_text(encoding="utf-8"))
        referenced = set()
        for clip in anim.get("animations", {}).values():
            referenced.update(clip.get("bones", {}).keys())
        unknown = referenced - names
        if unknown:
            raise SystemExit(f"animation references bones this model does not define: {sorted(unknown)}")

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.flight_controller",
                    "texture_width": SIZE,
                    "texture_height": SIZE,
                    "visible_bounds_width": 2,
                    "visible_bounds_height": 2.5,
                    "visible_bounds_offset": [0, 0.75, 0],
                },
                "bones": bones,
            }
        ],
    }


def hitboxes(bones):
    """Derive click regions from the interactive bones' own cubes.

    Model space spans x[-8..8], y[0..16], z[-8..8]; block-local space is [0..1] with the
    origin at the block corner, which is what BlockHitResult gives us. Converting here means
    the Java side does no coordinate maths and carries no constants.

    Only bones parented to the un-rotated ``deck`` are eligible, because a rotated parent
    (the console) would need its transform applied and an axis-aligned box would no longer
    describe it honestly.
    """
    by_name = {b["name"]: b for b in bones}
    regions = []
    for name, action in sorted(INTERACTIVE.items()):
        b = by_name.get(name)
        if b is None:
            raise SystemExit(f"interactive bone missing from model: {name}")
        if b.get("parent") != "deck":
            raise SystemExit(
                f"{name} is parented to {b.get('parent')!r}; axis-aligned hit regions are only "
                "valid under the un-rotated 'deck' bone"
            )
        cubes = b.get("cubes") or []
        if not cubes:
            raise SystemExit(f"interactive bone has no cubes to derive a region from: {name}")

        lo = [float("inf")] * 3
        hi = [float("-inf")] * 3
        for c in cubes:
            o, s = c["origin"], c["size"]
            for i in range(3):
                lo[i] = min(lo[i], o[i])
                hi[i] = max(hi[i], o[i] + s[i])
        lo = [v - CLICK_PADDING for v in lo]
        hi = [v + CLICK_PADDING for v in hi]

        def to_local(v, axis):
            # x and z are centred on the block; y already starts at the block floor.
            return (v + 8.0) / 16.0 if axis in (0, 2) else v / 16.0

        regions.append({
            "name": name,
            "action": action,
            "min": [round(to_local(lo[i], i), 5) for i in range(3)],
            "max": [round(to_local(hi[i], i), 5) for i in range(3)],
        })
    return {"regions": regions}


def main():
    model = build()
    bones = model["minecraft:geometry"][0]["bones"]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    cubes = sum(len(b.get("cubes", [])) for b in bones)
    print(f"wrote {OUT} ({len(bones)} bones, {cubes} cubes)")

    boxes = hitboxes(bones)
    HITBOX_OUT.parent.mkdir(parents=True, exist_ok=True)
    HITBOX_OUT.write_text(json.dumps(boxes, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {HITBOX_OUT} ({len(boxes['regions'])} click regions)")


if __name__ == "__main__":
    main()
