"""Retarget the first local-video Kamehameha to DMZ's seven-bone rig.

Timing is source-PTS measured; angles and camera distance are hand-retargeted estimates.
This clip is not accepted until compared in a fresh client. No source travel is baked into root.
"""
import json
from pathlib import Path

CLIP = 'combat.xeno_bt3_v3_kid_goku_kame_15'
RESOURCE = Path('src/main/resources/assets/xenopixelsmod/animations/entity/bt3_v3_techniques.animation.json')
OVERRIDES = Path('docs/combat-v3/choreography.json')

# Root, waist, head, arms, legs: low supporting stance, right-hip cup, forward push, recoil.
POSES = {
    '0.0': [[0, 0, 0], [14, 8, 0], [-8, -8, 0], [-42, -18, 28], [-38, 18, -28], [-18, -12, 8], [20, 12, -8]],
    '0.15': [[0, -15, 0], [22, -28, -5], [-10, 38, 0], [-30, -48, 38], [-56, -38, -35], [-28, -18, 10], [34, 20, -10]],
    '1.75': [[0, -15, 0], [22, -28, -5], [-10, 38, 0], [-30, -48, 38], [-56, -38, -35], [-28, -18, 10], [34, 20, -10]],
    '1.80': [[0, -8, 0], [18, -5, 0], [-12, 8, 0], [-65, -20, 15], [-72, 16, -15], [-30, -18, 10], [30, 20, -10]],
    '1.90': [[0, 0, 0], [20, 5, 0], [-15, -5, 0], [-92, -6, 6], [-92, 6, -6], [-36, -20, 12], [26, 22, -12]],
    '3.00': [[0, 0, 0], [20, 5, 0], [-15, -5, 0], [-92, -6, 6], [-92, 6, -6], [-36, -20, 12], [26, 22, -12]],
    '3.15': [[0, 0, 0], [14, 8, 0], [-8, -8, 0], [-42, -18, 28], [-38, 18, -28], [-18, -12, 8], [20, 12, -8]],
}
BONES = ['root', 'waist', 'head', 'right_arm', 'left_arm', 'right_leg', 'left_leg']


def main():
    resource = json.loads(RESOURCE.read_text()) if RESOURCE.exists() else {'format_version': '1.8.0', 'animations': {}}
    resource['animations'].pop(CLIP + '_cast', None)
    charge = {'0.0': POSES['0.0'], '0.15': POSES['0.15'], '1.75': POSES['1.75'], '1.8': POSES['1.75']}
    release = {'0.0': POSES['1.75'], '0.05': POSES['1.80'], '0.1': POSES['1.90'],
               '1.15': POSES['3.00'], '1.25': POSES['3.15']}
    for suffix, length, loop, poses in [('charge', 1.8, 'hold_on_last_frame', charge),
                                        ('release', 1.25, False, release)]:
        resource['animations'][CLIP + '_' + suffix] = {
            'animation_length': length, 'loop': loop, 'bones': {
                bone: {'rotation': {time: {'vector': pose[index]} for time, pose in poses.items()}}
                for index, bone in enumerate(BONES)}}
    # Server cancellation/end replaces the held controller with a finite neutral control clip.
    resource['animations']['combat.xeno_bt3_v3_release_control'] = {
        'animation_length': 0.05, 'loop': False, 'bones': {
            bone: {'rotation': {'0.0': {'vector': [0, 0, 0]}, '0.05': {'vector': [0, 0, 0]}}}
            for bone in BONES}}
    RESOURCE.write_text(json.dumps(resource, indent=2) + '\n', encoding='utf-8')
    data = json.loads(OVERRIDES.read_text()) if OVERRIDES.exists() else {'schema': 1, 'entries': []}
    key = 'xenopixelsmod:bt3_early_kid_goku_kamehameha_15'
    entry = {
        'id': key, 'sourceStartsMs': [15000], 'animationStatus': 'reference_timed_unverified',
        'durationTicks': 61,
        'beats': [beat('POSE', 0, CLIP + '_charge'), beat('KI_CHARGE', 3), beat('KI_HOLD', 36),
                  beat('POSE', 36, CLIP + '_release'), beat('KI_RELEASE', 39), beat('END', 61)],
        'camera': [
            {'tick': 0, 'duration': 39, 'position': [2.8, 1.0, -4.5], 'look': [0, 0, 0], 'focus': 0.25, 'easing': 'CUT'},
            {'tick': 39, 'duration': 22, 'position': [2.4, 0.8, -3.8], 'look': [0, 0, 0], 'focus': 0.3, 'easing': 'SMOOTH'}],
        'evidence': '.superpowers/sdd/2026-10-07-combat-v3-bt3/video-dense-part1/kid-kame-timing-2026-10-08.json',
        'landmarks': [
            {'sourceSeconds': 16.950266667, 'tick': 0, 'kind': 'cast_start'},
            {'sourceSeconds': 17.083733333, 'tick': 3, 'kind': 'hand_charge'},
            {'sourceSeconds': 18.752066667, 'tick': 36, 'kind': 'push_start'},
            {'sourceSeconds': 18.852166667, 'tick': 38, 'kind': 'forward_pose'},
            {'sourceSeconds': 18.9189, 'tick': 39, 'kind': 'beam_flare'},
            {'sourceSeconds': 19.986633333, 'tick': 61, 'kind': 'beam_ends'}],
        'unverified': ['Retargeted bone angles, child/adult proportions, hand placement and supporting foot pivot.',
                       'Camera offsets are estimates; source provides framing, not Minecraft distances.',
                       'Victim is obscured by blast; no invented victim reaction clip.',
                       'Projectile duration/contacts and recovery require fresh client comparison.',
                       'KI_HOLD pauses before the3tick hand-push lead-in; exact visible pose parity remains unverified.',
                       'Ordinary forward advance after20.086733 is excluded from named cast.']}
    data['entries'] = [item for item in data['entries'] if item['id'] != key] + [entry]
    OVERRIDES.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def beat(kind, tick, payload=''):
    return {'kind': kind, 'tick': tick, 'duration': 0, 'payload': payload, 'value': 0.0}


if __name__ == '__main__':
    main()
