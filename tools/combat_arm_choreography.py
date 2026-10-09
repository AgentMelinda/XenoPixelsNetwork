"""Keep the new combat arms in front of the torso, with a compact off-hand guard.

Owner correction,2026-10-08: no thrown-back counterbalancing arm, including kicks.
The supporting leg, hips, torso and strike timing stay authored independently.
"""
import json
from pathlib import Path


def is_new_clip(name):
    return (name.endswith(('_v3', '_v4')) or name.startswith(('combat.xeno_charge_',
            'combat.xeno_cinematic_rush_', 'combat.xeno_bt3_v3_')))


def front_arms(animations):
    changed = []
    for name, animation in animations.items():
        if not is_new_clip(name):
            continue
        # Owner 2026-03-22: charged punch/kick fire may overshoot XYZ out of the body.
        if name in ('combat.xeno_charge_punch_fire', 'combat.xeno_charge_kick_fire',
                    'combat.xeno_charge_punch_hold', 'combat.xeno_charge_kick_hold'):
            continue
        punch = any(word in name for word in ('jab_', 'cross_', 'hook_', 'uppercut_', 'body_punch_', 'charge_punch', 'dmz_punch_'))
        kick = any(word in name for word in ('kick', 'knee', 'roundhouse'))
        attacking = 'left_arm' if '_left' in name else 'right_arm'
        modified = False
        for bone, channels in animation.get('bones', {}).items():
            if bone not in ('left_arm', 'right_arm'):
                continue
            for channel in ('rotation', 'position'):
                for frame in channels.get(channel, {}).values():
                    if not isinstance(frame, dict) or not isinstance(frame.get('vector'), list):
                        continue
                    prior = frame['vector']
                    vector = list(prior)
                    if channel == 'position':
                        # Positive local Z translates the shoulder behind its authored bind point.
                        vector[2] = min(vector[2], 0)
                        if punch and bone != attacking:
                            vector = [0, 0, 0]
                    elif kick or (punch and bone != attacking):
                        vector = [max(-95, min(-55, vector[0])),
                                  max(-18, min(18, vector[1])), max(-32, min(32, vector[2]))]
                    else:
                        if vector[0] > 0:
                            vector[0] = -55
                        if punch and vector[0] > -75:
                            vector = [min(vector[0], -45), max(-25, min(25, vector[1])),
                                      max(-38, min(38, vector[2]))]
                    if vector != prior:
                        frame['vector'] = vector
                        modified = True
        if modified:
            changed.append(name)
    return changed


def main():
    # Amend only the requested poses; preserve all other dirty resource entries byte values.
    path = Path('src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json')
    resource = json.loads(path.read_text(encoding='utf-8'))
    changed = front_arms(resource['animations'])
    path.write_text(json.dumps(resource, indent=2) + '\n', encoding='utf-8')
    print(f'Corrected arms in {len(changed)} authored clips')
    print('\n'.join(changed))


if __name__ == '__main__':
    main()
