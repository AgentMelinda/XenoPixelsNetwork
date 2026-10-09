"""Whole-body GeckoLib strikes with authored XYZ reach on DMZ's seven-bone rig."""


def build_weighted_strikes(clip, guard_l, guard_r):
    animations = {}
    for side in ("left", "right"):
        sign = -1 if side == "left" else 1
        other_side = "right" if side == "left" else "left"
        arm, other = side + "_arm", other_side + "_arm"
        leg, support = side + "_leg", other_side + "_leg"
        guard, other_guard = (guard_l, guard_r) if side == "left" else (guard_r, guard_l)
        animations["combat.xeno_hook_%s_v4" % side] = clip(0.30, {
            "root": {"rotation": {0.0: [0, 0, 0], 0.07: [4, 0, -5*sign],
                                   0.13: [-6, 0, 8*sign], 0.30: [0, 0, 0]}},
            "waist": {"rotation": {0.0: [0, 0, 0], 0.07: [5, -28*sign, -7*sign],
                                    0.13: [-9, 35*sign, 10*sign], 0.21: [-5, 18*sign, 5*sign], 0.30: [0, 0, 0]}},
            arm: {"rotation": {0.0: guard, 0.07: [-48, -62*sign, 78*sign],
                                0.13: [-88, 42*sign, 24*sign], 0.21: [-72, 18*sign, 32*sign], 0.30: guard},
                  "position": {0.0: [0, 0, 0], 0.07: [1.1*sign, 0.2, 0.6],
                               0.13: [-1.4*sign, 0.5, -3.2],
                               0.21: [-0.6*sign, 0.2, -1.5], 0.30: [0, 0, 0]}},
            other: {"rotation": {0.0: other_guard, 0.07: [-84, 14*sign, -42*sign],
                                  0.13: [-78, 18*sign, -48*sign], 0.30: other_guard}},
            leg: {"rotation": {0.0: [0, 0, 0], 0.07: [16, -8*sign, 0],
                                0.13: [-18, 18*sign, 4*sign], 0.30: [0, 0, 0]}},
            support: {"rotation": {0.0: [0, 0, 0], 0.07: [-12, 0, 0],
                                    0.13: [10, 12*sign, -4*sign], 0.30: [0, 0, 0]}},
            "head": {"rotation": {0.0: [0, 0, 0], 0.13: [3, -12*sign, -3*sign], 0.30: [0, 0, 0]}},
        })
        for kind, length, strike_pose in (("low", 0.32, [-48, 10*sign, 8*sign]),
                                           ("mid", 0.34, [-92, 18*sign, 10*sign]),
                                           ("knee", 0.28, [-120, 6*sign, 4*sign])):
            hit, recover = round(length * 0.44, 4), round(length * 0.70, 4)
            name = "combat.xeno_%s_%s_v4" % (("knee" if kind == "knee" else kind + "_kick"), side)
            animations[name] = clip(length, {
                "root": {"rotation": {0.0: [0, 0, 0], 0.07: [7, 0, -6*sign],
                                       hit: [15, 0, -9*sign], length: [0, 0, 0]},
                         "position": {0.0: [0, 0, 0], 0.07: [-0.8*sign, 0.3, 0],
                                      hit: [-1.0*sign, 0.5, 0], length: [0, 0, 0]}},
                "waist": {"rotation": {0.0: [0, 0, 0], 0.07: [-6, -18*sign, 4*sign],
                                        hit: [-10, 28*sign, 8*sign], recover: [-5, 12*sign, 4*sign], length: [0, 0, 0]}},
                leg: {"rotation": {0.0: [0, 0, 0], 0.07: [-58, -12*sign, 4*sign],
                                    hit: strike_pose, recover: [-46, 8*sign, 6*sign], length: [0, 0, 0]},
                      "position": {0.0: [0, 0, 0], 0.07: [0.4*sign, 0.6, -0.7],
                                   hit: [0.8*sign, 1.5 if kind == "knee" else 0.8,
                                         -2.6 if kind == "knee" else -3.6],
                                   recover: [0.3*sign, 0.4, -1.1], length: [0, 0, 0]}},
                support: {"rotation": {0.0: [0, 0, 0], 0.07: [9, -6*sign, 6*sign],
                                        hit: [-15, 24*sign, 9*sign], length: [0, 0, 0]}},
                arm: {"rotation": {0.0: guard, 0.07: [-38, -16*sign, 50*sign],
                                    hit: [12, -22*sign, 32*sign], length: guard}},
                other: {"rotation": {0.0: other_guard, 0.07: [-92, 12*sign, -42*sign],
                                      hit: [-80, 20*sign, -48*sign], length: other_guard}},
                "head": {"rotation": {0.0: [0, 0, 0], hit: [-8, -10*sign, 3*sign], length: [0, 0, 0]}},
            })
    animations["combat.xeno_high_roundhouse_v4"] = clip(0.40, {
        "root": {"rotation": {0.0: [0, 0, 0], 0.10: [8, 0, -12], 0.18: [22, 0, -18], 0.40: [0, 0, 0]}},
        "waist": {"rotation": {0.0: [0, 0, 0], 0.10: [-10, -28, 5], 0.18: [-18, 38, 10], 0.40: [0, 0, 0]}},
        "right_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [-68, -38, 22], 0.18: [-108, 36, 48], 0.28: [-62, 28, 24], 0.40: [0, 0, 0]},
                      "position": {0.0: [0, 0, 0], 0.10: [1.2, 0.8, -0.8],
                                   0.18: [-1.8, 1.8, -3.4], 0.28: [-0.6, 0.6, -1.2],
                                   0.40: [0, 0, 0]}},
        "left_leg": {"rotation": {0.0: [0, 0, 0], 0.10: [12, -10, 10], 0.18: [-22, 32, 16], 0.40: [0, 0, 0]}},
        "right_arm": {"rotation": {0.0: guard_r, 0.10: [-28, -26, 62], 0.18: [18, -32, 48], 0.40: guard_r}},
        "left_arm": {"rotation": {0.0: guard_l, 0.10: [-92, 16, -46], 0.18: [-70, 28, -64], 0.40: guard_l}},
        "head": {"rotation": {0.0: [0, 0, 0], 0.18: [-12, -16, 6], 0.40: [0, 0, 0]}},
    })
    punch_hold = {"root": [6, 0, -5], "waist": [8, -28, -6], "head": [-3, 12, 0],
                  "right_arm": [-32, -42, 52], "left_arm": [-88, 12, -44],
                  "right_leg": [22, -12, -4], "left_leg": [-15, 8, 4]}
    kick_hold = {"root": [12, 0, -8], "waist": [-8, -24, 8], "head": [-6, 10, 2],
                 "right_arm": [-40, -18, 50], "left_arm": [-90, 14, -48],
                 "right_leg": [-68, -16, 8], "left_leg": [-12, 12, 8]}
    rest = {"root": [0, 0, 0], "waist": [0, 0, 0], "head": [0, 0, 0],
            "right_arm": guard_r, "left_arm": guard_l, "right_leg": [0, 0, 0], "left_leg": [0, 0, 0]}
    punch_hit = {"root": [-12, 0, 8], "waist": [-16, 38, 10], "head": [5, -14, -3],
                 "right_arm": [-98, 4, 8], "left_arm": [-70, 24, -48],
                 "right_leg": [-24, 28, 4], "left_leg": [16, 18, -6]}
    kick_hit = {"root": [20, 0, -12], "waist": [-18, 36, 12], "head": [-12, -14, 5],
                "right_arm": [18, -26, 42], "left_arm": [-76, 24, -54],
                "right_leg": [-102, 22, 12], "left_leg": [-24, 32, 12]}
    for kind, hold, impact in (("punch", punch_hold, punch_hit), ("kick", kick_hold, kick_hit)):
        animations["combat.xeno_charge_%s_hold" % kind] = clip(0.40, {
            bone: {"rotation": {0.0: rest[bone], 0.18: hold[bone], 0.40: hold[bone]}}
            for bone in rest
        })
        animations["combat.xeno_charge_%s_fire" % kind] = clip(0.60, {
            bone: {"rotation": {0.0: hold[bone], 0.12: hold[bone], 0.25: impact[bone],
                                 0.34: impact[bone], 0.46: hold[bone], 0.60: rest[bone]}}
            for bone in rest
        })
        striking_bone = "right_arm" if kind == "punch" else "right_leg"
        chamber = [0.8, 0.5, -0.5] if kind == "punch" else [0.6, 1.0, -0.8]
        contact = [-0.5, 0.9, -4.2] if kind == "punch" else [1.0, 1.6, -4.8]
        # Model units: the limb briefly reaches beyond its bind position, then returns.
        animations["combat.xeno_charge_%s_hold" % kind]["bones"][striking_bone]["position"] = {
            str(t): {"vector": list(v)} for t, v in
            ((0.0, [0, 0, 0]), (0.18, chamber), (0.40, chamber))
        }
        animations["combat.xeno_charge_%s_fire" % kind]["bones"][striking_bone]["position"] = {
            str(t): {"vector": list(v)} for t, v in
            ((0.0, chamber), (0.12, chamber), (0.25, contact), (0.34, contact),
             (0.46, chamber), (0.60, [0, 0, 0]))
        }
    return animations
