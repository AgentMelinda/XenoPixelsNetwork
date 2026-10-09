/**
 * XenoPixels + CustomNPCs — authored rush combo.
 *
 * Paste this entire file into the NPC Scripts tab.
 * Enabled = Yes. Tick Tick (or Update / Timer). Close the GUI so it saves.
 * AI tab: Aggressive / Retaliate so it picks a target. Set Melee Strength
 * to 0 so CustomNPCs does not add a second hit on top of this string.
 * No Dialog / Role — those eat the right-click demo.
 *
 * Combat Brain stays OFF. This script owns the string. Each melee beat
 * plays a clip and deals a real profile-scaled hit. No charged punch
 * or kick. Finishers are vanish, flying kick, kiblast, and kamehameha.
 *
 * Full DragonMineZ appearance uses the player rig. A native Xeno NPC with a selected
 * GeckoLib model keeps that model and searches XenoPixels combat clips as fallbacks;
 * the model must have matching arm/leg bones. DragonMineZ Master Gohan does.
 * meleeHit needs XenoPixels script API v27 — restart the client after rebuild.
 */
"use strict";

var T_STEP = 9501;
var INDEX_KEY = "xeno_combo_i";
var DUE_KEY = "xeno_combo_due";
var MELEE_RANGE = 4.0;
var CHASE_WAIT = 8;

// Edit this array to change the string. kind "hit" = clip + real damage.
var COMBO = [
    { kind: "gap" },
    { kind: "hit", name: "combat.xeno_jab_left_v3", wait: 6 },
    { kind: "hit", name: "combat.xeno_jab_right_v3", wait: 6 },
    { kind: "hit", name: "combat.xeno_body_punch_left_v3", wait: 7, scale: 1.1 },
    { kind: "hit", name: "combat.xeno_cross_right_v3", wait: 8, scale: 1.15 },
    { kind: "hit", name: "combat.xeno_hook_left_v3", wait: 8, scale: 1.15 },
    { kind: "vanish", side: 0, wait: 8 },
    { kind: "hit", name: "combat.xeno_knee_right_v3", wait: 7, scale: 1.2 },
    { kind: "hit", name: "combat.xeno_uppercut_right_v3", wait: 8, scale: 1.25 },
    { kind: "hit", name: "combat.xeno_spin_kick_right_v3", wait: 10, scale: 1.3 },
    { kind: "hit", name: "combat.xeno_flying_kick_v4", wait: 12, scale: 1.45 },
    { kind: "ki", id: "kiblast", duration: 20, wait: 16 },
    { kind: "hit", name: "combat.xeno_heavy_finish_v3", wait: 12, scale: 1.6 },
    { kind: "ki", id: "kamehameha", duration: 50, wait: 70 },
    { kind: "rest", wait: 24 }
];

function init(event) {
    dress(event.npc);
    resetCombo(event.npc);
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    if (typeof XenoPixels.lockOn === "function") {
        XenoPixels.lockOn(n, event.player);
    }
    try {
        n.setAttackTarget(event.player);
    } catch (e) {}
    refill(n);
    n.getStoreddata().put(INDEX_KEY, "0");
    n.getStoreddata().put(DUE_KEY, "0");
    n.say("Combo rush.");
    step(n, true);
}

function meleeAttack(event) {
    if (typeof XenoPixels === "undefined") {
        return;
    }
    var beat = currentBeat(event.npc);
    if (beat && beat.name) {
        XenoPixels.setMeleeAnimation(event.npc, beat.name);
    }
}

function timer(event) {
    if (event.id != T_STEP) {
        return;
    }
    step(event.npc, true);
}

function tick(event) {
    step(event.npc, false);
}

function update(event) {
    step(event.npc, false);
}

function died(event) {
    resetCombo(event.npc);
    if (typeof XenoPixels !== "undefined") {
        XenoPixels.cancelCharge(event.npc);
        XenoPixels.clearMeleeAnimation(event.npc);
        if (typeof XenoPixels.clearLock === "function") {
            XenoPixels.clearLock(event.npc);
        }
    }
}

function dress(n) {
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels API is not loaded on this server.");
        return;
    }
    XenoPixels.setProfile(n, "saiyan", 45, 28, 30, 35, 40, 80);
    XenoPixels.setPowerRelease(n, 100);
    XenoPixels.setAuthoritative(n, true);
    XenoPixels.setCombatBrain(n, false);
    if (typeof XenoPixels.setBrainFlag === "function") {
        XenoPixels.setBrainFlag(n, "charge", false);
        XenoPixels.setBrainFlag(n, "flyingFist", false);
    }
    XenoPixels.setAppearanceMode(n, "FULL");
    if (typeof XenoPixels.setPlayerModel === "function") {
        XenoPixels.setPlayerModel(n, true);
    }
    if (typeof XenoPixels.setAura === "function") {
        XenoPixels.setAura(n, true);
    }
    if (typeof XenoPixels.setKiColor === "function") {
        XenoPixels.setKiColor(n, "#66CCFF");
    }
    if (typeof XenoPixels.setAuraColor === "function") {
        XenoPixels.setAuraColor(n, "#FFCC44");
    }
    XenoPixels.addTechnique(n, "kiblast");
    XenoPixels.addTechnique(n, "kamehameha");
    refill(n);
}

function refill(n) {
    if (typeof XenoPixels.getMaxEnergy !== "function") {
        return;
    }
    var max = XenoPixels.getMaxEnergy(n);
    if (max > 0) {
        XenoPixels.setCurrentEnergy(n, max);
        XenoPixels.setCurrentStamina(n, XenoPixels.getMaxStamina(n));
    }
    XenoPixels.clearTechniqueCooldown(n, "kiblast");
    XenoPixels.clearTechniqueCooldown(n, "kamehameha");
}

function resetCombo(n) {
    n.getStoreddata().put(INDEX_KEY, "-1");
    n.getStoreddata().put(DUE_KEY, "0");
    try {
        n.getTimers().stop(T_STEP);
    } catch (e) {}
}

function step(n, force) {
    if (typeof XenoPixels === "undefined") {
        return;
    }
    var target = n.getAttackTarget();
    if (target == null) {
        if (comboIndex(n.getStoreddata()) >= 0) {
            resetCombo(n);
        }
        return;
    }

    var data = n.getStoreddata();
    var i = comboIndex(data);
    if (i < 0) {
        refill(n);
        i = 0;
        data.put(INDEX_KEY, "0");
        data.put(DUE_KEY, "0");
        force = true;
    }

    var now = worldTime(n);
    if (!force && now < dueAt(data)) {
        return;
    }
    if (i >= COMBO.length) {
        refill(n);
        i = 0;
    }

    var beat = COMBO[i];
    if (needsRange(beat) && tooFar(n, target)) {
        XenoPixels.chase(n, target);
        schedule(n, data, i, now, CHASE_WAIT);
        return;
    }

    var wait = playBeat(n, target, beat);
    schedule(n, data, i + 1, now, wait);
}

function schedule(n, data, next, now, wait) {
    data.put(INDEX_KEY, String(next));
    data.put(DUE_KEY, String(now + wait));
    try {
        n.getTimers().forceStart(T_STEP, wait, false);
    } catch (e) {}
}

function playBeat(n, target, beat) {
    var wait = beat.wait || 8;
    var kind = beat.kind;
    if (kind === "gap" || kind === "chase") {
        return 2;
    }
    if (kind === "hit" || kind === "anim" || kind === "kick" || kind === "punch") {
        return strike(n, target, beat.name, beat.scale, wait);
    }
    if (kind === "vanish") {
        var side = beat.side || 0;
        if (side < 0) {
            XenoPixels.vanishLeft(n, target);
        } else if (side > 0) {
            XenoPixels.vanishRight(n, target);
        } else {
            XenoPixels.vanishBehind(n, target);
        }
        return wait;
    }
    if (kind === "ki") {
        XenoPixels.clearTechniqueCooldown(n, beat.id);
        XenoPixels.fireTechnique(n, beat.id, target, beat.duration || 40);
        return wait;
    }
    if (kind === "burst") {
        XenoPixels.zBurst(n, target);
        return wait;
    }
    return wait;
}

function strike(n, target, name, scale, wait) {
    if (name) {
        XenoPixels.setMeleeAnimation(n, name);
        XenoPixels.playAnimation(n, name);
    }
    if (typeof XenoPixels.meleeHit === "function") {
        XenoPixels.meleeHit(n, target, scale == null ? 1.0 : scale);
    } else if (typeof XenoPixels.playMelee === "function") {
        XenoPixels.playMelee(n);
    }
    return wait;
}

function needsRange(beat) {
    if (!beat) {
        return false;
    }
    var kind = beat.kind;
    return kind === "gap" || kind === "chase" || kind === "hit" || kind === "anim"
        || kind === "kick" || kind === "punch";
}

function tooFar(a, b) {
    var dx = a.getX() - b.getX();
    var dy = a.getY() - b.getY();
    var dz = a.getZ() - b.getZ();
    return (dx * dx + dy * dy + dz * dz) > (MELEE_RANGE * MELEE_RANGE);
}

function currentBeat(n) {
    var i = comboIndex(n.getStoreddata());
    if (i < 0 || i >= COMBO.length) {
        return null;
    }
    return COMBO[i];
}

function worldTime(n) {
    try {
        return n.getWorld().getTotalTime();
    } catch (e) {
        return 0;
    }
}

function dueAt(data) {
    var raw = data.get(DUE_KEY);
    if (raw === null || raw === undefined || raw === "") {
        return 0;
    }
    var value = Number(raw);
    return isNaN(value) ? 0 : value;
}

function comboIndex(data) {
    var raw = data.get(INDEX_KEY);
    if (raw === null || raw === undefined || raw === "") {
        return -1;
    }
    var value = Number(raw);
    return isNaN(value) ? -1 : value;
}
