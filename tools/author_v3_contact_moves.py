"""Authored head-first contact and airborne stomp; gameplay retargets, not verified BT3 copies."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / 'src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json'
OVERRIDES = ROOT / 'docs/combat-v3/choreography.json'
BONES = ['root', 'waist', 'head', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
HEAD = 'combat.xeno_bt3_v3_dodoria_head_breaker'
SLAM = 'combat.xeno_bt3_v3_dash_slam'


def animation(length, poses, translations):
    bones = {bone: {'rotation': {str(time): {'vector': pose[index]} for time, pose in poses.items()}}
             for index, bone in enumerate(BONES)}
    for bone, frames in translations.items():
        bones[bone]['position'] = {str(time): {'vector': vector} for time, vector in frames.items()}
    return {'animation_length': length, 'loop': False, 'bones': bones}


def main():
    data = json.loads(RESOURCE.read_text(encoding='utf-8'))
    data['animations'][HEAD] = animation(1.6, {
        0: [[0, 0, 0], [-12, 0, 0], [-18, 0, 0], [-65, -12, 24], [-65, 12, -24], [-15, 0, 6], [15, 0, -6]],
        .2: [[0, 0, 0], [-22, 0, 0], [-25, 0, 0], [-65, -10, 20], [-65, 10, -20], [-30, 0, 10], [25, 0, -10]],
        .5: [[0, 0, 0], [48, 0, 0], [28, 0, 0], [-70, -8, 18], [-70, 8, -18], [-22, 0, 8], [20, 0, -8]],
        .7: [[0, 0, 0], [60, 0, 0], [38, 0, 0], [-70, -8, 18], [-70, 8, -18], [-18, 0, 8], [18, 0, -8]],
        1: [[0, 0, 0], [18, 0, 0], [-8, 0, 0], [-65, -12, 24], [-65, 12, -24], [-15, 0, 6], [15, 0, -6]],
        1.6: [[0, 0, 0], [0, 0, 0], [0, 0, 0], [-55, -12, 24], [-55, 12, -24], [0, 0, 0], [0, 0, 0]]},
        {'head': {0: [0, 0, 0], .2: [0, 1, 1], .5: [0, 0, -4], .7: [0, -1, -5], 1: [0, 0, -1], 1.6: [0, 0, 0]}})
    data['animations'][SLAM] = animation(.8, {
        0: [[0, 0, 0], [-12, 0, 0], [16, 0, 0], [-65, -15, 24], [-65, 15, -24], [-95, 0, 8], [10, 0, -8]],
        .15: [[0, 0, 0], [-18, 0, 0], [20, 0, 0], [-65, -15, 24], [-65, 15, -24], [-125, 0, 10], [20, 0, -8]],
        .3: [[0, 0, 0], [28, 0, 0], [8, 0, 0], [-70, -12, 20], [-70, 12, -20], [20, 0, 8], [-25, 0, -8]],
        .45: [[0, 0, 0], [35, 0, 0], [8, 0, 0], [-70, -12, 20], [-70, 12, -20], [15, 0, 8], [-20, 0, -8]],
        .8: [[0, 0, 0], [0, 0, 0], [0, 0, 0], [-55, -12, 24], [-55, 12, -24], [0, 0, 0], [0, 0, 0]]},
        {'right_leg': {0: [0, 0, 0], .15: [0, 2, -2], .3: [0, -6, -1], .45: [0, -5, -1], .8: [0, 0, 0]}})
    RESOURCE.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    authored = json.loads(OVERRIDES.read_text(encoding='utf-8'))
    for seconds in (3248, 3261):
        key = f'xenopixelsmod:bt3_dodoria_frantumateste_di_dodoria_{seconds}'
        beats = [beat('APPROACH', 0, duration=10), beat('POSE', 2, HEAD),
                 beat('STRIKE', 12, value=2.4), beat('SHOVE', 13, 'FORWARD'), beat('END', 34)]
        entry = {'id': key, 'sourceStartsMs': [seconds * 1000], 'durationTicks': 34,
                 'animationStatus': 'authored_gameplay_unverified', 'beats': beats,
                 'unverified': ['Head-first body/head contact replaces generic punches. Bone angles and timing are authored gameplay estimates, not source-measured or runtime-compared.']}
        authored['entries'] = [item for item in authored['entries'] if item['id'] != key] + [entry]
    OVERRIDES.write_text(json.dumps(authored, indent=2) + '\n', encoding='utf-8')


def beat(kind, tick, payload='', duration=0, value=0):
    return {'kind': kind, 'tick': tick, 'duration': duration, 'payload': payload, 'value': value}


if __name__ == '__main__':
    main()
