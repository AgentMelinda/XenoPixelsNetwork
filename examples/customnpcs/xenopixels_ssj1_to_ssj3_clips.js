/**
 * SSJ1 -> SSJ2 -> SSJ3. Paste into Scripts, Enabled = Yes, close to save.
 * Right-click starts. Right-click again cancels and drops to base.
 *
 * Each step binds a studio clip to TRANSFORM, then calls ascend. Do not
 * playClip-hold at the same time — that blocked the transform fire.
 *
 * Clips (publish first, or the form still changes without a custom pose):
 *   /xenoanim global push ssj1_transform
 *   /xenoanim global push ssj2_transform
 *   /xenoanim global push ssj3_transform
 *
 * Hair once as OP:  /xenopixels genhaircode black apply
 */
"use strict";

var GROUP = "supersaiyan";
var SSJ1 = "supersaiyanmastered";
var SSJ2 = "supersaiyan2";
var SSJ3 = "supersaiyan3";

var CLIP_SSJ1 = "ssj1_transform";
var CLIP_SSJ2 = "ssj2_transform";
var CLIP_SSJ3 = "ssj3_transform";

var HOLD_TICKS = 40;
var GAP_TICKS = 150;
var T_SSJ1 = 9301;
var T_SSJ2 = 9302;
var T_SSJ3 = 9303;
var T_DONE = 9304;
var CLIMB = "xeno_ssj_climb";

function init(event) {
    dress(event.npc);
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (Number(n.getStoreddata().get(CLIMB)) === 1) {
        stopClimb(n);
        n.say("Dropped to base.");
        return;
    }
    startClimb(n);
}

function startClimb(n) {
    dress(n);
    if (XenoPixels.isTransforming(n)) {
        return;
    }
    n.getStoreddata().put(CLIMB, 1);
    n.say("This power... Super Saiyan!");
    n.getTimers().forceStart(T_SSJ1, 8, false);
}

function timer(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (event.id == T_SSJ1) {
        stage(n, CLIP_SSJ1, SSJ1, "#FFD84A", false);
        n.getTimers().forceStart(T_SSJ2, GAP_TICKS, false);
    } else if (event.id == T_SSJ2) {
        if (XenoPixels.isTransforming(n)) {
            n.getTimers().forceStart(T_SSJ2, 8, false);
            return;
        }
        n.say("Not enough... Super Saiyan 2!");
        stage(n, CLIP_SSJ2, SSJ2, "#FFE566", true);
        n.getTimers().forceStart(T_SSJ3, GAP_TICKS, false);
    } else if (event.id == T_SSJ3) {
        if (XenoPixels.isTransforming(n)) {
            n.getTimers().forceStart(T_SSJ3, 8, false);
            return;
        }
        n.say("This is it... Super Saiyan 3!!");
        stage(n, CLIP_SSJ3, SSJ3, "#FFF3A0", true);
        n.getTimers().forceStart(T_DONE, HOLD_TICKS + 20, false);
    } else if (event.id == T_DONE) {
        n.getStoreddata().put(CLIMB, 0);
    }
}

function stage(n, clip, form, auraHex, lightning) {
    XenoPixels.setAura(n, true);
    XenoPixels.setAuraColor(n, auraHex);
    XenoPixels.setAuraLightning(n, lightning);
    var ticks = bindTransform(n, clip);
    if (!XenoPixels.ascend(n, GROUP, form, ticks)) {
        n.say("ascend failed: " + GROUP + "/" + form);
    }
}

function bindTransform(n, clip) {
    var ticks = HOLD_TICKS;
    if (typeof XenoPixels.clearStateClip === "function") {
        XenoPixels.clearStateClip(n, "TRANSFORM");
    }
    if (!clip) {
        return ticks;
    }
    var ready = typeof XenoPixels.isClipAvailable !== "function" || XenoPixels.isClipAvailable(clip);
    if (ready && typeof XenoPixels.setStateClip === "function") {
        XenoPixels.setStateClip(n, "TRANSFORM", clip);
        if (typeof XenoPixels.clipDuration === "function") {
            var d = XenoPixels.clipDuration(clip);
            if (d > 0) {
                ticks = d;
            }
        }
        return ticks;
    }
    n.say(clip + " is not published. Form still changes. /xenoanim global push " + clip);
    return ticks;
}

function dress(n) {
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "saiyan", 40, 40, 30, 40, 50, 40);
    if (typeof XenoPixels.setPowerRelease === "function") {
        XenoPixels.setPowerRelease(n, 100);
    }
    if (typeof XenoPixels.setAuthoritative === "function") {
        XenoPixels.setAuthoritative(n, true);
    }
    if (typeof XenoPixels.setCombatBrain === "function") {
        XenoPixels.setCombatBrain(n, false);
    }
    XenoPixels.setAppearanceMode(n, "FULL");
    if (typeof XenoPixels.setPlayerModel === "function") {
        XenoPixels.setPlayerModel(n, true);
    }
    XenoPixels.setHairEnabled(n, true);
    XenoPixels.setHairColor(n, "black");
    XenoPixels.setAuraColor(n, "#FFD84A");
    XenoPixels.setAura(n, true);
    XenoPixels.setAuraLightning(n, false);
    XenoPixels.setMastery(n, GROUP, SSJ1, 100);
    XenoPixels.setMastery(n, GROUP, SSJ2, 100);
    XenoPixels.setMastery(n, GROUP, SSJ3, 100);
}

function stopClimb(n) {
    n.getTimers().stop(T_SSJ1);
    n.getTimers().stop(T_SSJ2);
    n.getTimers().stop(T_SSJ3);
    n.getTimers().stop(T_DONE);
    n.getStoreddata().put(CLIMB, 0);
    if (typeof XenoPixels.stopClip === "function") {
        XenoPixels.stopClip(n);
    }
    XenoPixels.descend(n, 20);
}

function died(event) {
    if (typeof XenoPixels === "undefined") {
        return;
    }
    stopClimb(event.npc);
}
