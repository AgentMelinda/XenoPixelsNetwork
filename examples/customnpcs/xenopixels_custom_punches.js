/**
 * XenoPixels + CustomNPCs — specific animations on real melee punches.
 *
 * CustomNPCs still owns targeting, range, attack delay, and the actual hit. Its meleeAttack
 * script event runs before CustomNPCs commits the attack, so this script selects the animation
 * XenoPixels plays for that native attack attempt.
 *
 * Full DragonMineZ appearance:
 *   Use names returned by XenoPixels.listAnimations(). The names below ship with XenoPixels.
 *
 * CNPC Gecko custom model:
 *   Replace PUNCH_ANIMATIONS with the exact animation names authored in your Gecko model.
 *   XenoPixels cannot validate names inside a server owner's custom Gecko animation file.
 */
"use strict";

var PUNCH_GENERATION = 4;

// All authored punch clips remain available. Cross V1/V2 and generation 4 use Xeno-owned aliases
// of DMZ's real left/right one-handed punch movement.
var PUNCHES_BY_GENERATION = {
    "1": [
        "combat.xeno_jab_left",
        "combat.xeno_jab_right",
        "combat.xeno_cross_left",
        "combat.xeno_cross_right",
        "combat.xeno_body_punch_left",
        "combat.xeno_body_punch_right",
        "combat.xeno_hook_left",
        "combat.xeno_hook_right",
        "combat.xeno_spin_hook_left",
        "combat.xeno_spin_hook_right",
        "combat.xeno_spin_uppercut_left"
    ],
    "2": [
        "combat.xeno_jab_left_v2",
        "combat.xeno_jab_right_v2",
        "combat.xeno_cross_left_v2",
        "combat.xeno_cross_right_v2",
        "combat.xeno_body_punch_left_v2",
        "combat.xeno_body_punch_right_v2",
        "combat.xeno_hook_left_v2",
        "combat.xeno_hook_right_v2",
        "combat.xeno_spin_hook_left_v2",
        "combat.xeno_spin_hook_right_v2",
        "combat.xeno_spin_uppercut_left_v2"
    ],
    "3": [
        "combat.xeno_jab_left_v3",
        "combat.xeno_jab_right_v3",
        "combat.xeno_cross_left_v3",
        "combat.xeno_cross_right_v3",
        "combat.xeno_body_punch_left_v3",
        "combat.xeno_body_punch_right_v3",
        "combat.xeno_hook_left_v3",
        "combat.xeno_hook_right_v3",
        "combat.xeno_uppercut_left_v3",
        "combat.xeno_uppercut_right_v3",
        "combat.xeno_spin_hook_left_v3",
        "combat.xeno_spin_hook_right_v3",
        "combat.xeno_spin_uppercut_left_v3"
    ],
    "4": [
        "combat.xeno_dmz_punch_left_v4",
        "combat.xeno_dmz_punch_right_v4"
    ]
};

var PUNCH_INDEX_KEY = "xenopixels_custom_punch_index";

function init(event) {
    var npc = event.npc;

    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels API is not loaded on this server.");
        return;
    }

    // race, strength, strikePower, resistance, vitality, kiPower, energy
    XenoPixels.setProfile(npc, "human", 35, 20, 25, 30, 10, 30);
    XenoPixels.setPowerRelease(npc, 100);
    XenoPixels.setAuthoritative(npc, true);
    XenoPixels.setCombatBrain(npc, false);
    npc.getStoreddata().put(PUNCH_INDEX_KEY, 0);
}

function meleeAttack(event) {
    if (typeof XenoPixels === "undefined") {
        return;
    }

    var animations = PUNCHES_BY_GENERATION[String(PUNCH_GENERATION)];
    if (!animations || animations.length === 0) {
        animations = PUNCHES_BY_GENERATION["4"];
    }

    var npc = event.npc;
    var data = npc.getStoreddata();
    var index = Number(data.get(PUNCH_INDEX_KEY));
    if (!isFinite(index) || index < 0) {
        index = 0;
    }

    var animation = animations[index % animations.length];
    if (XenoPixels.setMeleeAnimation(npc, animation)) {
        data.put(PUNCH_INDEX_KEY, (index + 1) % animations.length);
    }
}

function died(event) {
    if (typeof XenoPixels !== "undefined") {
        XenoPixels.clearMeleeAnimation(event.npc);
    }
}
