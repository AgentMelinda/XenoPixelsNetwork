/**
 * NPC Hakai. Paste into Scripts, Enabled = Yes, close to save.
 * Tick Interact (and Damaged if you want a counter). Tick Tick/Update
 * so the channel can finish without the Timer box.
 *
 * Right-click starts Hakai on the attack target, else the clicking player.
 * Right-click again cancels. Being hit also aims the attacker.
 *
 * Needs XenoPixels API v25, a combat profile, Full appearance, and LOS.
 * Optional pose: /xenoanim global push newhakaipose
 *
 * Combat Brain does not use Hakai, Sparking, or 225% release. This script
 * sets release to 225 and sparking aura (visual only).
 */
"use strict";

function init(event) {
    dress(event.npc);
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    if (typeof XenoPixels.startHakai !== "function") {
        n.say("Need XenoPixels v25+ for NPC Hakai. This server is v"
            + XenoPixels.getVersion());
        return;
    }
    if (XenoPixels.isHakai(n)) {
        XenoPixels.cancelHakai(n);
        n.say("Hakai cancelled.");
        return;
    }
    var target = n.getAttackTarget();
    if (target == null && event.player) {
        target = event.player;
    }
    if (target == null) {
        n.say("No target. Aggro someone, or right-click me.");
        return;
    }
    startHakai(n, target);
}

function damaged(event) {
    if (typeof XenoPixels === "undefined" || typeof XenoPixels.startHakai !== "function") {
        return;
    }
    var n = event.npc;
    if (XenoPixels.isHakai(n)) {
        return;
    }
    var attacker = event.source;
    if (attacker != null) {
        startHakai(n, attacker);
    }
}

function startHakai(n, target) {
    dress(n);
    if (typeof XenoPixels.setStateClip === "function"
            && XenoPixels.isClipAvailable("newhakaipose")) {
        XenoPixels.setStateClip(n, "HAKAI_HOLD", "newhakaipose");
    }
    if (XenoPixels.startHakai(n, target)) {
        n.say("Hakai.");
    } else {
        n.say("Hakai refused (range, line of sight, or no profile).");
    }
}

function died(event) {
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.cancelHakai === "function") {
        XenoPixels.cancelHakai(event.npc);
    }
}

function dress(n) {
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (typeof XenoPixels.setProfile === "function") {
        XenoPixels.setProfile(n, "saiyan", 50, 40, 35, 40, 40, 60);
    }
    if (typeof XenoPixels.setPowerRelease === "function") {
        XenoPixels.setPowerRelease(n, 225);
    }
    if (typeof XenoPixels.setAura === "function") XenoPixels.setAura(n, true);
    if (typeof XenoPixels.setAuraSparking === "function") XenoPixels.setAuraSparking(n, true);
    if (typeof XenoPixels.setAuraLightning === "function") XenoPixels.setAuraLightning(n, true);
    if (typeof XenoPixels.setAuthoritative === "function") XenoPixels.setAuthoritative(n, true);
    if (typeof XenoPixels.setCombatBrain === "function") XenoPixels.setCombatBrain(n, false);
    XenoPixels.setAppearanceMode(n, "FULL");
    if (typeof XenoPixels.setPlayerModel === "function") {
        XenoPixels.setPlayerModel(n, true);
    }
}
