"""Keep authored V3 occurrence root translation separate from server-owned travel.

Run after all occurrence authoring scripts. Local bone translation and every XYZ
rotation stay intact; only root position frames become zero.
"""
from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"


def main() -> None:
    resource = json.loads(RESOURCE.read_text(encoding="utf-8"))
    changed = 0
    for clip in resource["animations"].values():
        root = clip.get("bones", {}).get("root", {})
        for frame in root.get("position", {}).values():
            vector = frame.get("vector") if isinstance(frame, dict) else None
            if isinstance(vector, list) and len(vector) == 3 and vector != [0, 0, 0]:
                frame["vector"] = [0, 0, 0]
                changed += 1
    if changed:
        RESOURCE.write_text(json.dumps(resource, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Zeroed {changed} occurrence root-position frames")


if __name__ == "__main__":
    main()
