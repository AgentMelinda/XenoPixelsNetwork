"""Author type-realistic choreography + cameras for EVERY Combat V3 Strike occurrence.

Owner 2026-03-22: not limited to Dodoria/Kamehameha — every strike gets Search-Fly approach
(where contact), rush→wide cinematic (contact/mixed), charge framing (pure ki), and bone poses.

Honest status: authored_gameplay_unverified (not reference_compared). Hand-authored entries
already in docs/combat-v3/choreography.json are preserved when their status is stronger than
archetype_placeholder.

Run from repo root:
  python tools/author_v3_all_strikes_batch.py
  python tools/gen_combat_v3_techniques.py
"""
from __future__ import annotations

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TECHNIQUES = ROOT / "src/main/resources/data/xenopixelsmod/combat_v3/techniques.json"
CHOREOGRAPHY = ROOT / "docs/combat-v3/choreography.json"

# Existing DMZ-compatible melee clips already used by gen_combat_v3_techniques.py.
# Do not attach the long kid-Goku Kamehameha charge/release clips to every technique —
# V3TechniqueCatalog refuses when durationTicks cannot cover the animation length.
PUNCH = "combat.one_handed_punch_right"
KICK = "combat.gutkick_right"
HEAD = "combat.xeno_bt3_v3_dodoria_head_breaker"

PRESERVE_STATUSES = {
    "reference_timed_unverified",
    "reference_compared",
    "authored_gameplay_unverified",
}


def beat(kind, tick, duration=0, payload="", value=0.0):
    return {
        "kind": kind,
        "tick": tick,
        "duration": duration,
        "payload": payload,
        "value": float(value),
    }


def shot(tick, duration, position, look=(0, 0, 0), focus=0.5, easing="SMOOTH"):
    return {
        "tick": tick,
        "duration": duration,
        "position": list(position),
        "look": list(look),
        "focus": focus,
        "easing": easing,
    }


def contact_camera(end_tick: int):
    """Rush in, then wide freeze on the pair — BT3 strike cinematic shape."""
    rush_end = min(12, max(6, end_tick // 4))
    return [
        shot(0, rush_end, (1.2, 1.4, -2.2), focus=0.15, easing="CUT"),
        shot(rush_end, max(8, end_tick - rush_end), (5.5, 2.2, -6.0), focus=0.55, easing="SMOOTH"),
    ]


def ki_camera(end_tick: int):
    charge_end = min(20, max(8, end_tick // 2))
    return [
        shot(0, charge_end, (2.6, 1.0, -4.2), focus=0.2, easing="CUT"),
        shot(charge_end, max(8, end_tick - charge_end), (3.4, 1.4, -5.5), focus=0.45, easing="SMOOTH"),
    ]


def timelines(kind: str):
    """Improved type timelines: Search Fly approach + multi-hit + energy where mixed."""
    if kind == "melee":
        beats = [
            beat("APPROACH", 0, duration=10),
            beat("POSE", 2, payload=PUNCH),
            beat("STRIKE", 12, value=0.55),
            beat("POSE", 14, payload=KICK),
            beat("STRIKE", 16, value=0.55),
            beat("POSE", 18, payload=PUNCH),
            beat("STRIKE", 20, value=0.7),
            beat("POSE", 22, payload=KICK),
            beat("STRIKE", 24, value=1.35),
            beat("SHOVE", 24, payload="FORWARD"),
            beat("END", 36),
        ]
        return 36, beats, contact_camera(36)
    if kind == "grab":
        beats = [
            beat("APPROACH", 0, duration=10),
            beat("POSE", 2, payload=PUNCH),
            beat("HOLD_TARGET", 12, duration=16),
            beat("POSE", 24, payload=KICK),
            beat("STRIKE", 26, value=1.6),
            beat("SHOVE", 26, payload="FORWARD"),
            beat("END", 38),
        ]
        return 38, beats, contact_camera(38)
    if kind == "melee_energy":
        beats = [
            beat("APPROACH", 0, duration=10),
            beat("POSE", 2, payload=PUNCH),
            beat("STRIKE", 12, value=0.55),
            beat("POSE", 14, payload=PUNCH),
            beat("STRIKE", 16, value=0.65),
            beat("POSE", 18, payload=KICK),
            beat("STRIKE", 20, value=0.85),
            beat("SHOVE", 20, payload="FORWARD"),
            beat("KI_CHARGE", 22),
            beat("KI_RELEASE", 34),
            beat("END", 52),
        ]
        return 52, beats, contact_camera(52)
    if kind == "radial":
        beats = [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, payload=PUNCH),
            beat("RADIAL", 14, payload="8", value=1.5),
            beat("END", 28),
        ]
        return 28, beats, contact_camera(28)
    if kind == "hold":
        beats = [
            beat("APPROACH", 0, duration=8),
            beat("POSE", 2, payload=PUNCH),
            beat("HOLD_TARGET", 10, duration=48),
            beat("END", 20),
        ]
        return 20, beats, contact_camera(20)
    if kind == "beam":
        beats = [
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 20),
            beat("END", 48),
        ]
        return 48, beats, ki_camera(48)
    if kind == "laser":
        beats = [
            beat("KI_CHARGE", 1),
            beat("KI_RELEASE", 7),
            beat("END", 24),
        ]
        return 24, beats, ki_camera(24)
    if kind == "disc":
        beats = [
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 13),
            beat("END", 32),
        ]
        return 32, beats, ki_camera(32)
    if kind == "ball":
        beats = [
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 15),
            beat("END", 36),
        ]
        return 36, beats, ki_camera(36)
    if kind == "giant_ball":
        beats = [
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 30),
            beat("END", 60),
        ]
        return 60, beats, ki_camera(60)
    if kind == "volley":
        beats = [
            beat("KI_CHARGE", 2),
            beat("KI_RELEASE", 13),
            beat("END", 42),
        ]
        return 42, beats, ki_camera(42)
    # Fallback melee-shaped
    return timelines("melee")


def stronger(existing_status: str | None) -> bool:
    return existing_status in PRESERVE_STATUSES


def main():
    catalog = json.loads(TECHNIQUES.read_text(encoding="utf-8"))
    techniques = catalog["techniques"]
    authored = {"schema": 1, "entries": []}
    if CHOREOGRAPHY.exists():
        authored = json.loads(CHOREOGRAPHY.read_text(encoding="utf-8"))
        if authored.get("schema") != 1:
            raise SystemExit("Unsupported choreography schema")

    by_id = {e["id"]: e for e in authored.get("entries", [])}
    kept = []
    written = 0
    preserved = 0

    for tech in techniques:
        tid = tech["id"]
        existing = by_id.get(tid)
        if existing and stronger(existing.get("animationStatus")):
            kept.append(existing)
            preserved += 1
            continue
        duration, beats, camera = timelines(tech["type"])
        # Dodoria head-breaker labels keep the head-first pose when present in the name.
        label = (tech.get("sourceLabel") or tech.get("name") or "").lower()
        if "frantumateste" in label or "head breaker" in label or "testa" in label and "dodoria" in label:
            beats = [
                beat("APPROACH", 0, duration=10),
                beat("POSE", 2, payload=HEAD),
                beat("STRIKE", 12, value=2.4),
                beat("SHOVE", 13, payload="FORWARD"),
                beat("END", 34),
            ]
            duration = 34
            camera = contact_camera(34)
        entry = {
            "id": tid,
            "sourceStartsMs": copy.deepcopy(tech["sourceStartsMs"]),
            "animationStatus": "authored_gameplay_unverified",
            "durationTicks": duration,
            "beats": beats,
            "camera": camera,
            "unverified": [
                "Type-realistic bulk choreography from graphify-guided author_v3_all_strikes_batch.py. "
                "Bone angles and camera distances are gameplay estimates, not measured 1:1 BT3 PTS parity."
            ],
        }
        kept.append(entry)
        written += 1

    # Keep any orphan authored entries that still match a technique id
    tech_ids = {t["id"] for t in techniques}
    for eid, entry in by_id.items():
        if eid not in tech_ids:
            continue
        if all(e["id"] != eid for e in kept):
            kept.append(entry)
            preserved += 1

    kept.sort(key=lambda e: e["id"])
    out = {"schema": 1, "entries": kept}
    CHOREOGRAPHY.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"choreography entries={len(kept)} newly_authored={written} preserved={preserved}")


if __name__ == "__main__":
    main()
