#!/usr/bin/env python3
"""Generate theme-aware DMZ lock-on, radar, and scouter textures from approved drop-ins."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys

from PIL import Image


ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/source/dmz_our_style/dragonminez/textures/gui"
OUTPUT = ROOT / "src/main/resources/assets/xenopixelsmod/textures/gui/dmz_theme"
MANIFEST = ROOT / "tools/generated/dmz_hud_theme_manifest.json"
FILES = {
    "lock_on.png": ((256, 256), SOURCE / "lock_on.png"),
    "radar.png": ((256, 256), SOURCE / "radar.png"),
    "scouter_blue.png": ((128, 128), SOURCE / "scouter/scouter_blue.png"),
    "scouter_green.png": ((128, 128), SOURCE / "scouter/scouter_green.png"),
    "scouter_purple.png": ((128, 128), SOURCE / "scouter/scouter_purple.png"),
    "scouter_red.png": ((128, 128), SOURCE / "scouter/scouter_red.png"),
}


def build() -> tuple[dict[pathlib.Path, bytes], dict[str, object]]:
    outputs: dict[pathlib.Path, bytes] = {}
    entries: dict[str, object] = {}
    for name, (expected_size, source) in FILES.items():
        if not source.exists():
            raise FileNotFoundError(source)
        with Image.open(source) as image:
            if image.size != expected_size:
                raise ValueError(f"{source} is {image.size}, expected {expected_size}")
        data = source.read_bytes()
        outputs[OUTPUT / name] = data
        entries[name] = {
            "source": source.relative_to(ROOT).as_posix(),
            "size": list(expected_size),
            "sha256": hashlib.sha256(data).hexdigest(),
        }
    return outputs, {
        "generator": "tools/gen_dmz_hud_theme.py",
        "theme_gate": "XenoHudConfig.dmzMenusThemed()",
        "outputs": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        outputs, manifest = build()
    except (FileNotFoundError, ValueError) as exc:
        print(exc, file=sys.stderr)
        return 1
    expected = dict(outputs)
    expected[MANIFEST] = (json.dumps(manifest, indent=2) + "\n").encode()
    stale = [path for path, data in expected.items() if not path.exists() or path.read_bytes() != data]
    if args.check:
        if stale:
            for path in stale:
                print(f"out of date: {path}", file=sys.stderr)
            return 1
        print("DMZ HUD theme is up to date")
        return 0
    for path, data in expected.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    print("Generated 6 DMZ HUD theme textures")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
