"""Dodoria Head Breaker re-authored from the local BT3 reference video (2026-10-08).

Source: the owner's local MP4 (Part 1, 720p), occurrences at 3248 s and 3261 s, inspected as
15 fps contact sheets (ffmpeg fps=15, 400 px, 6x5 tiles) over 3249.2-3251.2 s and 3262.3-3264.3 s.

Observed sequence (both occurrences agree):
  * ~0.0-0.6 s  aura burst; Dodoria upright, chest up, both arms swept up and back.
  * ~0.6-0.9 s  the whole body pitches forward to horizontal: head leading, arms trailing along
                the flanks, legs straight back - a torpedo, not a standing headbutt.
  * ~0.9-1.3 s  flies forward inside a spinning purple energy sphere.
  * ~1.3 s      head/body impact, large burst, victim launched away ("Prima attacco!" appears).
  * ~1.3-2.3 s  drive-through and recovery to standing.

The previous authored clip was a standing forward lean with a forward guard; that is not what the
source shows, so it is replaced by two clips:
  combat.xeno_bt3_v3_dodoria_head_breaker_dive  - wind-up then torpedo hold, sent at cast start so
                                                  it plays through the server-owned fly-in
  combat.xeno_bt3_v3_dodoria_head_breaker       - torpedo -> contact -> recovery, sent on arrival

Rotation sign convention follows the existing v3 clips in this file: +X on waist/arms = pitch
forward (arms at -65 are raised forward; waist at +60 leans forward). The owner permits bone
displacement beyond the body; the head is pushed forward at contact.

Status stays reference_timed_unverified: timed and posed from the source frames, NOT yet compared
against a rendered in-game clip. Nothing here is a claim of one-to-one parity.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / 'src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json'
OVERRIDES = ROOT / 'docs/combat-v3/choreography.json'
BONES = ['root', 'waist', 'head', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
HEAD = 'combat.xeno_bt3_v3_dodoria_head_breaker'
DIVE = 'combat.xeno_bt3_v3_dodoria_head_breaker_dive'

# Torpedo: body horizontal, head leading, arms trailing along the flanks, legs straight back.
TORPEDO = [[0, 0, 0], [88, 0, 0], [-32, 0, 0], [58, -8, 18], [58, 8, -18], [6, 0, 5], [6, 0, -5]]
# Wind-up: chest up, both arms swept up and back (source 3249.3-3249.8 s).
WINDUP = [[0, 0, 0], [-12, 0, 0], [-16, 0, 0], [-150, -18, 28], [-150, 18, -28], [-8, 0, 6], [8, 0, -6]]
REST = [[0, 0, 0], [0, 0, 0], [0, 0, 0], [-22, -12, 24], [-22, 12, -24], [0, 0, 0], [0, 0, 0]]


def animation(length, poses, translations):
    bones = {bone: {'rotation': {str(time): {'vector': pose[index]} for time, pose in poses.items()}}
             for index, bone in enumerate(BONES)}
    for bone, frames in translations.items():
        bones[bone]['position'] = {str(time): {'vector': vector} for time, vector in frames.items()}
    return {'animation_length': length, 'loop': False, 'bones': bones}


def with_root_roll(pose, roll):
    out = [list(v) for v in pose]
    out[0] = [0, 0, roll]
    return out


def main():
    data = json.loads(RESOURCE.read_text(encoding='utf-8'))
    # Fly-in clip: wind-up 0.25 s, pitch to torpedo by 0.45 s, spin one full roll while flying.
    data['animations'][DIVE] = animation(1.25, {
        0: WINDUP,
        .25: [[0, 0, 0], [20, 0, 0], [-24, 0, 0], [-60, -14, 24], [-60, 14, -24], [-4, 0, 6], [4, 0, -6]],
        .45: TORPEDO,
        .85: with_root_roll(TORPEDO, 180),
        1.25: with_root_roll(TORPEDO, 360)},
        {'head': {0: [0, 0, 0], .25: [0, 0.5, -1], .45: [0, 0.5, -2], 1.25: [0, 0.5, -2]}})
    # Arrival clip: torpedo at 0, contact drive at 0.2 s (STRIKE), recoil, back on the feet by 1.0 s.
    data['animations'][HEAD] = animation(1.3, {
        0: TORPEDO,
        .2: [[0, 0, 0], [96, 0, 0], [-42, 0, 0], [66, -6, 14], [66, 6, -14], [10, 0, 5], [10, 0, -5]],
        .4: [[0, 0, 0], [72, 0, 0], [-20, 0, 0], [30, -10, 20], [30, 10, -20], [-6, 0, 6], [6, 0, -6]],
        .7: [[0, 0, 0], [30, 0, 0], [-6, 0, 0], [-40, -12, 22], [-40, 12, -22], [-14, 0, 6], [12, 0, -6]],
        1: [[0, 0, 0], [6, 0, 0], [0, 0, 0], [-30, -12, 24], [-30, 12, -24], [-4, 0, 4], [4, 0, -4]],
        1.3: REST},
        {'head': {0: [0, 0.5, -2], .2: [0, 0, -3.5], .4: [0, 0, -1.5], .7: [0, 0, -0.5], 1: [0, 0, 0]}})
    RESOURCE.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

    authored = json.loads(OVERRIDES.read_text(encoding='utf-8'))
    for seconds, window in ((3248, '3249.2-3251.2'), (3261, '3262.3-3264.3')):
        key = f'xenopixelsmod:bt3_dodoria_frantumateste_di_dodoria_{seconds}'
        # Owner: head-breaker launches the victim DOWN (not up/forward). Distance uses
        # /xenoset v3.strikeLaunchDistance (default 20).
        beats = [beat('POSE', 0, DIVE), beat('APPROACH', 0, duration=10), beat('POSE', 1, HEAD),
                 beat('STRIKE', 5, value=2.4), beat('SHOVE', 6, 'DOWN'), beat('END', 28)]
        entry = {
            'id': key, 'sourceStartsMs': [seconds * 1000], 'durationTicks': 28,
            'animationStatus': 'reference_timed_unverified', 'beats': beats,
            'evidence': {
                'source': 'owner local MP4, Part 1 720p, 2026-10-08',
                'window_s': window,
                'method': 'ffmpeg fps=15 scale=400 tile=6x5 contact sheets, read by the authoring agent',
            },
            'landmarks': [
                'aura burst, upright, both arms swept up and back',
                'whole body pitches to horizontal: head leading, arms trailing on the flanks, legs straight back',
                'flies forward spinning inside a purple energy sphere',
                'head/body impact burst; victim launched away',
                'drive-through and recovery to standing',
            ],
            'unverified': [
                'Bone angles are posed from 15 fps source frames; no rendered in-game comparison yet.',
                'Whether DragonMineZ keeps the dive pose visible over its own flight animation during the server fly-in is not verified.',
                'The energy sphere around the dive is not drawn; only the body choreography is authored.',
            ],
        }
        authored['entries'] = [item for item in authored['entries'] if item['id'] != key] + [entry]
    OVERRIDES.write_text(json.dumps(authored, indent=2) + '\n', encoding='utf-8')


def beat(kind, tick, payload='', duration=0, value=0):
    return {'kind': kind, 'tick': tick, 'duration': duration, 'payload': payload, 'value': value}


if __name__ == '__main__':
    main()
