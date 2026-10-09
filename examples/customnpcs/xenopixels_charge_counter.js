/**
 * Counter charged punch / kick. Paste into Scripts, Enabled = Yes, close to save.
 *
 * Needs XenoPixels API v23 and a combat profile.
 * When this NPC is hit, it winds a charge that auto-releases. Odd hits are
 * punches; even hits are kicks (neutral bias). A new start replaces the old
 * hold. Combat Brain stays off so CustomNPCs still owns walking.
 *
 * Set the NPC Aggressive / Retaliate so it has someone to hit when the cone
 * fires. Charge hits the locked or attack target if present, else the cone
 * ahead.
 */
"use strict";

var HITS_KEY = "xeno_charge_hits";

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "human", 40, 24, 28, 32, 12, 30);
    XenoPixels.setPowerRelease(n, 100);
    XenoPixels.setAuthoritative(n, true);
    XenoPixels.setCombatBrain(n, false);
    XenoPixels.setAppearanceMode(n, "FULL");
    n.getStoreddata().put(HITS_KEY, 0);
}

function damaged(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (XenoPixels.isCharging(n)) {
        return;
    }
    var data = n.getStoreddata();
    var hits = Number(data.get(HITS_KEY)) + 1;
    data.put(HITS_KEY, hits);
    var started = (hits % 2 === 0)
        ? XenoPixels.startChargeKick(n, 20, 0)
        : XenoPixels.startChargePunch(n, 20);
    if (started) {
        n.say("Charging " + XenoPixels.getChargeStyle(n));
    }
}

function died(event) {
    if (typeof XenoPixels !== "undefined") {
        XenoPixels.cancelCharge(event.npc);
    }
}
