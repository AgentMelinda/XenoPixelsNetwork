"""Author the two distinct Meteor Crash occurrences from the local BT3 video.

310 s has a separated opening impact and a 13-hit aerial rush. 691 s is a
faster 14-hit sequence with a sideways fall before its final crash. Poses use
all seven rig bones and XYZ rotation/position. Angles are reference-guided
estimates until a fresh in-game visual comparison.
"""
from __future__ import annotations

import json
from pathlib import Path

from author_v3_meteor_combination_116 import _beat, _shot, _vector


ROOT = Path(__file__).resolve().parents[1]
ANIMATIONS = ROOT / "src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json"
CHOREOGRAPHY = ROOT / "docs/combat-v3/choreography.json"
BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")
ROWS = (
    (
        "xenopixelsmod:bt3_early_goku_super_saiyan_schianto_della_meteorite_310",
        "combat.xeno_bt3_v3_meteor_crash_310",
        (1.0, 5.0, 5.2, 5.4, 5.6, 5.9, 6.2, 6.5, 6.8, 7.1, 7.4, 8.5, 10.0),
        232,
        1,
    ),
    (
        "xenopixelsmod:bt3_687_i_ll_beat_you_in_5_seconds_6_schianto_della_meteorite_691",
        "combat.xeno_bt3_v3_meteor_crash_691",
        (1.8, 2.2, 2.5, 2.8, 3.1, 3.4, 3.7, 4.0, 4.3, 4.6, 4.9, 5.2, 6.4, 8.2),
        204,
        -1,
    ),
)


def _clip(hits: tuple[float, ...], duration_ticks: int, facing: int) -> dict:
    tracks: dict[str, dict[str, dict[str, dict]]] = {
        bone: {"rotation": {}, "position": {}} for bone in BONES
    }

    def key(time: float, bone: str, rotation=None, position=None) -> None:
        stamp = f"{time:.3f}".rstrip("0").rstrip(".")
        if rotation is not None:
            tracks[bone]["rotation"][stamp] = _vector(rotation)
        if position is not None:
            tracks[bone]["position"][stamp] = _vector((0, 0, 0) if bone == "root" else position)

    for bone in BONES:
        key(0, bone, (0, 0, 0), (0, 0, 0))
    key(0.45, "root", (12, facing * 8, -facing * 4), (0, 0.15, -0.25))
    key(0.45, "waist", (20, facing * 10, 0))
    key(0.45, "head", (-12, -facing * 7, 0))
    key(0.45, "right_arm", (-65, -18, 20), (0.4, 0.15, -0.25))
    key(0.45, "left_arm", (-65, 18, -20), (-0.4, 0.15, -0.25))
    key(0.45, "right_leg", (-28, 8, 5), (0.15, 0.12, 0.3))
    key(0.45, "left_leg", (22, -8, -5), (-0.15, 0.12, 0.3))

    if len(hits) == 13:
        # At 311 s one bright opening contact is followed by a long airborne gap.
        key(2.6, "root", (32, facing * 18, -facing * 6), (0, 0.45, -0.8))
        key(2.6, "right_arm", (-80, -12, 28), (0.65, -0.1, -1.1))
        key(2.6, "left_arm", (-78, 12, -28), (-0.65, -0.1, -1.1))
        key(4.65, "root", (20, -facing * 12, facing * 5), (0, 0.3, -0.55))
    else:
        # At 694 s the attacker folds into a headlong flying rush.
        key(2.7, "root", (45, -facing * 14, facing * 9), (0, 0.45, -0.95))
        key(2.7, "waist", (30, -facing * 12, 0))
        key(2.7, "right_arm", (-48, -14, 25), (0.55, -0.3, 0.2))
        key(2.7, "left_arm", (-48, 14, -25), (-0.55, -0.3, 0.2))

    for index, hit in enumerate(hits):
        side = "right" if index % 2 == 0 else "left"
        other = "left" if side == "right" else "right"
        sign = facing * (1 if side == "right" else -1)
        kick = index in (0, 5, 10) if len(hits) == 13 else index in (3, 10, 13)
        pre = hit - (0.13 if index == 0 or hit - hits[index - 1] > 0.2 else 0.06)
        key(pre, "root", (12, -sign * 10, sign * 5), (0, 0.1, -0.35))
        key(pre, "waist", (14, -sign * 22, sign * 7))
        key(pre, "head", (-4, sign * 8, 0))
        if kick:
            key(pre, f"{side}_leg", (-65, sign * 15, sign * 11),
                (sign * 0.18, 0.25, 0.5))
            key(hit, f"{side}_leg", (-128, sign * 25, sign * 17),
                (sign * 0.3, 0.35, -4.0))
            key(hit, f"{other}_leg", (-22, -sign * 10, -sign * 5),
                (-sign * 0.12, -0.08, 0.2))
            key(hit, "right_arm", (-60, -12, 19), (0.2, 0.1, -0.3))
            key(hit, "left_arm", (-60, 12, -19), (-0.2, 0.1, -0.3))
        else:
            key(pre, f"{side}_arm", (-68, -sign * 22, sign * 18),
                (sign * 0.25, 0.15, 0.25))
            key(hit, f"{side}_arm", (-125, sign * 7, sign * 9),
                (-sign * 0.35, 0.35, -4.15))
            key(hit, f"{other}_arm", (-67, -sign * 16, -sign * 18),
                (-sign * 0.18, 0.1, -0.3))
            key(hit, "right_leg", (-16, 8, 4), (0.1, -0.06, 0.18))
            key(hit, "left_leg", (18, -8, -4), (-0.1, -0.06, 0.18))
        key(hit, "root", (-10, sign * 16, -sign * 8), (0, 0.22, -0.85))
        key(hit, "waist", (-18, sign * 28, -sign * 11))
        key(hit, "head", (6, -sign * 7, 0))

    if len(hits) == 14:
        # The 698 s side fall is visible before the fourteenth hit.
        key(6.9, "root", (72, facing * 28, facing * 48), (0, 0.48, -0.4))
        key(6.9, "waist", (18, facing * 18, facing * 12))
        key(6.9, "head", (-18, -facing * 8, 0))
        key(6.9, "right_arm", (-52, -18, 25), (0.45, -0.2, -0.35))
        key(6.9, "left_arm", (-52, 18, -25), (-0.45, -0.2, -0.35))
    else:
        key(9.15, "root", (38, -facing * 16, facing * 12), (0, 0.3, -0.7))
        key(9.15, "waist", (22, -facing * 20, facing * 7))

    end = duration_ticks / 20 - 0.25
    for bone in BONES:
        key(end, bone, (0, 0, 0), (0, 0, 0))
    key(end, "right_arm", (-55, -14, 22), (0.15, 0, -0.2))
    key(end, "left_arm", (-55, 14, -22), (-0.15, 0, -0.2))
    for channels in tracks.values():
        for name, frames in channels.items():
            channels[name] = dict(sorted(frames.items(), key=lambda item: float(item[0])))
    return {"animation_length": duration_ticks / 20 - 0.1, "bones": tracks}


def main() -> None:
    resource = json.loads(ANIMATIONS.read_text(encoding="utf-8"))
    authored = json.loads(CHOREOGRAPHY.read_text(encoding="utf-8"))
    by_id = {row["id"]: row for row in authored["entries"]}
    for technique_id, clip_id, hits, duration, facing in ROWS:
        if technique_id not in by_id:
            raise ValueError(f"Missing choreography override for {technique_id}")
        resource["animations"][clip_id] = _clip(hits, duration, facing)
        row = by_id[technique_id]
        row["durationTicks"] = duration
        row["beats"] = [_beat("APPROACH", 0, duration=28), _beat("POSE", 0, clip_id)]
        for index, hit in enumerate(hits):
            row["beats"].append(_beat("STRIKE", round(hit * 20),
                                      value=1.15 if index == len(hits) - 1 else 0.25))
        row["beats"] += [_beat("SHOVE", round(hits[-1] * 20) + 3, "FORWARD"),
                         _beat("END", duration)]
        wide_start = 90 if len(hits) == 13 else 60
        row["camera"] = [
            _shot(0, wide_start, (2.2, 1.2, -3.4), 0.22, "CUT"),
            _shot(wide_start, 50, (5.0, 2.0, -5.7), 0.5, "SMOOTH"),
            _shot(wide_start + 50, duration - wide_start - 50,
                  (3.5, 2.5, -4.3), 0.48, "CUT"),
        ]
        row["evidence"] = f"docs/combat-v3/video-pipeline-302/meteor-crash-{technique_id[-3:]}"
        print(f"Authored {technique_id}: {len(hits)} strikes, {duration} ticks")
    ANIMATIONS.write_text(json.dumps(resource, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    CHOREOGRAPHY.write_text(json.dumps(authored, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
