/**
 * Charged punch on an NPC. Paste into Scripts, Enabled = Yes, close to save.
 *
 * Needs XenoPixels API v23 and a combat profile (startCharge* returns false
 * without one). Right-click starts a punch that auto-releases at 24 ticks.
 * Right-click again while holding to release early.
 *
 * Optional studio poses (publish first):
 *   /xenoanim global push my_charge
 *   XenoPixels.setStateClip(npc, "CHARGE_PUNCH", "my_charge");
 *   XenoPixels.setStateClip(npc, "CHARGE_PUNCH_FIRE", "my_release");
 */
"use strict";

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "human", 35, 20, 25, 30, 10, 30);
    XenoPixels.setPowerRelease(n, 100);
    XenoPixels.setAuthoritative(n, true);
    XenoPixels.setCombatBrain(n, false);
    XenoPixels.setAppearanceMode(n, "FULL");
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (XenoPixels.isCharging(n)) {
        var pct = XenoPixels.getChargePercent(n);
        XenoPixels.releaseCharge(n);
        n.say("Released punch at " + pct + "%");
        return;
    }
    if (XenoPixels.startChargePunch(n, 24)) {
        n.say("Charging punch");
    } else {
        n.say("Could not start charge (need a live profile, not transforming)");
    }
}
